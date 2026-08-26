package com.lensora.lensorastudio.core.watch;

import com.lensora.lensorastudio.core.io.FileChangeCoordinator;
import com.lensora.lensorastudio.core.threading.BackgroundExecutor;

import javafx.application.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Watches a project's folder tree for changes made OUTSIDE the app (via OS
 * file explorer, sync tools, other programs) and notifies a callback so the
 * UI can refresh. This is a SUPPLEMENT to the app's own explicit
 * refresh-after-operation calls, not a replacement:
 *  - the app's own operations already know precisely what changed and
 *    refresh immediately, with no dependency on OS event delivery
 *  - WatchService reliability on network/NAS drives is inconsistent, so
 *    it must never be the ONLY way changes get picked up
 *
 * Recursively registers every subdirectory, dynamically adds newly created
 * ones, and debounces bursts of events per-folder before notifying.
 */
public class FolderWatchService
{
    private static final Logger logger = LoggerFactory.getLogger(FolderWatchService.class);
    private static final long DEBOUNCE_MS = 500;

    private WatchService watchService;
    private final Map<WatchKey, Path> keyToPath = new ConcurrentHashMap<>();
    private final Map<Path, ScheduledFuture<?>> pendingDebounce = new ConcurrentHashMap<>();
    private Thread watchThread;
    private volatile boolean running = false;

    private Consumer<File> onExternalChange;

    public void setOnExternalChange(Consumer<File> callback)
    {
        this.onExternalChange = callback;
    }

    /** Starts watching the given project root recursively. Stops any previous watch first. */
    public void watch(File projectRoot)
    {
        stop();
        if (projectRoot == null || !projectRoot.isDirectory()) return;

        try
        {
            watchService = FileSystems.getDefault().newWatchService();
            registerRecursively(projectRoot.toPath());
            running = true;

            // Dedicated daemon thread: pollLoop() blocks indefinitely on watchService.take().
            // Submitting an infinite blocking loop to BackgroundExecutor would tie up an I/O 
            // pool thread permanently (thread hijacking), reducing capacity for user tasks.
            watchThread = new Thread(this::pollLoop, "Lensora-folder-watch-service");
            watchThread.setDaemon(true);
            watchThread.start();
        }
        catch (IOException e)
        {
            logger.warn("[FolderWatchService] Could not start watching {} - external-change auto-refresh disabled for this session.",
                    projectRoot, e);
        }
    }

    public void stop()
    {
        running = false;
        keyToPath.clear();
        pendingDebounce.values().forEach(f -> f.cancel(false));
        pendingDebounce.clear();

        if (watchService != null)
        {
            try { watchService.close(); } catch (IOException ignored) {}
            watchService = null;
        }
        if (watchThread != null)
        {
            watchThread.interrupt();
            watchThread = null;
        }
    }

    private void registerRecursively(Path root) throws IOException
    {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                registerSingle(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerSingle(Path dir) throws IOException
    {
        WatchKey key = dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);
        keyToPath.put(key, dir);
    }

    private void pollLoop()
    {
        while (running && !Thread.currentThread().isInterrupted())
        {
            WatchKey key;
            try
            {
                key = watchService.take(); // blocks until an event or close()
            }
            catch (InterruptedException | ClosedWatchServiceException e)
            {
                Thread.currentThread().interrupt();
                break;
            }

            Path dir = keyToPath.get(key);
            if (dir == null) { key.reset(); continue; }

            for (WatchEvent<?> event : key.pollEvents())
            {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                @SuppressWarnings("unchecked")
                Path changedName = ((WatchEvent<Path>) event).context();
                Path changedPath = dir.resolve(changedName);

                if (kind == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changedPath))
                {
                    // Offload subfolder traversal to IO pool to prevent blocking pollLoop
                    BackgroundExecutor.getInstance().executeIO(() -> {
                        try 
                        {
                            registerRecursively(changedPath);
                        } 
                        catch (IOException ignored) {}
                    });
                }
            }

            scheduleDebouncedNotify(dir.toFile());

            boolean valid = key.reset();
            if (!valid)
            {
                keyToPath.remove(key);
            }
        }
    }

    /**
     * Coalesces bursts of events per-folder into one atomic UI refresh call.
     * Uses Map.compute and atomic removal to avoid race conditions.
     */
    private void scheduleDebouncedNotify(File folder)
    {
        Path path = folder.toPath();

        pendingDebounce.compute(path, (p, existing) -> {
            if (existing != null)
            {
                existing.cancel(false);
            }

            ScheduledFuture<?>[] futureHolder = new ScheduledFuture<?>[1];
            futureHolder[0] = BackgroundExecutor.getInstance().scheduleOnce(() -> {
                // Remove only if this exact task is still the active one
                pendingDebounce.remove(p, futureHolder[0]);

                // Ask the coordinator FIRST - drop if self-initiated
                if (FileChangeCoordinator.getInstance().consumeIfExpected(p)) 
                {
                    return;
                }
                
                if (onExternalChange != null) 
                {
                    Platform.runLater(() -> onExternalChange.accept(folder));
                }
            }, DEBOUNCE_MS, TimeUnit.MILLISECONDS);

            return futureHolder[0];
        });
    }
}