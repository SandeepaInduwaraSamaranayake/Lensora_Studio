package com.lensora.lensorastudio.backup.engine;

import com.lensora.lensorastudio.model.Project;

import java.io.File;
import java.util.List;

/** High-level entry point for creating backups. */
public final class BackupService
{
    private BackupService() {}

    public static BackupJob createBackupJob(Project project, File destinationFile)
    {
        return new BackupJob(project, destinationFile);
    }

    public static BatchBackupJob createBatchBackupJob(List<BatchBackupJob.BatchItem> items)
    {
        return new BatchBackupJob(items);
    }

    /** Suggests a default filename: "WED-0003 - John & Sarah - 2026-08-07.lsbak" */
    public static String suggestFileName(Project project)
    {
        String date = java.time.LocalDate.now().toString();
        String safeName = (project.getProjectNumber() + " - " + project.getClientName())
                .replaceAll("[\\\\/:*?\"<>|]", "");
        return safeName + " - " + date + ".lsbak";
    }
}