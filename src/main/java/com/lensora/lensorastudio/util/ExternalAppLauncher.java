package com.lensora.lensorastudio.util;

import com.lensora.lensorastudio.model.ExternalApp;

import javafx.application.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

        CompletableFuture.runAsync(() -> {
            List<String> command = new ArrayList<>();
            command.add(app.getExecutablePath());
            for (File file : files)
            {
                command.add(file.getAbsolutePath());
            }

            try
            {
                ProcessBuilder pb = new ProcessBuilder(command);
                // Discard stdout and stderr to prevent OS pipe buffers from filling up and hanging on Linux
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                pb.start();
                logger.info("[ExternalAppLauncher] Opened {} file(s) with {}", files.size(), app.getName());
            }
            catch (IOException e)
            {
                logger.error("[ExternalAppLauncher] Failed to launch {} with {} file(s)", app.getName(), files.size(), e);
                Platform.runLater(() -> ErrorHandler.show(null, "Could not launch " + app.getName(), e) );
            }
        });
    }

    /**
     * Shows the operating system's native "Open With" dialog for a single
     * file. No cross-platform Java API exists for this — it's invoked via
     * a platform-specific shell command. Falls back to Desktop.open()
     * (opens with the OS default app) if the platform isn't supported.
     */
    public static void showNativeOpenWithDialog(File file)
    {
        if (file == null) return;

        CompletableFuture.runAsync(() -> {
            String os = System.getProperty("os.name").toLowerCase();

            try
            {
                if (os.contains("win"))
                {
                    // rundll32 shell32.dll,OpenAs_RunDLL is the standard way to
                    // invoke Windows' native "Open with" picker.
                    new ProcessBuilder("rundll32.exe", "shell32.dll,OpenAs_RunDLL", file.getAbsolutePath() ).start();
                }
                else if (os.contains("mac"))
                {
                    // macOS has no direct single-file "Open With" picker command;
                    // 'open -R' reveals it in Finder, where Open With is one
                    // right-click away. There is no cleaner native equivalent.
                    new ProcessBuilder("open", "-R", file.getAbsolutePath()).start();
                }
                else if (os.contains("linux") || os.contains("nix"))
                {
                    logger.info("Native 'Open With' dialog is not universally supported on Linux.");
                    openWithSystemDefaultInternal(file);
                }
                else
                {
                    openWithSystemDefaultInternal(file);
                }
            }
            catch (IOException e)
            {
                logger.warn("[ExternalAppLauncher] Native Open With dialog unavailable, falling back to default app", e);
                openWithSystemDefaultInternal(file);
            }
        });
    }

    /**
     * Opens a file using the operating system's default application.
     */
    public static void openWithSystemDefault(File file)
    {
        if (file == null) return;
        CompletableFuture.runAsync(() -> openWithSystemDefaultInternal(file));
    }

    private static void openWithSystemDefaultInternal(File file)
    {
        String os = System.getProperty("os.name").toLowerCase();
        try
        {
            if (os.contains("linux") || os.contains("nix"))
            {
                // xdg-open via ProcessBuilder is cleaner on Linux than Desktop.open()
                ProcessBuilder pb = new ProcessBuilder("xdg-open", file.getAbsolutePath());
                pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                pb.start();
            }
            else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
            {
                Desktop.getDesktop().open(file);
            }
        }
        catch (IOException e)
        {
            logger.error("[ExternalAppLauncher] Failed to open system default for {}", file.getName(), e);
            Platform.runLater(() -> ErrorHandler.show(null, "Could not open file", e));
        }
    }
}