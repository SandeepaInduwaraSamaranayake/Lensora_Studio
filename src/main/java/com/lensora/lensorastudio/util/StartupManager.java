package com.lensora.lensorastudio.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manages adding/removing the application to/from system startup.
 * Supports Windows (Registry), macOS (LaunchAgents), and Linux (XDG autostart).
 */
public final class StartupManager {

    private static final Logger log = LoggerFactory.getLogger(StartupManager.class);
    private static final String REG_KEY = "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "LensoraStudio";
    private static final String LINUX_AUTOSTART_DIR = ".config/autostart";
    private static final String LINUX_DESKTOP_NAME = "lensorastudio.desktop";

    private StartupManager() {}

    // ---------------------------- Public API -----------------------------------

    public static boolean isStartupEnabled() 
    {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) 
        {
            return isWindowsStartupEnabled();
        } 
        else if (os.contains("mac"))
        {
            return isMacStartupEnabled();
        } 
        else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) 
        {
            return isLinuxStartupEnabled();
        } 
        else 
        {
            return false;
        }
    }

    public static boolean addToStartup() 
    {
        String os = System.getProperty("os.name").toLowerCase();
        try 
        {
            if (os.contains("win")) 
            {
                return addToWindowsStartup();
            } 
            else if (os.contains("mac")) 
            {
                return addToMacStartup();
            } 
            else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) 
            {
                return addToLinuxStartup();
            } 
            else 
            {
                log.warn("Auto-start not supported on this OS: {}", os);
                return false;
            }
        } 
        catch (Exception e) 
        {
            log.error("Failed to add to startup", e);
            return false;
        }
    }

    public static boolean removeFromStartup() 
    {
        String os = System.getProperty("os.name").toLowerCase();
        try 
        {
            if (os.contains("win")) 
            {
                return removeFromWindowsStartup();
            } 
            else if (os.contains("mac")) 
            {
                return removeFromMacStartup();
            } 
            else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) 
            {
                return removeFromLinuxStartup();
            } 
            else 
            {
                log.warn("Auto-start not supported on this OS: {}", os);
                return false;
            }
        } 
        catch (Exception e) 
        {
            log.error("Failed to remove from startup", e);
            return false;
        }
    }

    // -------------------------- Windows -----------------------------------

    private static boolean isWindowsStartupEnabled() 
    {
        try 
        {
            ProcessBuilder pb = new ProcessBuilder(
                    "reg", "query", REG_KEY, "/v", VALUE_NAME
            );
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } 
        catch (Exception e) 
        {
            log.warn("Failed to check Windows startup status", e);
            return false;
        }
    }

    private static boolean addToWindowsStartup() throws IOException, InterruptedException 
    {
        String exePath = getCurrentExecutablePath();
        if (exePath == null) return false;
        String quotedPath = "\"" + exePath + "\"";

        ProcessBuilder pb = new ProcessBuilder(
                "reg", "add", REG_KEY,
                "/v", VALUE_NAME,
                "/t", "REG_SZ",
                "/d", quotedPath,
                "/f"
        );
        Process process = pb.start();
        int exitCode = process.waitFor();
        String error = new String(process.getErrorStream().readAllBytes());
        if (exitCode == 0) 
        {
            log.info("Added Lensora Studio to Windows startup.");
            return true;
        } 
        else 
        {
            log.error("Reg add failed with exit {}: {}", exitCode, error);
            return false;
        }
    }

    private static boolean removeFromWindowsStartup() throws IOException, InterruptedException 
    {
        ProcessBuilder pb = new ProcessBuilder(
                "reg", "delete", REG_KEY, 
                "/v", VALUE_NAME, "/f"
        );
        Process process = pb.start();
        int exitCode = process.waitFor();
        String error = new String(process.getErrorStream().readAllBytes());
        if (exitCode == 0) 
        {
            log.info("Removed Lensora Studio from Windows startup.");
            return true;
        } 
        else if (exitCode == 1) 
        {
            log.warn("Startup entry not found, removal skipped.");
            return true;
        } 
        else 
        {
            log.error("Reg delete failed with exit {}: {}", exitCode, error);
            return false;
        }
    }

    //------------------------------- macOS ---------------------------------------

    private static boolean isMacStartupEnabled() 
    {
        String home = System.getProperty("user.home");
        Path plist = Paths.get(home, "Library", "LaunchAgents", "com.lensorastudio.launcher.plist");
        return Files.exists(plist);
    }

    private static boolean addToMacStartup() throws IOException, InterruptedException 
    {
        String home = System.getProperty("user.home");
        Path launchAgents = Paths.get(home, "Library", "LaunchAgents");
        Files.createDirectories(launchAgents);

        String exePath = getCurrentExecutablePath();
        if (exePath == null) return false;

        Path plistPath = launchAgents.resolve("com.lensorastudio.launcher.plist");
        String content = String.format("""
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>com.lensorastudio.launcher</string>
                <key>ProgramArguments</key>
                <array>
                    <string>%s</string>
                </array>
                <key>RunAtLoad</key>
                <true/>
            </dict>
            </plist>
            """, exePath);
        Files.writeString(plistPath, content);

        // Use 'bootstrap' (modern)
        String bootstrapCmd = "launchctl bootstrap gui/$(id -u) " + plistPath.toString();
        Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", bootstrapCmd});
        int exitCode = p.waitFor();
        String error = new String(p.getErrorStream().readAllBytes());
        if (exitCode == 0) 
        {
            log.info("Added Lensora Studio to macOS startup.");
            return true;
        } 
        else 
        {
            log.error("launchctl bootstrap failed with exit {}: {}", exitCode, error);
            return false;
        }
    }

    private static boolean removeFromMacStartup() throws IOException, InterruptedException 
    {
        String home = System.getProperty("user.home");
        Path plistPath = Paths.get(home, "Library", "LaunchAgents", "com.lensorastudio.launcher.plist");
        if (!Files.exists(plistPath)) 
        {
            log.warn("macOS launch agent plist not found - nothing to remove.");
            return true;
        }
        String unloadCmd = "launchctl bootout gui/$(id -u) " + plistPath.toString();
        Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", unloadCmd});
        p.waitFor();
        Files.deleteIfExists(plistPath);
        log.info("Removed Lensora Studio from macOS startup.");
        return true;
    }

    // -------------------------- Linux (XDG Autostart) ----------------------------------

    private static boolean isLinuxStartupEnabled() 
    {
        String home = System.getProperty("user.home");
        Path desktopFile = Paths.get(home, LINUX_AUTOSTART_DIR, LINUX_DESKTOP_NAME);
        return Files.exists(desktopFile);
    }

    private static boolean addToLinuxStartup() throws IOException {
        String home = System.getProperty("user.home");
        Path autostartDir = Paths.get(home, LINUX_AUTOSTART_DIR);
        Files.createDirectories(autostartDir);

        String exePath = getCurrentExecutablePath();
        if (exePath == null) return false;

        // .desktop file content
        String desktopContent = String.format("""
            [Desktop Entry]
            Type=Application
            Name=Lensora Studio
            Exec=%s
            Hidden=false
            X-GNOME-Autostart-enabled=true
            """, exePath);

        Path desktopFile = autostartDir.resolve(LINUX_DESKTOP_NAME);
        Files.writeString(desktopFile, desktopContent);
        // Make executable
        if (desktopFile.toFile().setExecutable(true)) 
        {
            log.info("Made desktop file executable.");
        }
        log.info("Added Lensora Studio to Linux startup.");
        return true;
    }

    private static boolean removeFromLinuxStartup() throws IOException 
    {
        String home = System.getProperty("user.home");
        Path desktopFile = Paths.get(home, LINUX_AUTOSTART_DIR, LINUX_DESKTOP_NAME);
        if (Files.exists(desktopFile)) 
        {
            Files.delete(desktopFile);
            log.info("Removed Lensora Studio from Linux startup.");
            return true;
        } 
        else 
        {
            log.warn("Linux startup desktop file not found – nothing to remove.");
            return true;
        }
    }

    // ------------------------------ Helpers ---------------------------------------

    private static String getCurrentExecutablePath() 
    {
        // Try ProcessHandle (works for native launchers, AppImage, etc.)
        String command = ProcessHandle.current()
                .info()
                .command()
                .orElse(null);
        if (command != null && !command.toLowerCase().contains("java")) 
        {
            return command;
        }

        // Fallback for JAR or IDE
        String jarPath = StartupManager.class.getProtectionDomain()
                .getCodeSource().getLocation().getPath();
        if (jarPath != null && jarPath.endsWith(".jar")) 
        {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            return String.format("\"%s\" -jar \"%s\"", javaBin, jarPath);
        }

        log.warn("Could not determine executable path; auto-start may not work.");
        return null;
    }

    /**
     * Returns true if the application is running from an IDE (not a packaged JAR or native launcher).
     */
    public static boolean isDevelopmentMode() 
    {
        // Check if the command is "java" (or javaw) and the code source is not a .jar
        String command = ProcessHandle.current()
                .info()
                .command()
                .orElse("");
        if (command.toLowerCase().contains("java")) 
        {
            String jarPath = StartupManager.class.getProtectionDomain()
                    .getCodeSource().getLocation().getPath();
            return jarPath == null || !jarPath.endsWith(".jar");
        }
        return false;
    }
}