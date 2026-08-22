package com.lensora.lensorastudio.feature.backup.engine;

import java.io.File;
import java.util.List;

public final class RestoreService
{
    private RestoreService() {}

    public static RestoreJob createRestoreJob(File lsbakFile, File destinationFolder)
    {
        return new RestoreJob(lsbakFile, destinationFolder);
    }

    public static BatchRestoreJob createBatchRestoreJob(List<BatchRestoreJob.BatchItem> items)
    {
        return new BatchRestoreJob(items);
    }
}