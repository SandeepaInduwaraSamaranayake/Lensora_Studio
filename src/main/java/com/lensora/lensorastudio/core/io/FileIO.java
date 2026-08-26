package com.lensora.lensorastudio.core.io;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Rarely used directly. Use InstrumentedFileIO instead.
 * Pure filesystem and OS-level operations.
 * Free of application event-tracking, watchers, or UI dependencies.
 */
public class FileIO
{
    public File createDirectory(File parent, String name) throws IOException
    {
        String sanitized = sanitizeFolderName(name);
        if (sanitized.isEmpty())
        {
            throw new IllegalArgumentException("Invalid directory name");
        }

        File newFolder = new File(parent, sanitized);
        if (newFolder.exists())
        {
            throw new FileAlreadyExistsException(newFolder.getAbsolutePath());
        }

        Files.createDirectory(newFolder.toPath());
        return newFolder;
    }

    public boolean moveToTrash(File file)
    {
        if (!Desktop.isDesktopSupported()) return false;
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) return false;

        return desktop.moveToTrash(file);
    }

    public void deleteRecursive(File file) throws IOException
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
        String sanitized = sanitizeFolderName(newName.trim());
        if (sanitized.isEmpty())
        {
            throw new IllegalArgumentException("Invalid file name");
        }
        File newFile = new File(file.getParentFile(), sanitized);
        if (newFile.exists())
        {
            throw new FileAlreadyExistsException("File already exists in :" + newFile.getAbsolutePath());
        }

        Files.move(file.toPath(), newFile.toPath());
        return newFile;
    }

    public File move(File file, File destDir) throws IOException
    {
        if (file == null || destDir == null || !file.exists())
        {
            throw new IllegalArgumentException("Source file or destination directory does not exist.");
        }

        if (file.getParentFile() != null && file.getParentFile().equals(destDir))
        {
            return file;
        }

        if (file.isDirectory() && destDir.toPath().startsWith(file.toPath()))
        {
            throw new IllegalArgumentException("Cannot move a folder into itself or a subfolder of itself.");
        }

        File destFile = new File(destDir, file.getName());

        if (destFile.exists())
        {
            throw new FileAlreadyExistsException(destFile.getAbsolutePath());
        }

        if (isSameFileStore(file, destDir))
        {
            try
            {
                Files.move(file.toPath(), destFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                Files.move(file.toPath(), destFile.toPath());
            }
        }
        else
        {
            try
            {
                copyRecursiveInternal(file, destFile);
            }
            catch (IOException copyEx)
            {
                if (destFile.exists())
                {
                    try { deleteRecursive(destFile); } catch (Exception ignored) {}
                }
                throw copyEx;
            }

            deleteRecursive(file);
        }

        return destFile;
    }

    public void copyRecursiveInternal(File src, File dest) throws IOException
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
                Files.copy(file, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public String sanitizeFolderName(String name)
    {
        return name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    public boolean isSameFileStore(File src, File destDir) throws IOException
    {
        return Files.getFileStore(src.toPath()).equals(Files.getFileStore(destDir.toPath()));
    }
}