package com.lensora.lensorastudio.backup.engine;

import com.lensora.lensorastudio.backup.model.BackupManifest;
import com.lensora.lensorastudio.backup.util.HashService;
import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.repository.ProjectBackupRepository;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javafx.concurrent.Task;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a self-contained .lsbak archive for one project: DB rows
 * (exported via JDBC, never a raw file copy — avoids WAL-mode corruption
 * risk entirely) + manifest + every file under the project's folder.
 */
public class BackupJob extends Task<File>
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String APP_VERSION = "1.0-SNAPSHOT";

    private final Project project;
    private final File destinationFile;

    public BackupJob(Project project, File destinationFile)
    {
        this.project = project;
        this.destinationFile = destinationFile;
    }

    @Override
    protected File call() throws Exception
    {
        updateMessage("Scanning project files...");
        File projectRoot = new File(project.getProjectPath());
        List<File> allFiles = scanFiles(projectRoot);

        long totalSize = allFiles.stream().mapToLong(File::length).sum();

        updateMessage("Collecting project data...");
        var backupData = ProjectBackupRepository.exportProject(project.getProjectId());

        BackupManifest manifest = new BackupManifest();
        manifest.created = LocalDateTime.now().toString();
        manifest.appVersion = APP_VERSION;
        manifest.projectNumber = project.getProjectNumber();
        manifest.clientName = project.getClientName();
        manifest.totalFiles = allFiles.size();
        manifest.totalSize = totalSize;
        manifest.files = new ArrayList<>();

        File tempFile = File.createTempFile("lensora-backup-", ".tmp");
        long copiedBytes = 0;

        try (FileOutputStream fos = new FileOutputStream(tempFile);
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(fos)))
        {
            zos.setLevel(6);

            // project-data.json
            updateMessage("Writing project data...");
            writeJsonEntry(zos, "project-data.json", backupData);

            // files/
            updateMessage("Compressing files...");
            for (int i = 0; i < allFiles.size(); i++)
            {
                if (isCancelled()) throw new InterruptedException("Backup cancelled");

                File file = allFiles.get(i);
                String relativePath = projectRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/');

                String hash = HashService.sha256(file.toPath());
                manifest.files.add(new BackupManifest.ManifestFileEntry(relativePath, file.length(), hash));

                zos.putNextEntry(new ZipEntry("files/" + relativePath));
                Files.copy(file.toPath(), zos);
                zos.closeEntry();

                copiedBytes += file.length();
                updateProgress(copiedBytes, totalSize);
                updateMessage(String.format("Compressing files... (%d / %d)", i + 1, allFiles.size()));
            }

            // manifest.json (written last so totalFiles/totalSize reflect the real write)
            writeJsonEntry(zos, "manifest.json", manifest);
        }

        updateMessage("Finalizing archive...");
        Files.move(tempFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        updateMessage("Backup complete.");
        updateProgress(1, 1);
        return destinationFile;
    }

    private List<File> scanFiles(File root) throws IOException
    {
        List<File> files = new ArrayList<>();
        if (!root.exists()) return files;
        try (var stream = Files.walk(root.toPath()))
        {
            stream.filter(Files::isRegularFile).forEach(p -> files.add(p.toFile()));
        }
        return files;
    }

    private void writeJsonEntry(ZipOutputStream zos, String entryName, Object data) throws IOException
    {
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(GSON.toJson(data).getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}