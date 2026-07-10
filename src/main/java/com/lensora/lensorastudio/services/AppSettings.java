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

    private static final String     KEY_THEME                                       = "appearance.theme";
    private static final String     KEY_FONT_SIZE                                   = "appearance.font_size";
    private static final String     KEY_DEFAULT_PROJECT_ROOT                        = "project.default_root";
    private static final String     KEY_DEFAULT_LOG_DIR                             = "general.default_log_directory";
    private static final String     KEY_OPEN_ON_STARTUP                             = "general.open_on_startup";
    private static final String     KEY_LAST_PROJECT_ID                             = "general.last_opened_id";
    private static final String     KEY_CLEAR_SEARCH_ON_PROJECT_SELECT              = "general.clear_search_on_select";
    private static final String     KEY_RESET_STATUS_ON_CLEAR_SEARCH                = "general.reset_status_on_clear_search";
    private static final String     KEY_OPEN_LAST_PROJECT                           = "general.open_last_project";
    private static final String     KEY_SEARCH_DEBOUNCE_MS                          = "advanced.search_debounce_ms";

    // ------------------------------- Defaults --------------------------------------

    public static final Theme       DEFAULT_THEME                                   = Theme.CUPERTINO_DARK;
    public static final double      DEFAULT_FONT_SIZE                               = 12.0;
    public static final String      DEFAULT_PROJECT_ROOT                            = System.getProperty("user.home") + "/LensoraProjects";
    public static final String      DEFAULT_LOG_DIR                                 = System.getProperty("user.home") + "/.lensorastudio/logs";
    public static final boolean     DEFAULT_OPEN_ON_STARTUP                         = false;
    public static final int         DEFAULT_LAST_PROJECT_ID                         = -1;
    public static final boolean     DEFAULT_CLEAR_SEARCH_ON_PROJECT_SELECT          = false;
    public static final boolean     DEFAULT_RESET_STATUS_ON_CLEAR_SEARCH            = false;
    public static final boolean     DEFAULT_OPEN_LAST_PROJECT                       = true;
    public static final int         DEFAULT_SEARCH_DEBOUNCE_MS                      = 50;

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

    // -------------------- Last Project ID  ----------------------------------

    public int getLastProjectId() 
    {
        return prefs.getInt(KEY_LAST_PROJECT_ID, DEFAULT_LAST_PROJECT_ID);
    }

    public void setLastProjectId(int id) 
    {
        prefs.putInt(KEY_LAST_PROJECT_ID, id);
    }

    // ----------------- Clear Search on Project Select  ---------------------

    public boolean getClearSearchOnProjectSelect() 
    {
        return prefs.getBoolean(KEY_CLEAR_SEARCH_ON_PROJECT_SELECT, DEFAULT_CLEAR_SEARCH_ON_PROJECT_SELECT);
    }

    public void setClearSearchOnProjectSelect(boolean value) 
    {
        prefs.putBoolean(KEY_CLEAR_SEARCH_ON_PROJECT_SELECT, value);
    }

    // ----------------------- Open Last Project  ---------------------------
    public boolean getOpenLastProject() 
    {
        return prefs.getBoolean(KEY_OPEN_LAST_PROJECT, DEFAULT_OPEN_LAST_PROJECT);
    }

    public void setOpenLastProject(boolean value) 
    {
        prefs.putBoolean(KEY_OPEN_LAST_PROJECT, value);
    }

    // ------------- Reset status filter on search clear  ------------------
    public boolean getResetStatusOnClearSearch() 
    {
        return prefs.getBoolean(KEY_RESET_STATUS_ON_CLEAR_SEARCH, DEFAULT_RESET_STATUS_ON_CLEAR_SEARCH);
    }

    public void setResetStatusOnClearSearch(boolean value) 
    {
        prefs.putBoolean(KEY_RESET_STATUS_ON_CLEAR_SEARCH, value);
    }

    // ----------------------- Search Debounce  ----------------------------
    public int getSearchDebounceMs() 
    {
        return prefs.getInt(KEY_SEARCH_DEBOUNCE_MS, DEFAULT_SEARCH_DEBOUNCE_MS);
    }

    public void setSearchDebounceMs(int ms) 
    {
        prefs.putInt(KEY_SEARCH_DEBOUNCE_MS, ms);
    }
}