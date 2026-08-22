package com.lensora.lensorastudio.feature.backup.model;

import java.util.List;

public class BackupManifest
{
    public int      version = 1;
    public String   created;
    public String   appVersion;
    public String   projectNumber;
    public String   clientName;
    public int      totalFiles;
    public long     totalSize;
    public List<ManifestFileEntry> files;

    public static class ManifestFileEntry
    {
        public String relativePath;
        public long size;
        public String sha256;

        public ManifestFileEntry() {}

        public ManifestFileEntry(String relativePath, long size, String sha256)
        {
            this.relativePath = relativePath;
            this.size = size;
            this.sha256 = sha256;
        }
    }
}