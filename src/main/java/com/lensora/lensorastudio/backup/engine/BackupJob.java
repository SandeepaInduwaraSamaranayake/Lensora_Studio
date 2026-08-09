package com.lensora.lensorastudio.backup.engine;

import com.lensora.lensorastudio.backup.model.BackupManifest;
import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.repository.ProjectBackupRepository;
import com.lensora.lensorastudio.util.FileSizeFormatter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javafx.concurrent.Task;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class BackupJob extends Task<File>
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String APP_VERSION = "1.0-SNAPSHOT";
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Project project;
    private final File destinationFile;

    public BackupJob(Project project, File destinationFile)
    {
        this.project = project;
        this.destinationFile = destinationFile;
    }

    public Project getProject()
    {
        return project;
    }

    @Override
    protected File call() throws Exception
    {
        updateMessage("Scanning project directory...");
        File projectRoot = new File(project.getProjectPath());

        List<Path> allPaths = scanAllPaths(projectRoot.toPath());
        long totalSize = allPaths.stream()
                .filter(Files::isRegularFile)
                .mapToLong(p -> p.toFile().length())
                .sum();

        updateMessage("Collecting project database records...");
        var backupData = ProjectBackupRepository.exportProject(project.getProjectId());

        BackupManifest manifest = new BackupManifest();
        manifest.created = LocalDateTime.now().toString();
        manifest.appVersion = APP_VERSION;
        manifest.projectNumber = project.getProjectNumber();
        manifest.clientName = project.getClientName();
        manifest.totalSize = totalSize;
        manifest.files = new ArrayList<>();

        File tempFile = File.createTempFile("lensora-backup-", ".tmp");
        long copiedBytes = 0;
        long startTime = System.currentTimeMillis();
        long lastUpdateTime = startTime;

        try
        {
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                BufferedOutputStream bos = new BufferedOutputStream(fos, BUFFER_SIZE);
                ZipOutputStream zos = new ZipOutputStream(bos))
            {
                zos.setLevel(6);

                updateMessage("Writing database metadata...");
                writeJsonEntry(zos, "project-data.json", backupData);

                int processedFiles = 0;
                byte[] buffer = new byte[BUFFER_SIZE];

                for (Path path : allPaths)
                {
                    if (isCancelled()) throw new InterruptedException("Backup cancelled by user");

                    String relativePath = projectRoot.toPath().relativize(path).toString().replace('\\', '/');

                    if (Files.isDirectory(path))
                    {
                        // Preserve empty directory structure - without this,
                        // photographers' intentional folder hierarchies
                        // (RAW/, Exports/, Selected/) collapse on restore.
                        ZipEntry dirEntry = new ZipEntry("files/" + relativePath + "/");
                        dirEntry.setTime(Files.getLastModifiedTime(path).toMillis());
                        zos.putNextEntry(dirEntry);
                        zos.closeEntry();
                        continue;
                    }

                    long fileSize = Files.size(path);
                    ZipEntry fileEntry = new ZipEntry("files/" + relativePath);
                    fileEntry.setTime(Files.getLastModifiedTime(path).toMillis());
                    zos.putNextEntry(fileEntry);

                    // Single-pass: hash and compress in the same read loop,
                    // instead of reading the file twice (once for
                    // HashService.sha256, once for Files.copy). Halves disk
                    // I/O for large projects.
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try (InputStream fis = Files.newInputStream(path);
                        BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE);
                        DigestInputStream dis = new DigestInputStream(bis, digest))
                    {
                        int bytesRead;
                        while ((bytesRead = dis.read(buffer)) != -1)
                        {
                            if (isCancelled()) throw new InterruptedException("Backup cancelled by user");
                            zos.write(buffer, 0, bytesRead);
                            copiedBytes += bytesRead;

                            long now = System.currentTimeMillis();
                            if (now - lastUpdateTime > 200)
                            {
                                double elapsedSec = Math.max(0.001, (now - startTime) / 1000.0);
                                double speedMBs = (copiedBytes / (1024.0 * 1024.0)) / elapsedSec;
                                long remainingBytes = Math.max(0, totalSize - copiedBytes);
                                long etaSeconds = speedMBs > 0
                                        ? (long) (remainingBytes / (speedMBs * 1024 * 1024))
                                        : 0;

                                updateProgress(copiedBytes, Math.max(1, totalSize));
                                updateMessage(String.format(
                                        "Compressing... %.1f MB/s · %d/%d files · ETA %s",
                                        speedMBs, processedFiles + 1, allPaths.size(), FileSizeFormatter.formatFileSize(etaSeconds)));
                                lastUpdateTime = now;
                            }
                        }
                    }
                    zos.closeEntry();

                    String hash = HexFormat.of().formatHex(digest.digest());
                    manifest.files.add(new BackupManifest.ManifestFileEntry(relativePath, fileSize, hash));
                    processedFiles++;
                }

                manifest.totalFiles = manifest.files.size();
                writeJsonEntry(zos, "manifest.json", manifest);
            }

            updateMessage("Finalizing archive...");
            if (destinationFile.getParentFile() != null)
            {
                Files.createDirectories(destinationFile.getParentFile().toPath());
            }
            Files.move(tempFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        finally
        {
            if (tempFile.exists()) tempFile.delete();
        }

        updateMessage("Backup complete.");
        updateProgress(1, 1);
        return destinationFile;
    }

    private List<Path> scanAllPaths(Path root) throws IOException
    {
        if (!Files.exists(root)) return List.of();
        try (var stream = Files.walk(root))
        {
            return stream.filter(p -> !p.equals(root)).toList();
        }
    }

    private void writeJsonEntry(ZipOutputStream zos, String entryName, Object data) throws IOException
    {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(GSON.toJson(data).getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    public File runSynchronously() throws Exception
    {
        return call();
    }
}