// package com.lensora.lensorastudio.core.io;

// import java.awt.Desktop;
// import java.io.File;
// import java.io.IOException;
// import java.nio.file.AtomicMoveNotSupportedException;
// import java.nio.file.FileAlreadyExistsException;
// import java.nio.file.FileVisitResult;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.SimpleFileVisitor;
// import java.nio.file.StandardCopyOption;
// import java.nio.file.attribute.BasicFileAttributes;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.function.Consumer;
// import java.util.function.Supplier;

// /**
//  * Shared low-level filesystem mutation logic: create/delete/trash/rename/move a
//  * file or folder, mark the change as self-caused via
//  * FileChangeCoordinator (so FolderWatchService doesn't double-refresh
//  * for it), and trigger the app's single unified refresh callback.
//  *
//  * Used by BOTH file-listing operations (FileActionService) and
//  * folder-tree operations (FolderContextMenuManager) so there is exactly
//  * one path for "a filesystem change happened, here's how the UI finds
//  * out about it" no separate tree-only refresh mechanism.
//  */
// public class FileSystemOperations
// {
//     public record BatchResult(int succeeded, List<File> failedFiles) {}

//     private final Consumer<File> refreshCallback;
//     private final Supplier<File> watchRootSupplier;

//     public FileSystemOperations(Consumer<File> refreshCallback, Supplier<File> watchRootSupplier)
//     {
//         this.refreshCallback = refreshCallback;
//         this.watchRootSupplier = watchRootSupplier;
//     }

//     public void expectChange(File file)
//     {
//         File root = watchRootSupplier != null ? watchRootSupplier.get() : null;
//         FileChangeCoordinator.getInstance().expect(
//                 file.toPath(),
//                 root != null ? root.toPath() : null);
//     }

//     public void clearExpectation(File file)
//     {
//         if (file != null)
//         {
//             FileChangeCoordinator.getInstance().clearExpectation(file.toPath());
//         }
//     }

//     /** Creates a new folder under parent. Returns the created File. Marks the change and triggers refresh. */
//     public File createDirectory(File parent, String name) throws IOException
//     {
//         String sanitized = sanitizeFolderName(name);
//         if (sanitized.isEmpty())
//         {
//             throw new IllegalArgumentException("Invalid directory name");
//         }

//         File newFolder = new File(parent, sanitized);
//         if (newFolder.exists())
//         {
//             throw new FileAlreadyExistsException(newFolder.getAbsolutePath());
//         }

//         expectChange(newFolder);
//         try
//         {
//             Files.createDirectory(newFolder.toPath());
//             refreshCallback.accept(parent);
//             return newFolder;
//         }
//         catch (IOException e)
//         {
//             clearExpectation(newFolder);
//             throw e;
//         }
//     }

//     /** Attempts to move a file/folder to the OS trash. Marks the change and triggers refresh on success. */
//     public boolean moveToTrash(File file)
//     {
//         if (!Desktop.isDesktopSupported()) return false;
//         Desktop desktop = Desktop.getDesktop();
//         if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) return false;

//         expectChange(file);
//         boolean moved = desktop.moveToTrash(file);
//         if (moved)
//         {
//             refreshCallback.accept(file.getParentFile());
//         }
//         else
//         {
//             clearExpectation(file);
//         }
//         return moved;
//     }

//     /** Permanently deletes a file/folder (recursively for directories). Marks the change and triggers refresh. */
//     public void deleteRecursive(File file) throws IOException
//     {
//         deleteRecursive(file, true, true);
//     }

//     /** 
//      * Deletes a file/folder with explicit control over watcher expectations and refresh notifications.
//      */
//     public void deleteRecursive(File file, boolean registerExpectation, boolean triggerRefresh) throws IOException
//     {
//         if (registerExpectation) { expectChange(file); }
//         try
//         {
//             deleteRecursiveInternal(file);
//             if (triggerRefresh && file.getParentFile() != null) 
//             { 
//                 refreshCallback.accept(file.getParentFile()); 
//             }
//         }
//         catch(IOException e)
//         {
//             if (registerExpectation) { clearExpectation(file); }
//             throw e;
//         }
//     }

//     /** Internal Method: Do not use directly */
//     private void deleteRecursiveInternal(File file) throws IOException
//     {
//         Path path = file.toPath();
//         if (!Files.exists(path)) return;

//         Files.walkFileTree(path, new SimpleFileVisitor<Path>()
//         {
//             @Override
//             public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
//             {
//                 Files.delete(file);
//                 return FileVisitResult.CONTINUE;
//             }

//             @Override
//             public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException
//             {
//                 if (exc != null) throw exc;
//                 Files.delete(dir);
//                 return FileVisitResult.CONTINUE;
//             }
//         });
//     }

//     public long countFilesRecursive(File folder)
//     {
//         File[] children = folder.listFiles();
//         if (children == null) return 0;

//         long count = 0;
//         for (File child : children)
//         {
//             count += child.isDirectory() ? countFilesRecursive(child) : 1;
//         }
//         return count;
//     }

//     public File rename(File file, String newName) throws IOException
//     {
//         String sanitized = sanitizeFolderName(newName.trim());
//         if (sanitized.isEmpty()) 
//         {
//             throw new IllegalArgumentException("Invalid file name");
//         }
//         File newFile = new File(file.getParentFile(), sanitized);
//         if (newFile.exists())
//         {
//             throw new FileAlreadyExistsException("File already exists in :" + newFile.getAbsolutePath());
//         }

//         expectChange(file);    // old path (ENTRY_DELETE)
//         expectChange(newFile); // new path (ENTRY_CREATE)

//         try
//         {
//             Files.move(file.toPath(), newFile.toPath());
//             refreshCallback.accept(file.getParentFile());
//             return newFile;
//         }
//         catch (IOException e)
//         {
//             clearExpectation(file);
//             clearExpectation(newFile);
//             throw e;
//         }
//     }

//     /** Moves a single file or directory into the destination folder. */
//     private File move(File file, File destDir) throws IOException
//     {
//         if (file == null || destDir == null || !file.exists())
//         {
//             throw new IllegalArgumentException("Source file or destination directory does not exist.");
//         }

//         if (file.getParentFile() != null && file.getParentFile().equals(destDir))
//         {
//             return file; // Already in target directory
//         }

//         // Prevent moving a directory into itself or its own subdirectory
//         if (file.isDirectory() && destDir.toPath().startsWith(file.toPath()))
//         {
//             throw new IllegalArgumentException("Cannot move a folder into itself or a subfolder of itself.");
//         }

//         File destFile = new File(destDir, file.getName());

//         if (destFile.exists())
//         {
//             throw new FileAlreadyExistsException(destFile.getAbsolutePath());
//         }

//         expectChange(file);     // source path
//         expectChange(destFile); // destination path

//         try
//         {
//             if (isSameFileStore(file, destDir))
//             {
//                 try
//                 {
//                     Files.move(file.toPath(), destFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
//                 }
//                 catch (AtomicMoveNotSupportedException e)
//                 {
//                     Files.move(file.toPath(), destFile.toPath());
//                 }
//             }
//             else
//             {
//                 // Cross-device fallback: Copy source first
//                 try
//                 {
//                     copyRecursiveInternal(file, destFile);
//                 }
//                 catch (IOException copyEx)
//                 {
//                     if (destFile.exists())
//                     {
//                         try { deleteRecursiveInternal(destFile); } catch (Exception ignored) {}
//                     }
//                     throw copyEx;
//                 }

//                 // Delete source after verified copy; failure preserves destination
//                 deleteRecursiveInternal(file);
//             }
//         }
//         catch (IOException e)
//         {
//             clearExpectation(file);
//             clearExpectation(destFile);
//             throw e;
//         }

//         return destFile;
//     }

//     /** Moves a batch of files or directories into the destination folder. */
//     public BatchResult moveBatch(List<File> files, File destDir)
//     {
//         int movedCount = 0;
//         List<File> failedFiles = new ArrayList<>();

//         for (File file : files)
//         {
//             if (file.getParentFile() != null && file.getParentFile().equals(destDir))
//             {
//                 continue;
//             }
//             try
//             {
//                 move(file, destDir);
//                 movedCount++;
//             }
//             catch (IOException ex)
//             {
//                 failedFiles.add(file);
//             }
//         }

//         if (movedCount > 0)
//         {
//             refreshCallback.accept(destDir);
//         }
//         return new BatchResult(movedCount, failedFiles);
//     }

//     /** Internal Method: Do not use directly. 
//      *  Robust cross-drive recursive copy using NIO Path API and attribute preservation. */
//     private void copyRecursiveInternal(File src, File dest) throws IOException
//     {
//         Path srcPath = src.toPath();
//         Path destPath = dest.toPath();

//         Files.walkFileTree(srcPath, new SimpleFileVisitor<Path>()
//         {
//             @Override
//             public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException
//             {
//                 Path targetDir = destPath.resolve(srcPath.relativize(dir));
//                 if (!Files.exists(targetDir))
//                 {
//                     Files.createDirectories(targetDir);
//                 }
//                 return FileVisitResult.CONTINUE;
//             }

//             @Override
//             public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException
//             {
//                 Path targetFile = destPath.resolve(srcPath.relativize(file));
//                 Files.copy(file, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
//                 return FileVisitResult.CONTINUE;
//             }
//         });
//     }

//     /** Strips characters that are invalid in folder names on Windows/macOS/Linux. */
//     public String sanitizeFolderName(String name)
//     {
//         return name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
//     }

//     private boolean isSameFileStore(File src, File destDir) throws IOException
//     {
//         return Files.getFileStore(src.toPath()).equals(Files.getFileStore(destDir.toPath()));
//     }
// }






package com.lensora.lensorastudio.core.io;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared high-level filesystem operation wrapper: handles expectation tracking 
 * via FileChangeCoordinator and triggers app refresh callbacks around raw I/O execution.
 */
public class InstrumentedFileIO
{
    public record BatchResult(int succeeded, List<File> failedFiles) {}

    private final Consumer<File> refreshCallback;
    private final Supplier<File> watchRootSupplier;
    private final FileIO fileIO;

    public InstrumentedFileIO (Consumer<File> refreshCallback, Supplier<File> watchRootSupplier)
    {
        this(refreshCallback, watchRootSupplier, new FileIO());
    }

    public InstrumentedFileIO (Consumer<File> refreshCallback, Supplier<File> watchRootSupplier, FileIO fileIO)
    {
        this.refreshCallback = refreshCallback;
        this.watchRootSupplier = watchRootSupplier;
        this.fileIO = fileIO;
    }

    public void expectChange(File file)
    {
        File root = watchRootSupplier != null ? watchRootSupplier.get() : null;
        FileChangeCoordinator.getInstance().expect(
                file.toPath(),
                root != null ? root.toPath() : null);
    }

    public void clearExpectation(File file)
    {
        if (file != null)
        {
            FileChangeCoordinator.getInstance().clearExpectation(file.toPath());
        }
    }

    /** Creates a new folder under parent. Returns the created File. Marks the change and triggers refresh. */
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
            refreshCallback.accept(parent);
            return created;
        }
        catch (IOException e)
        {
            clearExpectation(newFolder);
            throw e;
        }
    }

    /** Attempts to move a file/folder to the OS trash. Marks the change and triggers refresh on success. */
    public boolean moveToTrash(File file)
    {
        expectChange(file);
        boolean moved = fileIO.moveToTrash(file);
        if (moved)
        {
            refreshCallback.accept(file.getParentFile());
        }
        else
        {
            clearExpectation(file);
        }
        return moved;
    }

    /** Permanently deletes a file/folder (recursively for directories). Marks the change and triggers refresh. */
    public void deleteRecursive(File file) throws IOException
    {
        deleteRecursive(file, true, true);
    }

    /** 
     * Deletes a file/folder with explicit control over watcher expectations and refresh notifications.
     */
    public void deleteRecursive(File file, boolean registerExpectation, boolean triggerRefresh) throws IOException
    {
        if (registerExpectation) { expectChange(file); }
        try
        {
            fileIO.deleteRecursive(file);
            if (triggerRefresh && file.getParentFile() != null)
            {
                refreshCallback.accept(file.getParentFile());
            }
        }
        catch (IOException e)
        {
            if (registerExpectation) { clearExpectation(file); }
            throw e;
        }
    }

    public long countFilesRecursive(File folder)
    {
        return fileIO.countFilesRecursive(folder);
    }

    public File rename(File file, String newName) throws IOException
    {
        String sanitized = sanitizeFolderName(newName.trim());
        if (sanitized.isEmpty()) throw new IllegalArgumentException("Invalid directory name");

        File newFile = new File(file.getParentFile(), sanitized);

        expectChange(file);    // old path (ENTRY_DELETE)
        expectChange(newFile); // new path (ENTRY_CREATE)

        try
        {
            File renamed = fileIO.rename(file, newName);
            refreshCallback.accept(file.getParentFile());
            return renamed;
        }
        catch (IOException e)
        {
            clearExpectation(file);
            clearExpectation(newFile);
            throw e;
        }
    }

    /** Moves a single file or directory into the destination folder. */
    private File move(File file, File destDir) throws IOException
    {
        if (file == null || destDir == null || !file.exists())
        {
            throw new IllegalArgumentException("Source file or destination directory does not exist.");
        }

        File destFile = new File(destDir, file.getName());

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
    }

    /** Moves a batch of files or directories into the destination folder. */
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
                refreshCallback.accept(parent);
            }

            // Refresh target location that gained files
            refreshCallback.accept(destDir);
        }
        return new BatchResult(movedCount, failedFiles);
    }

    /** Strips characters that are invalid in folder names on Windows/macOS/Linux. */
    public String sanitizeFolderName(String name)
    {
        return fileIO.sanitizeFolderName(name);
    }
}