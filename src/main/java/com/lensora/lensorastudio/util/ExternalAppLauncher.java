package com.lensora.lensorastudio.util;

import com.lensora.lensorastudio.model.ExternalApp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Launches files with a specific external application, or shows the OS's
 * native "Open With" chooser where supported.
 */
public final class ExternalAppLauncher
{
    private static final Logger logger = LoggerFactory.getLogger(ExternalAppLauncher.class);

    private ExternalAppLauncher() {}

    /**
     * Opens each file with the given app in a single process invocation
     * where the OS/app supports multiple file arguments (most photo/video
     * editors do — Photoshop, Lightroom, etc. accept a file list and load
     * them all into one session).
     */
    public static void openWith(ExternalApp app, List<File> files)
    {
        if (app == null || files == null || files.isEmpty()) return;

        List<String> command = new ArrayList<>();
        command.add(app.getExecutablePath());
        for (File file : files)
        {
            command.add(file.getAbsolutePath());
        }

        try
        {
            new ProcessBuilder(command).start();
            logger.info("[ExternalAppLauncher] Opened {} file(s) with {}", files.size(), app.getName());
        }
        catch (IOException e)
        {
            logger.error("[ExternalAppLauncher] Failed to launch {} with {} file(s)", app.getName(), files.size(), e);
            ErrorHandler.show(null, "Could not launch " + app.getName(), e);
        }
    }

    /**
     * Shows the operating system's native "Open With" dialog for a single
     * file. No cross-platform Java API exists for this — it's invoked via
     * a platform-specific shell command. Falls back to Desktop.open()
     * (opens with the OS default app) if the platform isn't supported.
     */
    public static void showNativeOpenWithDialog(File file)
    {
        String os = System.getProperty("os.name").toLowerCase();

        try
        {
            if (os.contains("win"))
            {
                // rundll32 shell32.dll,OpenAs_RunDLL is the standard way to
                // invoke Windows' native "Open with" picker.
                new ProcessBuilder("rundll32.exe", "shell32.dll,OpenAs_RunDLL", file.getAbsolutePath()
                    ).start();
            }
            else if (os.contains("mac"))
            {
                // macOS has no direct single-file "Open With" picker command;
                // 'open -R' reveals it in Finder, where Open With is one
                // right-click away. There is no cleaner native equivalent.
                new ProcessBuilder("open", "-R", file.getAbsolutePath()).start();
            }
            else
            {
                // Linux desktop environments vary too much for one command —
                // fall back to opening with the system default application.
                openWithSystemDefault(file);
            }
        }
        catch (IOException e)
        {
            logger.warn("[ExternalAppLauncher] Native Open With dialog unavailable, falling back to default app", e);
            openWithSystemDefault(file);
        }
    }

    public static void openWithSystemDefault(File file)
    {
        try
        {
            if (Desktop.isDesktopSupported())
            {
                Desktop.getDesktop().open(file);
            }
        }
        catch (IOException e)
        {
            ErrorHandler.show(null, "Could not open file", e);
        }
    }
}