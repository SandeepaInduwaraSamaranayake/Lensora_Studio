package com.lensora.lensorastudio.services;

import java.util.prefs.Preferences;

/**
 * Singleton that persists user-configurable UI preferences using the Java
 * Preferences API (Windows Registry on Windows, ~/.java on Linux/macOS).
 *
 * Add new settings by following the same pattern:
 *   1. Add a KEY_ constant
 *   2. Add a DEFAULT_ constant
 *   3. Add a typed getter and setter
 */
public class AppSettings
{
    // ---------------------------- Preference keys ----------------------------------

    private static final String KEY_THEME                   = "appearance.theme";
    private static final String KEY_FONT_SIZE               = "appearance.font_size";
    private static final String KEY_DEFAULT_PROJECT_ROOT    = "project.default_root";
    private static final String KEY_DEFAULT_LOG_DIR         = "general.default_log_directory";
    private static final String KEY_OPEN_ON_STARTUP         = "general.open_on_startup";

    // ------------------------------- Defaults --------------------------------------

    public static final Theme   DEFAULT_THEME               = Theme.CUPERTINO_DARK;
    public static final double  DEFAULT_FONT_SIZE           = 12.0;
    public static final String  DEFAULT_PROJECT_ROOT        = System.getProperty("user.home") + "/LensoraProjects";
    public static final String  DEFAULT_LOG_DIR             = System.getProperty("user.home") + "/.lensorastudio/logs";
    public static final boolean DEFAULT_OPEN_ON_STARTUP     = false;

    // --------------------------- Supported themes ----------------------------------

    public enum Theme
    {
        CUPERTINO_DARK  ("Cupertino Dark"),
        CUPERTINO_LIGHT ("Cupertino Light"),
        NORD_DARK       ("Nord Dark"),
        PRIMER_DARK     ("Primer Dark"),
        PRIMER_LIGHT    ("Primer Light");

        public final String displayName;
        Theme(String displayName) { this.displayName = displayName; }
    }

    // ----------------------------- Singleton ---------------------------------------

    private static AppSettings instance;

    public static AppSettings getInstance()
    {
        if (instance == null) instance = new AppSettings();
        return instance;
    }

    private final Preferences prefs;

    private AppSettings()
    {
        // Scoped to this application — stored separately from any other Java app
        prefs = Preferences.userNodeForPackage(AppSettings.class);
    }

    // ------------------------------ Theme -------------------------------------------

    public Theme getTheme()
    {
        String saved = prefs.get(KEY_THEME, DEFAULT_THEME.name());
        try
        {
            return Theme.valueOf(saved);
        }
        catch (IllegalArgumentException e)
        {
            return DEFAULT_THEME; // Safely fall back if a saved value is stale
        }
    }

    public void setTheme(Theme theme)
    {
        prefs.put(KEY_THEME, theme.name());
    }

    // ---------------------------- Font size ---------------------------------------

    public double getFontSize()
    {
        return prefs.getDouble(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
    }

    public void setFontSize(double size)
    {
        prefs.putDouble(KEY_FONT_SIZE, size);
    }

    // --------------------- Default Project Root ------------------------------------

    public String getDefaultProjectRoot() 
    {
        return prefs.get(KEY_DEFAULT_PROJECT_ROOT, DEFAULT_PROJECT_ROOT);
    }

    public void setDefaultProjectRoot(String path) 
    {
        prefs.put(KEY_DEFAULT_PROJECT_ROOT, path);
    }

    // -------------------- Default Log Directory -----------------------------------

    public String getDefaultLogDir() 
    {
        return prefs.get(KEY_DEFAULT_LOG_DIR, DEFAULT_LOG_DIR);
    }

    public void setDefaultLogDir(String path) 
    {
        prefs.put(KEY_DEFAULT_LOG_DIR, path);
    }

    // -------------------- Open on Startup  -----------------------------------

    public boolean getOpenOnStartup() 
    {
        return prefs.getBoolean(KEY_OPEN_ON_STARTUP, DEFAULT_OPEN_ON_STARTUP);
    }

    public void setOpenOnStartup(boolean enabled) 
    {
        prefs.putBoolean(KEY_OPEN_ON_STARTUP, enabled);
    }
}