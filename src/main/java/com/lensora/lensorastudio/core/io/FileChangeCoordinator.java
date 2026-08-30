package com.lensora.lensorastudio.core.io;

import com.lensora.lensorastudio.core.threading.BackgroundExecutor;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Single source of truth distinguishing IN-APP file changes from EXTERNAL
 * ones - so refresh logic never has to guess based on elapsed time.
 *
 * <p>How it works:
 * <ol>
 *   <li>Before/immediately after Lensora performs a filesystem operation
 *       (create/rename/move/delete/paste), it calls {@link #expect(Path, Path)} to
 *       declare "I just changed this path myself."</li>
 *   <li>When {@code FolderWatchService} later observes a raw OS-level change for
 *       that same path, it calls {@link #consumeIfExpected(Path)} <b>first</b>. If the
 *       change was expected, it is suppressed – the app already refreshed
 *       synchronously when it performed the operation – and the expectation is
 *       cleared so it doesn't accidentally suppress a <i>later</i>, genuinely
 *       unrelated change to the same path. If NOT expected, it's a real external
 *       change and gets forwarded.</li>
 *   <li>A generous safety‑net expiry exists ONLY to prevent a leaked
 *       expectation from lingering forever if the OS never delivers a
 *       corresponding event at all (can happen on some network/NAS mounts) –
 *       it is NOT what suppresses duplicates in the normal case; explicit
 *       consumption by the real matching event is.</li>
 * </ol>
 *
 * <p>This implementation marks every ancestor directory up to the given
 * {@code watchRoot}. That fully consumes Windows' multi‑level mtime cascades
 * (e.g. when a child is created, the parent's mtime changes, and Windows
 * reports that as a change on the grandparent's watch).
 */
public final class FileChangeCoordinator
{
    private static final FileChangeCoordinator INSTANCE = new FileChangeCoordinator();

    /** Fallback‑only leak guard - not part of the suppression logic itself. */
    private static final long EXPIRY_MS = 30_000;

    private final ConcurrentHashMap<Path, Long> expectedChanges = new ConcurrentHashMap<>();

    private FileChangeCoordinator()
    {
        BackgroundExecutor.getInstance().scheduleAtFixedRate(
                this::cleanupExpired,
                10, 10, TimeUnit.SECONDS
        );
    }

    public static FileChangeCoordinator getInstance()
    {
        return INSTANCE;
    }

    /**
     * Declares that Lensora itself just changed the given path. Also marks
     * the parent, since some platforms (Windows) additionally report a
     * directory's own mtime change on its PARENT's watch too.
     *
     * <p>Marks {@code changedPath} and EVERY ancestor directory up to
     * {@code watchRoot} (if provided). This consumes Windows' multi‑level
     * mtime cascades completely.
     *
     * @param changedPath the file/directory that was changed by the app
     * @param watchRoot   the root of the watched project (may be {@code null});
     *                    if {@code null}, only the changed path and its immediate
     *                    parent are marked as a safety fallback.
     */
    public void expect(Path changedPath, Path watchRoot)
    {
        if (changedPath == null) return;

        long now = System.currentTimeMillis();
        Path current = changedPath;
        int count = 0;

        while (current != null)
        {
            expectedChanges.put(current, now);
            if (watchRoot != null && current.equals(watchRoot)) break;
            if (watchRoot == null && count >= 1) break; // changedPath + parent fallback
            count++;
            current = current.getParent();
        }
    }

    /**
     * Called by the watch service for every raw change it observes.
     *
     * @param path the path that triggered the watch event
     * @return {@code true} if this change was caused by Lensora itself and
     *         should be suppressed; {@code false} otherwise (it's a genuine
     *         external change).
     */
    public boolean consumeIfExpected(Path path)
    {
        return path != null && expectedChanges.remove(path) != null;
    }

    /**
     * Manually removes an expectation for the given path.
     * Useful when an operation fails after the expectation was registered,
     * so that a subsequent external change is not incorrectly suppressed.
     */
    public void clearExpectation(Path path)
    {
        if (path != null)
        {
            expectedChanges.remove(path);
        }
    }

    /** Removes all entries that have exceeded the safety expiry. */
    private void cleanupExpired()
    {
        long now = System.currentTimeMillis();
        expectedChanges.entrySet().removeIf(entry -> now - entry.getValue() > EXPIRY_MS);
    }
}