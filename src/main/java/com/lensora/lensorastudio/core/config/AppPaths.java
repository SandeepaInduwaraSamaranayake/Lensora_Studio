package com.lensora.lensorastudio.core.config;

import java.nio.file.Path;

/**
 * Resolves the native OS cache directory path.
 * macOS: ~/Library/Caches/LensoraStudio/thumbnails
 * Windows: %LOCALAPPDATA%\LensoraStudio\Cache\thumbnails
 * Linux: ~/.cache/lensorastudio/thumbnails
 */
public final class AppPaths 
{
    public static Path getCacheDirectory(String appName) 
    {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) 
        {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path base = (localAppData != null && !localAppData.isBlank()) 
                    ? Path.of(localAppData) 
                    : Path.of(userHome, "AppData", "Local");
            return base.resolve(appName).resolve("Cache");
        } 
        else if (os.contains("mac"))
        {
            return Path.of(userHome, "Library", "Caches", appName);
        } 
        else 
        {
            // Linux / Unix (XDG Specification)
            String xdgCache = System.getenv("XDG_CACHE_HOME");
            Path base = (xdgCache != null && !xdgCache.isBlank()) 
                    ? Path.of(xdgCache) 
                    : Path.of(userHome, ".cache");
            return base.resolve(appName.toLowerCase());
        }
    }
}