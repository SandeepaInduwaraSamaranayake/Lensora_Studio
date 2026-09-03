package com.lensora.lensorastudio.core.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.lensora.lensorastudio.core.watch.FolderWatchService;

import javafx.application.Platform;

/**
 * <p>High-level filesystem operation wrapper that adds <b>expectation tracking</b>
 * and <b>UI refresh notifications</b> around raw I/O execution.</p>
 *
 * <p>This is the <b>primary API</b> that should be used by all UI and business
 * logic for file/folder mutations. It delegates the actual disk operations to
 * a pure {@link FileIO} instance, but before each operation it registers the
 * expected change with {@link FileChangeCoordinator} so that the watch service
 * does not double‑fire redundant refresh events. After a successful operation,
 * it triggers a single unified refresh callback to update the UI tree and
 * file listings.</p>
 *
 * <p>If an operation fails, the expectations are automatically cleared so that
 * a later, genuinely external change to the same path is not incorrectly
 * suppressed.</p>
 *
 * <p>All methods that perform I/O are <b>synchronous</b> and will block the
 * calling thread. Callers should offload them to background threads (e.g.,
 * using {@code CompletableFuture}) to avoid freezing the UI.</p>
 *
 * @see FileIO
 * @see FileChangeCoordinator
 */
public class InstrumentedFileIO
{
    /**
     * Result of a batch move operation.
     *
     * @param succeeded   number of files successfully moved
     * @param failedFiles list of files that could not be moved (with exceptions
     *                    already logged/handled)
     */
    public record BatchResult(int succeeded, List<File> failedFiles) {}

    private final Consumer<File> refreshCallback;
    private final Supplier<File> watchRootSupplier;
    private FolderWatchService folderWatchService; // injected via setter
    private final FileIO fileIO;

    public void setFolderWatchService(FolderWatchService service) { this.folderWatchService = service; }

    /**
     * Creates a new instance using a default {@link FileIO} backend.
     *
     * @param refreshCallback    callback to notify the UI that a folder's
     *                           content has changed; receives the affected
     *                           folder as argument
     * @param watchRootSupplier  supplies the root of the currently watched
     *                           project; used to scope expectation marking
     *                           to the project tree (may be {@code null})
     */
    public InstrumentedFileIO (Consumer<File> refreshCallback, Supplier<File> watchRootSupplier)
    {
        this(refreshCallback, watchRootSupplier, new FileIO());
    }

    /**
     * Creates a new instance with a custom {@link FileIO} backend.
     *
     * @param refreshCallback    callback to notify the UI that a folder's
     *                           content has changed
     * @param watchRootSupplier  supplies the root of the currently watched
     *                           project (may be {@code null})
     * @param fileIO             the raw I/O implementation to delegate to
     */
    public InstrumentedFileIO (Consumer<File> refreshCallback, Supplier<File> watchRootSupplier, FileIO fileIO)
    {
        this.refreshCallback = refreshCallback;
        this.watchRootSupplier = watchRootSupplier;
        this.fileIO = fileIO;
    }

    /**
     * Registers an expectation that the given file will be changed by the app.
     * The expectation is marked for the file itself and all its ancestors up
     * to the project root (if known).
     *
     * <p>This is called <b>before</b> every mutation. The expectation will be
     * consumed by the {@code FolderWatchService} when the corresponding OS
     * event arrives, preventing a double refresh.</p>
     *
     * @param file the file that is about to be changed
     */
    public void expectChange(File file)
    {
        File root = watchRootSupplier != null ? watchRootSupplier.get() : null;
        FileChangeCoordinator.getInstance().expect(
                file.toPath(),
                root != null ? root.toPath() : null);
    }

    /**
     * Manually removes an expectation for the given file.
     * Used primarily when an operation fails after registering an expectation,
     * to ensure that a later external change is not suppressed.
     *
     * @param file the file whose expectation should be removed
     */
    public void clearExpectation(File file)
    {
        if (file != null)
        {
            FileChangeCoordinator.getInstance().clearExpectation(file.toPath());
        }
    }

    /**
     * Creates a new directory under the given parent directory.
     *
     * <p>The folder name is sanitised to remove invalid characters. If the
     * sanitised name is empty, an {@link IllegalArgumentException} is thrown.
     * If a file or folder with the sanitised name already exists, a
     * {@link FileAlreadyExistsException} is thrown.</p>
     *
     * <p>An expectation is registered for the new folder before the operation.
     * On success, the parent folder is refreshed in the UI. On failure, the
     * expectation is cleared.</p>
     *
     * @param  parent the parent directory
     * @param  name   the desired folder name (may contain invalid characters)
     * 
     * @return the created {@link File} object
     * 
     * @throws IllegalArgumentException      if the sanitised name is empty
     * @throws FileAlreadyExistsException    if the target already exists
     * @throws IOException                   if the creation fails for any other
     *                                       reason
     */
    public File createDirectory(File parent, String name) throws IOException
    {
        String sanitized = sanitizeFolderName(name);
        if (sanitized.isEmpty()) 
        {
            throw new IllegalArgumentException("Invalid directory name");
        }

        File newFolder = new File(parent, sanitized);
        expectChange(newFolder);

        try
        {
            File created = fileIO.createDirectory(parent, name);
            refreshUI(parent);
            return created;
        }
        catch (IOException e)
        {
            clearExpectation(newFolder);
            throw e;
        }
    }

    /**
     * Attempts to move the given file or folder to the OS trash/recycle bin.
     *
     * <p>An expectation is registered before the operation. On success, the
     * parent folder is refreshed. On failure (including unsupported platform),
     * the expectation is cleared.</p>
     *
     * @param file the file or directory to move to trash
     * @return {@code true} if successfully moved to trash,
     *         {@code false} if trash is not supported or the operation failed
     */
    public boolean moveToTrash(File file)
    {
        // Pause watching if it's a directory
        pauseWatching(file);
        expectChange(file);

        try 
        {
            boolean moved = fileIO.moveToTrash(file);
            if (moved)
            {
                refreshUI(file.getParentFile());
            }
            else
            {
                clearExpectation(file);
            }
            return moved;
        }
        finally 
        {
            // Resume watching after the operation
            resumeWatching();
        }
    }

    /**
     * Permanently deletes a file or directory recursively.
     *
     * <p>An expectation is registered and the parent folder is refreshed on
     * success. On failure, the expectation is cleared.</p>
     *
     * <p>This method is a convenience that calls
     * {@link #deleteRecursive(File, boolean, boolean)} with
     * {@code registerExpectation = true} and {@code triggerRefresh = true}.</p>
     *
     * @param file the file or directory to delete
     * @throws IOException if deletion fails
     */
    public void deleteRecursive(File file) throws IOException
    {
        deleteRecursive(file, true, true);
    }

    /**
     * Permanently deletes a file or directory with explicit control over
     * expectation registration and refresh triggering.
     *
     * <p>This overload is useful when combining deletions in a batch
     * (e.g., after a cut operation) where the caller wants to manage
     * expectations and refreshes manually.</p>
     *
     * @param file                 the file or directory to delete
     * @param registerExpectation  if {@code true}, an expectation is registered
     *                             before deletion; if {@code false}, no
     *                             expectation is set (used when the caller
     *                             has already registered it)
     * @param triggerRefresh       if {@code true}, the parent folder is
     *                             refreshed after successful deletion
     * @throws IOException         if deletion fails
     */
    public void deleteRecursive(File file, boolean registerExpectation, boolean triggerRefresh) throws IOException
    {
        // Pause watching if it's a directory
        pauseWatching(file);

        if (registerExpectation) { expectChange(file); }
        try
        {
            fileIO.deleteRecursive(file);
            if (triggerRefresh && file.getParentFile() != null)
            {
                refreshUI(file.getParentFile());
            }
        }
        catch (IOException e)
        {
            if (registerExpectation) { clearExpectation(file); }
            throw e;
        }
        finally 
        {
            // Resume watching after the operation
            resumeWatching();
        }
    }

    /**
     * Recursively counts all regular files inside the given folder.
     *
     * <p>This is a read‑only operation and does not involve expectations or
     * refreshes. It simply delegates to {@link FileIO#countFilesRecursive(File)}.</p>
     *
     * @param  folder the folder to count
     * @return total number of files (not directories) in the folder and its
     *         subfolders
     */
    public long countFilesRecursive(File folder)
    {
        return fileIO.countFilesRecursive(folder);
    }

    /**
     * Renames a file or folder within its parent directory.
     *
     * <p>The new name is sanitised; if the sanitised name is empty, an
     * {@link IllegalArgumentException} is thrown. If the target already
     * exists, a {@link FileAlreadyExistsException} is thrown.</p>
     *
     * <p>Expectations are registered for both the old and new paths.
     * On success, the parent folder is refreshed. On failure, both
     * expectations are cleared.</p>
     *
     * @param file    the file or directory to rename
     * @param newName the desired new name (may contain invalid characters)
     * 
     * @return the renamed {@link File} object
     * 
     * @throws IllegalArgumentException      if the sanitised name is empty
     * @throws FileAlreadyExistsException    if the target already exists
     * @throws IOException                   if the rename fails for any other
     *                                       reason
     */
    public File rename(File file, String newName) throws IOException
    {
        String sanitized = sanitizeFolderName(newName.trim());
        if (sanitized.isEmpty()) throw new IllegalArgumentException("Invalid directory name");

        File newFile = new File(file.getParentFile(), sanitized);
        if (newFile.exists())
        {
            throw new FileAlreadyExistsException("File already exists in :" + newFile.getAbsolutePath());
        }

        // Pause watching if it's a directory
        pauseWatching(file);

        expectChange(file);    // old path (ENTRY_DELETE)
        expectChange(newFile); // new path (ENTRY_CREATE)

        try
        {
            File renamed = fileIO.rename(file, newName);
            refreshUI(file.getParentFile());
            return renamed;
        }
        catch (IOException e)
        {
            clearExpectation(file);
            clearExpectation(newFile);
            throw e;
        }
        finally 
        {
            // Resume watching after the operation
            resumeWatching();
        }
    }

    /**
     * Moves a single file or directory to another folder.
     *
     * <p><b>Note:</b> This method is {@code private} and should not be called
     * directly. Use {@link #moveBatch(List, File)} for all move operations,
     * as it ensures both source and destination folders are refreshed
     * correctly.</p>
     *
     * <p>Expectations are registered for both source and destination paths.
     * On success, no refresh is performed here - that is handled by the caller
     * ({@code moveBatch}). On failure, expectations are cleared.</p>
     *
     * @param  file       the source file to move
     * @param  destDir    the destination folder
     * @return the moved file (now located in {@code destDir})
     * @throws IOException if the move operation fails
     */
    private File move(File file, File destDir) throws IOException
    {
        if (file == null || destDir == null || !file.exists())
        {
            throw new IllegalArgumentException("Source file or destination directory does not exist.");
        }

        File destFile = new File(destDir, file.getName());

        // Pause watching if it's a directory
        pauseWatching(file);

        expectChange(file);     // source path
        expectChange(destFile); // destination path

        try
        {
            File moved = fileIO.move(file, destDir);
            return moved;
        }
        catch (IOException e)
        {
            clearExpectation(file);
            clearExpectation(destFile);
            throw e;
        }
        finally 
        {
            // Resume watching after the operation
            resumeWatching();
        }
    }

    /**
     * Moves a batch of files and/or directories to a destination folder.
     *
     * <p>This is the <b>primary API for moves</b> in the application.
     * It attempts to move each file individually; if one fails, the operation
     * continues with the remaining files. The result contains the number of
     * successes and a list of files that could not be moved.</p>
     *
     * <p>Expectations are registered for each source and destination pair
     * inside {@code move()}. After all moves are attempted, the UI is
     * refreshed for <b>both</b> the source parents (if any files were
     * successfully moved out) and the destination folder (which gained
     * new files).</p>
     *
     * @param files   the list of files/directories to move
     * @param destDir the target folder (must be a directory)
     * @return a {@link BatchResult} containing the number of successes and
     *         any failed files
     */
    public BatchResult moveBatch(List<File> files, File destDir)
    {
        int movedCount = 0;
        List<File> failedFiles = new ArrayList<>();
        Set<File> updatedSourceParents = new HashSet<>();

        for (File file : files)
        {
            File sourceParent = file.getParentFile();
            if (sourceParent != null && sourceParent.equals(destDir))
            {
                continue;
            }
            try
            {
                move(file, destDir);
                movedCount++;
                if (sourceParent != null)
                {
                    updatedSourceParents.add(sourceParent);
                }
            }
            catch (IOException | IllegalArgumentException ex)
            {
                failedFiles.add(file);
            }
        }

        if (movedCount > 0)
        {
            // Refresh source locations that lost files
            for (File parent : updatedSourceParents)
            {
                refreshUI(parent);
            }

            // Refresh target location that gained files
            refreshUI(destDir);
        }
        return new BatchResult(movedCount, failedFiles);
    }

    /**
     * Sanitises a folder/file name by removing characters invalid on
     * Windows, macOS, and Linux.
     *
     * <p>This is a pass‑through to {@link FileIO#sanitizeFolderName(String)}.</p>
     *
     * @param name the raw name to sanitise
     * @return the sanitised name (may be empty)
     */
    public String sanitizeFolderName(String name)
    {
        return fileIO.sanitizeFolderName(name);
    }

    /**
     * Safely triggers the refresh callback on the JavaFX Application Thread.
     * This ensures all UI updates happen on the correct thread, regardless of
     * where the filesystem operation was executed.
     * 
     * @param folder the folder need to be refreshed
     */
    public void refreshUI(File folder)
    {
        if(folder == null) return;
        if (Platform.isFxApplicationThread())
        {
            // Already on the FX thread
            refreshCallback.accept(folder);
        }
        else
        {
            // Called from a background thread (BackgroundExecutor, etc.)
            // must marshal to the FX thread.
            Platform.runLater(() -> refreshCallback.accept(folder));
        }
    }

    /**
     * Pauses watching for the given file and all its descendants.
     * Only applies if the file is a directory and the watch service is available.
     */
    private void pauseWatching(File file) 
    {
        if (file != null && file.exists() && file.isDirectory() && folderWatchService != null) 
        {
            folderWatchService.pauseWatchingSubtree(file);
        }
    }

    /**
     * Resumes watching the entire project root after a structural change.
     * Only applies if the watch service is available.
     */
    private void resumeWatching() 
    {
        if (folderWatchService != null && watchRootSupplier != null) 
        {
            File root = watchRootSupplier.get();
            if (root != null && root.exists() && root.isDirectory())
            {
                folderWatchService.resumeWatching(root);
            }
        }
    }
}