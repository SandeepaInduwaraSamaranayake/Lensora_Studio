package com.lensora.lensorastudio.backup.engine;

import com.lensora.lensorastudio.backup.model.BackupManifest;
import com.lensora.lensorastudio.backup.model.ProjectBackupData;
import com.lensora.lensorastudio.repository.ProjectBackupRepository;

import com.google.gson.Gson;

import javafx.concurrent.Task;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class RestoreJob extends Task<Integer>
{
    private static final Gson GSON = new Gson();

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

        try (ZipFile zip = new ZipFile(lsbakFile))
        {
            BackupManifest manifest = readJsonEntry(zip, "manifest.json", BackupManifest.class);
            if (manifest == null)
            {
                throw new IOException("manifest.json missing - not a valid Lensora backup.");
            }

            ProjectBackupData data = readJsonEntry(zip, "project-data.json", ProjectBackupData.class);
            if (data == null || data.project == null)
            {
                throw new IOException("project-data.json missing or invalid.");
            }

            Files.createDirectories(destinationFolder.toPath());

            updateMessage("Extracting files...");
            long totalBytes = manifest.totalSize > 0 ? manifest.totalSize : 1;
            long extracted = 0;

            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements())
            {
                if (isCancelled()) throw new InterruptedException("Restore cancelled");

                ZipEntry entry = entries.nextElement();
                if (!entry.getName().startsWith("files/")) continue;

                String relativePath = entry.getName().substring("files/".length());
                Path targetPath = destinationFolder.toPath().resolve(relativePath).normalize();

                if (!targetPath.startsWith(destinationFolder.toPath()))
                {
                    // Zip-slip guard — refuse to extract outside the destination folder.
                    continue;
                }

                Files.createDirectories(targetPath.getParent());
                Files.copy(zip.getInputStream(entry), targetPath, StandardCopyOption.REPLACE_EXISTING);

                extracted += entry.getSize();
                updateProgress(extracted, totalBytes);
            }

            updateMessage("Restoring project data...");
            int newProjectId = ProjectBackupRepository.importProject(data, destinationFolder.getAbsolutePath());

            updateMessage("Restore complete.");
            updateProgress(1, 1);
            return newProjectId;
        }
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
}