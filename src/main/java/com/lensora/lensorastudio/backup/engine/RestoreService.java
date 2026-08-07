package com.lensora.lensorastudio.backup.engine;

import java.io.File;

public final class RestoreService
{
    private RestoreService() {}

    public static RestoreJob createRestoreJob(File lsbakFile, File destinationFolder)
    {
        return new RestoreJob(lsbakFile, destinationFolder);
    }
}