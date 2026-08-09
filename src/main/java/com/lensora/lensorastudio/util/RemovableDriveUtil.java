package com.lensora.lensorastudio.util;

import java.io.File;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enumerates removable/external drives attached to the system, excluding
 * the OS/system drive, and provides subfolder scanning for cascading menus.
 */
public final class RemovableDriveUtil
{
    private static final Logger logger = LoggerFactory.getLogger(RemovableDriveUtil.class);

    private RemovableDriveUtil() {}

    public record DriveInfo(String label, Path rootPath, long usableBytes, long totalBytes) {}

    public static List<DriveInfo> listRemovableDrives()
    {
        List<DriveInfo> drives = new ArrayList<>();
        String systemDriveRoot = resolveSystemDriveRoot();

        for (File root : File.listRoots())
        {
            try
            {
                Path rootPath = root.toPath();

                // Skip the system/boot drive - "Send To" should only list
                // genuinely external/secondary locations.
                if (rootPath.toString().equalsIgnoreCase(systemDriveRoot))
                {
                    continue;
                }
                // Skip drives that aren't actually accessible/mounted right
                // now (e.g. a stale CD-ROM drive letter with no disc).
                if (!root.exists() || root.getTotalSpace() <= 0)
                {
                    continue;
                }

                FileStore store = null;
                try { store = Files.getFileStore(rootPath); } catch (Exception ignored) {}

                String label = (store != null && store.name() != null && !store.name().isBlank())
                        ? store.name() + " (" + root.getPath() + ")"
                        : root.getPath();

                drives.add(new DriveInfo(label, rootPath, root.getUsableSpace(), root.getTotalSpace()));
            }
            catch (Exception e)
            {
                logger.debug("[RemovableDriveUtil] Skipping unreadable root: {}", root, e);
            }
        }

        return drives;
    }

    /**
     * Lists accessible, non-hidden subdirectories for a given folder sorted alphabetically.
     */
    public static List<File> listSubdirectories(File parentDir)
    {
        if (parentDir == null || !parentDir.exists() || !parentDir.isDirectory())
        {
            return List.of();
        }

        File[] dirs = parentDir.listFiles(f -> f.isDirectory() && !f.isHidden());
        if (dirs == null) return List.of();

        List<File> dirList = new ArrayList<>(List.of(dirs));
        dirList.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return dirList;
    }

    /** Best-effort resolution of the system drive root (e.g. "C:\" on Windows, "/" on Unix-like systems). */
    private static String resolveSystemDriveRoot()
    {
        String userHome = System.getProperty("user.home");
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win"))
        {
            for (File root : File.listRoots())
            {
                if (userHome.toUpperCase().startsWith(root.getPath().toUpperCase()))
                {
                    return root.getPath();
                }
            }
            return "C:\\";
        }
        else
        {
            // macOS/Linux: single root filesystem
            return "/";
        }
    }
}