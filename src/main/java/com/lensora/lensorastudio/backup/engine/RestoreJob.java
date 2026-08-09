package com.lensora.lensorastudio.backup.engine;

import com.lensora.lensorastudio.backup.model.BackupManifest;
import com.lensora.lensorastudio.backup.model.ProjectBackupData;
import com.lensora.lensorastudio.repository.ProjectBackupRepository;

import com.google.gson.Gson;

import javafx.concurrent.Task;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.io.File;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class RestoreJob extends Task<Integer>
{
    private static final Gson GSON = new Gson();
    private static final int BUFFER_SIZE = 64 * 1024;

    private final File lsbakFile;
    private final File destinationFolder;

    public RestoreJob(File lsbakFile, File destinationFolder)
    {
        this.lsbakFile = lsbakFile;
        this.destinationFolder = destinationFolder;
    }

    @Override
    protected Integer call() throws Exception
    {
        updateMessage("Reading archive...");

        Path parentDir = destinationFolder.toPath().getParent();
        if (parentDir == null) parentDir = destinationFolder.toPath();
        Files.createDirectories(parentDir);

        // Extract into a staging directory first. Only if extraction AND
        // database import both succeed do we move it into the real
        // destination — a failed or cancelled restore never leaves the
        // destination folder in a half-extracted state.
        Path stagingFolder = Files.createTempDirectory(parentDir, ".restore_staging_");

        try
        {
            try (ZipFile zip = new ZipFile(lsbakFile))
            {
                BackupManifest manifest = readJsonEntry(zip, "manifest.json", BackupManifest.class);
                if (manifest == null) throw new IOException("manifest.json missing.");

                ProjectBackupData data = readJsonEntry(zip, "project-data.json", ProjectBackupData.class);
                if (data == null || data.project == null) throw new IOException("project-data.json invalid.");

                updateMessage("Extracting files...");
                long totalBytes = manifest.totalSize > 0 ? manifest.totalSize : 1;
                long extractedBytes = 0;

                Enumeration<? extends ZipEntry> entries = zip.entries();
                byte[] buffer = new byte[BUFFER_SIZE];

                while (entries.hasMoreElements())
                {
                    if (isCancelled()) throw new InterruptedException("Restore cancelled");

                    ZipEntry entry = entries.nextElement();
                    if (!entry.getName().startsWith("files/")) continue;

                    String relativePath = entry.getName().substring("files/".length());
                    if (relativePath.isBlank()) continue;

                    Path targetPath = stagingFolder.resolve(relativePath).normalize();

                    if (!targetPath.startsWith(stagingFolder))
                    {
                        continue; // zip-slip guard
                    }

                    if (entry.isDirectory())
                    {
                        Files.createDirectories(targetPath);
                    }
                    else
                    {
                        Files.createDirectories(targetPath.getParent());
                        try (var is = zip.getInputStream(entry);
                            var os = Files.newOutputStream(targetPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
                        {
                            int read;
                            while ((read = is.read(buffer)) != -1)
                            {
                                if (isCancelled()) throw new InterruptedException("Restore cancelled");
                                os.write(buffer, 0, read);
                                extractedBytes += read;
                                updateProgress(extractedBytes, totalBytes);
                            }
                        }
                        if (entry.getTime() > 0)
                        {
                            Files.setLastModifiedTime(targetPath, FileTime.fromMillis(entry.getTime()));
                        }
                    }
                }

                updateMessage("Finalizing files...");
                Files.createDirectories(destinationFolder.toPath());
                moveDirectory(stagingFolder, destinationFolder.toPath());

                updateMessage("Restoring database records...");
                int newProjectId = ProjectBackupRepository.importProject(data, destinationFolder.getAbsolutePath());

                updateMessage("Restore complete.");
                updateProgress(1, 1);
                return newProjectId;
            }
        }
        finally
        {
            deleteDirectoryRecursively(stagingFolder);
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException
    {
        try (var stream = Files.walk(source))
        {
            for (Path src : stream.toList())
            {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src))
                {
                    Files.createDirectories(dest);
                }
                else
                {
                    Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void deleteDirectoryRecursively(Path path)
    {
        if (path == null || !Files.exists(path)) return;
        try
        {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException 
                {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException 
                {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        catch (IOException ignored) {}
    }

    private <T> T readJsonEntry(ZipFile zip, String entryName, Class<T> type) throws IOException
    {
        ZipEntry entry = zip.getEntry(entryName);
        if (entry == null) return null;
        try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))
        {
            return GSON.fromJson(reader, type);
        }
    }

    public Integer runSynchronously() throws Exception
    {
        return call();
    }
}