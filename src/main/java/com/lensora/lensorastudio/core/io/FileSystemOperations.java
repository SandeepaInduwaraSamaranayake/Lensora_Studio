package com.lensora.lensorastudio.core.io;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared low-level filesystem mutation logic: create/delete/trash/rename/move a
 * file or folder, mark the change as self-caused via
 * FileChangeCoordinator (so FolderWatchService doesn't double-refresh
 * for it), and trigger the app's single unified refresh callback.
 *
 * Used by BOTH file-listing operations (FileActionService) and
 * folder-tree operations (FolderContextMenuManager) so there is exactly
 * one path for "a filesystem change happened, here's how the UI finds
 * out about it" no separate tree-only refresh mechanism.
 */
public class FileSystemOperations
{
    private final Consumer<File> refreshCallback;
    private final Supplier<File> watchRootSupplier;

    public FileSystemOperations(Consumer<File> refreshCallback, Supplier<File> watchRootSupplier)
    {
        this.refreshCallback = refreshCallback;
        this.watchRootSupplier = watchRootSupplier;
    }

    private void expectChange(File file)
    {
        File root = watchRootSupplier != null ? watchRootSupplier.get() : null;
        FileChangeCoordinator.getInstance().expect(
                file.toPath(),
                root != null ? root.toPath() : null);
    }

    /** Creates a new folder under parent. Returns the created File. Marks the change and triggers refresh. */
    public File createDirectory(File parent, String sanitizedName) throws IOException
    {
        File newFolder = new File(parent, sanitizedName);
        expectChange(newFolder);
        Files.createDirectory(newFolder.toPath());
        refreshCallback.accept(parent);
        return newFolder;
    }

    /** Attempts to move a file/folder to the OS trash. Marks the change and triggers refresh on success. */
    public boolean moveToTrash(File file)
    {
        if (!Desktop.isDesktopSupported()) return false;
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) return false;

        expectChange(file);
        boolean moved = desktop.moveToTrash(file);
        if (moved)
        {
            refreshCallback.accept(file.getParentFile());
        }
        return moved;
    }

    /** Permanently deletes a file/folder (recursively for directories). Marks the change and triggers refresh. */
    public void deleteRecursive(File file) throws IOException
    {
        expectChange(file);
        deleteRecursiveInternal(file);
        refreshCallback.accept(file.getParentFile());
    }

    /** Internal Method: Do not use directly */
    private void deleteRecursiveInternal(File file) throws IOException
    {
        Path path = file.toPath();
        if (!Files.exists(path)) return;

        Files.walkFileTree(path, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
            {
                if (exc != null) throw exc;
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public long countFilesRecursive(File folder)
    {
        File[] children = folder.listFiles();
        if (children == null) return 0;

        long count = 0;
        for (File child : children)
        {
            count += child.isDirectory() ? countFilesRecursive(child) : 1;
        }
        return count;
    }

    public File rename(File file, String newName) throws IOException
    {
        File newFile = new File(file.getParentFile(), newName.trim());
        if (newFile.exists())
        {
            throw new FileAlreadyExistsException(newFile.getAbsolutePath());
        }

        expectChange(file);    // old path (ENTRY_DELETE)
        expectChange(newFile); // new path (ENTRY_CREATE)

        Files.move(file.toPath(), newFile.toPath());
        refreshCallback.accept(file.getParentFile());
        return newFile;
    }

    /** Moves a single file or directory into the destination folder. */
    public File move(File file, File destDir) throws IOException
    {
        if (file == null || destDir == null || !file.exists())
        {
            throw new IllegalArgumentException("Source file or destination directory does not exist.");
        }

        if (file.getParentFile() != null && file.getParentFile().equals(destDir))
        {
            return file; // Already in target directory
        }

        // 1. Prevent moving a directory into itself or its own subdirectory
        if (file.isDirectory() && destDir.toPath().startsWith(file.toPath()))
        {
            throw new IllegalArgumentException("Cannot move a folder into itself or a subfolder of itself.");
        }

        File destFile = new File(destDir, file.getName());
        expectChange(file);     // source path
        expectChange(destFile); // destination path

        try
        {
            // Primary fast move (atomic rename on same drive for files & folders)
            Files.move(file.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException ex)
        {
            // Fallback for cross-drive non-empty directory moves
            if (file.isDirectory())
            {
                try
                {
                    copyRecursiveInternal(file, destFile);
                    deleteRecursiveInternal(file);
                }
                catch (IOException copyEx)
                {
                    // Clean up partial destination folder if copy fails midway
                    if (destFile.exists())
                    {
                        try { deleteRecursiveInternal(destFile); } catch (Exception ignored) {}
                    }
                    throw copyEx;
                }
            }
            else
            {
                throw ex;
            }
        }

        return destFile;
    }

    /** Moves a batch of files or directories into the destination folder. */
    public int moveBatch(List<File> files, File destDir) throws IOException
    {
        int movedCount = 0;
        for (File file : files)
        {
            if (file.getParentFile() != null && file.getParentFile().equals(destDir))
            {
                continue;
            }
            move(file, destDir);
            movedCount++;
        }

        if (movedCount > 0)
        {
            refreshCallback.accept(destDir);
        }
        return movedCount;
    }

    /** Internal Method: Do not use directly. 
     *  Robust cross-drive recursive copy using NIO Path API and attribute preservation. */
    private void copyRecursiveInternal(File src, File dest) throws IOException
    {
        Path srcPath = src.toPath();
        Path destPath = dest.toPath();

        Files.walkFileTree(srcPath, new SimpleFileVisitor<Path>()
        {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
            {
                Path targetDir = destPath.resolve(srcPath.relativize(dir));
                if (!Files.exists(targetDir))
                {
                    Files.createDirectories(targetDir);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
            {
                Path targetFile = destPath.resolve(srcPath.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** Strips characters that are invalid in folder names on Windows/macOS/Linux. */
    public String sanitizeFolderName(String name)
    {
        return name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }
}