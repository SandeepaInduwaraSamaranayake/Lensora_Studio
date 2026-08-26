package com.lensora.lensorastudio.core.io;

import com.lensora.lensorastudio.core.threading.BackgroundExecutor;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Single source of truth distinguishing IN-APP file changes from EXTERNAL
 * ones - so refresh logic never has to guess based on elapsed time.
 *
 * How it works:
 *  1. Before/immediately after Lensora performs a filesystem operation
 *     (create/rename/move/delete/paste), it calls expect(path) to
 *     declare "I just changed this path myself."
 *  2. When FolderWatchService later observes a raw OS-level change for
 *     that same path, it calls consumeIfExpected(path) FIRST. If the
 *     change was expected, it's suppressed - the app already refreshed
 *     synchronously when it performed the operation - and the
 *     expectation is cleared so it doesn't accidentally suppress a
 *     LATER, genuinely unrelated change to the same path. If NOT
 *     expected, it's a real external change and gets forwarded.
 *  3. A generous safety-net expiry exists ONLY to prevent a leaked
 *     expectation from lingering forever if the OS never delivers a
 *     corresponding event at all (can happen on some network/NAS
 *     mounts) - it is NOT what suppresses duplicates in the normal
 *     case; explicit consumption by the real matching event is.
 */
public final class FileChangeCoordinator
{
    private static final FileChangeCoordinator INSTANCE = new FileChangeCoordinator();

    /** Fallback-only leak guard — not part of the suppression logic itself. */
    private static final long SAFETY_EXPIRY_SECONDS = 30;

    private final Set<Path> expectedChanges = ConcurrentHashMap.newKeySet();

    private FileChangeCoordinator() {}

    public static FileChangeCoordinator getInstance() { return INSTANCE; }

    /**
     * Declares that Lensora itself just changed the given path. Also
     * marks the parent, since some platforms (Windows) additionally
     * report a directory's own mtime change on its PARENT's watch too.
     */
    /**
     * Marks changedPath and EVERY ancestor directory up to watchRoot.
     * Consumes Windows' multi-level mtime cascades completely.
     */
    public void expect(Path changedPath, Path watchRoot) {
        if (changedPath == null) return;

        Set<Path> marked = new HashSet<>();
        Path current = changedPath;

        while (current != null) 
        {
            expectedChanges.add(current);
            marked.add(current);

            if (watchRoot != null && current.equals(watchRoot)) break;
            if (watchRoot == null && !marked.isEmpty() && marked.size() >= 2) break; // no root known: changedPath + parent only
            
            current = current.getParent();
        }

        BackgroundExecutor.getInstance().scheduleOnce(
            () -> expectedChanges.removeAll(marked),
            SAFETY_EXPIRY_SECONDS, TimeUnit.SECONDS
        );
    }

    /**
     * Called by the watch service for every raw change it observes.
     * Returns true (and clears the expectation) if this change was
     * caused by Lensora itself and should be suppressed.
     */
    public boolean consumeIfExpected(Path path)
    {
        return expectedChanges.remove(path);
    }
}