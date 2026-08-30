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
 * <p>Pure filesystem and OS-level operations.</p>
 *
 * <p>This class is completely free of application‑specific concerns such as
 * event tracking, watch services, or UI callbacks. It is intended to be used
 * <b>only</b> as a low‑level delegate by {@link InstrumentedFileIO}, which
 * adds expectation management and refresh notifications.</p>
 *
 * <p>All methods are self‑contained and throw standard Java I/O exceptions
 * on failure. They are <b>not</b> thread‑safe by themselves - callers must
 * manage concurrency externally.</p>
 * 
 * Rarely used directly. Use InstrumentedFileIO APIs instead.
 */
public class FileIO
{
    /**
     * Creates a new directory under the given parent.
     *
     * <p>The directory name is automatically sanitised to remove characters
     * that are invalid on Windows, macOS, and Linux. If the sanitised name
     * becomes empty, an {@link IllegalArgumentException} is thrown.</p>
     *
     * @param parent the parent directory in which to create the new folder
     * @param name   the desired folder name (may contain invalid characters,
     *               which will be stripped)
     * @return the newly created {@link File} object
     * @throws IllegalArgumentException      if the sanitised name is empty
     * @throws FileAlreadyExistsException   if a file or folder with the
     *                                       sanitised name already exists
     * @throws IOException                   if an I/O error occurs during
     *                                       directory creation
     */
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

    /**
     * Attempts to move the given file or folder to the operating system's
     * trash/recycle bin.
     *
     * <p>This method uses {@link Desktop#moveToTrash(File)} which is
     * supported only on certain platforms (e.g., Windows, macOS).
     * If the platform does not support trash, this method returns
     * {@code false} without throwing an exception.</p>
     *
     * @param file the file or directory to trash
     * @return {@code true} if the file was successfully moved to the trash,
     *         {@code false} if trash is not supported or the operation failed
     * @see Desktop#moveToTrash(File)
     */
    public boolean moveToTrash(File file)
    {
        if (!Desktop.isDesktopSupported()) return false;
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) return false;

        return desktop.moveToTrash(file);
    }

    /**
     * Recursively deletes a file or directory.
     *
     * <p>If the given file is a directory, all its contents (files and
     * subdirectories) are deleted first. If deletion fails at any point,
     * an exception is thrown and the file system may be left in a
     * partially‑deleted state.</p>
     *
     * <p>This method does <b>not</b> use the trash - the deletion is
     * permanent and irreversible.</p>
     *
     * @param file the file or directory to delete
     * @throws IOException if an I/O error occurs during deletion
     */
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

    /**
     * Recursively counts the total number of files (not directories) inside
     * the given folder.
     *
     * <p>Subdirectories are traversed recursively; directories themselves
     * are not counted. If the folder does not exist or is not a directory,
     * this method returns 0.</p>
     *
     * @param folder the folder to count files inside
     * @return the total number of regular files in the folder and all its
     *         subdirectories
     */
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

    /**
     * Renames a file or directory within its parent folder.
     *
     * <p>The new name is automatically sanitised to remove invalid characters.
     * If the sanitised name is empty, an exception is thrown. The operation
     * fails if a file or folder with the new name already exists.</p>
     *
     * @param file    the file or directory to rename
     * @param newName the desired new name (may contain invalid characters,
     *                which will be stripped)
     * @return the renamed {@link File} object
     * @throws IllegalArgumentException      if the sanitised name is empty
     * @throws FileAlreadyExistsException    if the destination already exists
     * @throws IOException                   if the rename fails for any other
     *                                       reason (e.g., cross‑device move
     *                                       not supported)
     */
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

    /**
     * Moves a single file or directory to another folder.
     *
     * <p>This method handles both same‑volume (atomic) and cross‑volume moves.
     * For cross‑volume moves, it transparently falls back to a copy‑then‑delete
     * approach. If the copy fails, any partially copied destination is
     * cleaned up. The operation fails if the destination already contains an
     * entry with the same name.</p>
     *
     * <p><b>Note:</b> This method does <b>not</b> refresh any UI or trigger
     * watch expectations - it is a pure I/O operation. Use
     * {@link InstrumentedFileIO#moveBatch} for coordinated moves that also
     * update the application state.</p>
     *
     * @param  file    the source file or directory to move
     * @param  destDir the destination folder (must be a directory)
     * @return the moved file (now located inside {@code destDir})
     * @throws IllegalArgumentException      if the source or destination is
     *                                       invalid, or the source is a
     *                                       directory that contains the
     *                                       destination
     * @throws FileAlreadyExistsException   if a file or folder with the same
     *                                       name already exists in
     *                                       {@code destDir}
     * @throws IOException                   if the move operation fails for
     *                                       any other reason
     */
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

        // Prevent moving a directory into itself or its own subdirectory
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
            // Cross-device fallback: Copy source first
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

            // Delete source after verified copy; failure preserves destination
            deleteRecursive(file);
        }

        return destFile;
    }

    /**
     * Recursively copies a source file or directory to a destination.
     *
     * <p>This method preserves file attributes and creates any necessary
     * parent directories. It is used internally as the copy step in
     * cross‑volume moves.</p>
     *
     * <p><b>Note:</b> This method is intentionally public to allow testing,
     * but it is considered internal and should not be called directly by
     * application code - use {@link InstrumentedFileIO} instead.</p>
     *
     * @param src  the source file or directory to copy
     * @param dest the destination path (will be created if it does not exist)
     * @throws IOException if an I/O error occurs during copying
     */
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

    /**
     * Sanitises a folder or file name by removing characters that are invalid
     * on Windows, macOS, and Linux.
     *
     * <p>The following characters are removed: {@code \ / : * ? " < > |}.
     * Leading and trailing whitespace is also trimmed.</p>
     *
     * @param  name the raw name to sanitise
     * @return the sanitised name, possibly empty if the original contained
     *         only invalid characters or whitespace
     */
    public String sanitizeFolderName(String name)
    {
        return name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    /**
     * Checks whether the given source file and destination directory reside
     * on the same filesystem (file store).
     *
     * <p>This is used internally to decide whether a move can be performed
     * atomically or requires a copy‑then‑delete fallback.</p>
     *
     * @param src     the source file
     * @param destDir the destination directory
     * @return {@code true} if both paths are on the same file store,
     *         {@code false} otherwise
     * @throws IOException if the file store cannot be determined
     */
    public boolean isSameFileStore(File src, File destDir) throws IOException
    {
        return Files.getFileStore(src.toPath()).equals(Files.getFileStore(destDir.toPath()));
    }
}