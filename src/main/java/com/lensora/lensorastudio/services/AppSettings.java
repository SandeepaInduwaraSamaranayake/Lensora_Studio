package com.lensora.lensorastudio.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;

import com.google.gson.Gson;
import  com.lensora.lensorastudio.model.ExternalApp;

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
    private static final String     KEY_DOCK_LAYOUT                                 = "ui.dock_layout";
    private static final String     KEY_FFPROBE_PATH                                = "advanced.ffprobe_path";
    private static final String     KEY_SHOW_METADATA_PREVIEW                       = "metadata.show_preview";
    private static final String     KEY_METADATA_PREVIEW_SIZE                       = "metadata.preview_quality";
    private static final String     KEY_LAYOUT_LOCKED                               = "layout.locked";
    private static final String     KEY_EXTERNAL_APPS                               = "file.external_apps";
    private static final String     KEY_FOLDER_SAVE_DELAY_MS                        = "folder.save_delay_ms";
    private static final String     KEY_IMAGE_CACHE_SIZE                            = "image.cache.size";
    private static final String     KEY_ZOOM_SENSITIVITY                            = "image.viewer.zoom_sensitivity";
    private static final String     KEY_BACKUP_HISTORY                              = "backup.history.paths";
    private static final String     KEY_IMAGE_VIEWER_QUALITY                        = "image.viewer.quality";

    // ------------------------------- Defaults --------------------------------------

    public static final Theme       DEFAULT_THEME                                   = Theme.MODENA;
    public static final double      DEFAULT_FONT_SIZE                               = 11.0;
    public static final String      DEFAULT_PROJECT_ROOT                            = System.getProperty("user.home") + "/LensoraProjects";
    public static final String      DEFAULT_LOG_DIR                                 = System.getProperty("user.home") + "/.lensorastudio/logs";
    public static final boolean     DEFAULT_OPEN_ON_STARTUP                         = false;
    public static final int         DEFAULT_LAST_PROJECT_ID                         = -1;
    public static final boolean     DEFAULT_CLEAR_SEARCH_ON_PROJECT_SELECT          = false;
    public static final boolean     DEFAULT_RESET_STATUS_ON_CLEAR_SEARCH            = false;
    public static final boolean     DEFAULT_OPEN_LAST_PROJECT                       = true;
    public static final int         DEFAULT_SEARCH_DEBOUNCE_MS                      = 50;
    public static final String      DEFAULT_FFPROBE_PATH                            = ""; // empty = rely on system PATH
    public static final boolean     DEFAULT_SHOW_METADATA_PREVIEW                   = true;
    public static final int         DEFAULT_METADATA_PREVIEW_SIZE                   = 600;
    public static final boolean     DEFAULT_LAYOUT_LOCKED                           = false;
    public static final int         DEFAULT_FOLDER_SAVE_DELAY_MS                    = 400;
    public static final int         DEFAULT_IMAGE_CACHE_SIZE                        = 200;
    public static final double      DEFAULT_ZOOM_SENSITIVITY                        = 1.40;
    public static final String      DEFAULT_IMAGE_VIEWER_QUALITY                    = ImageQuality.HIGH.name();

    // --------------------------- Supported themes ----------------------------------

    public enum Theme
    {
        CUPERTINO_DARK  ("Cupertino Dark", true),
        CUPERTINO_LIGHT ("Cupertino Light", true),
        NORD_DARK       ("Nord Dark", true),
        PRIMER_DARK     ("Primer Dark", true),
        PRIMER_LIGHT    ("Primer Light", true),
        MODENA          ("JavaFX Modena (Native SnapFX)", false),
        CASPIAN         ("JavaFX Caspian (Native SnapFX)", false);

        public final String displayName;
        public final boolean atlantaFxBased;
        Theme(String displayName, boolean atlantaFxBased)
        {
            this.displayName = displayName;
            this.atlantaFxBased = atlantaFxBased;
        }
    }

    // ----------------------- Supported image qualities ------------------------------

    public enum ImageQuality
    {
        LOW             ("Low (800px)", 800),
        MEDIUM          ("Medium (1600px)", 1600),
        HIGH            ("High (2600px)", 2600),
        ORIGINAL        ("Original (Full Resolution)", 0); // 0 = load at native size, no downscale

        public final String displayName;
        public final int maxDimension;

        ImageQuality(String displayName, int maxDimension)
        {
            this.displayName = displayName;
            this.maxDimension = maxDimension;
        }

        @Override
        public String toString() { return displayName; }
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
        // Scoped to this application - stored separately from any other Java app
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

    // ------------------------- Dock Layout ------------------------------
    public String getDockLayout()
    {
        return prefs.get(KEY_DOCK_LAYOUT, "");
    }

    public void setDockLayout(String layout)
    {
        prefs.put(KEY_DOCK_LAYOUT, layout);
    }

    // ------------------------- FFProbe Path ------------------------------
    public String getFfprobePath()
    {
        return prefs.get(KEY_FFPROBE_PATH, DEFAULT_FFPROBE_PATH);
    }

    public void setFfprobePath(String path)
    {
        prefs.put(KEY_FFPROBE_PATH, path);
    }

    // 
    public boolean getShowMetadataImagePreview() 
    {
        return prefs.getBoolean(KEY_SHOW_METADATA_PREVIEW, DEFAULT_SHOW_METADATA_PREVIEW);
    }

    public void setShowMetadataImagePreview(boolean show) 
    {
        prefs.putBoolean(KEY_SHOW_METADATA_PREVIEW, show);
    }

    public boolean getLayoutLocked()
    {
        return prefs.getBoolean(KEY_LAYOUT_LOCKED, DEFAULT_LAYOUT_LOCKED);
    }

    public void setLayoutLocked(boolean locked)
    {
        prefs.putBoolean(KEY_LAYOUT_LOCKED, locked);
    }

    public List<ExternalApp> getExternalApps()
    {
        String json = prefs.get(KEY_EXTERNAL_APPS, "[]");
        try
        {
            ExternalApp[] apps = new Gson().fromJson(json, ExternalApp[].class);
            return apps != null ? new ArrayList<>(Arrays.asList(apps)) : new ArrayList<>();
        }
        catch (Exception e)
        {
            return new ArrayList<>();
        }
    }

    public void setExternalApps(List<ExternalApp> apps)
    {
        prefs.put(KEY_EXTERNAL_APPS, new Gson().toJson(apps));
    }

    // --------------------- Folder Save Debounce  -------------------------
    public int getFolderSaveDelayMs() 
    {
        return prefs.getInt(KEY_FOLDER_SAVE_DELAY_MS, DEFAULT_FOLDER_SAVE_DELAY_MS);
    }

    public void setFolderSaveDelayMs(int ms) 
    {
        prefs.putInt(KEY_FOLDER_SAVE_DELAY_MS, ms);
    }

    // --------------- Metadata image preview quality  --------------------
    public int getMetadataPreviewSize() 
    {
        return prefs.getInt(KEY_METADATA_PREVIEW_SIZE, DEFAULT_METADATA_PREVIEW_SIZE);
    }

    public void setMetadataPreviewSize(int size) 
    {
        prefs.putInt(KEY_METADATA_PREVIEW_SIZE, size);
    }

    // ------------------------ Cache size  --------------------------------
    public int getImageCacheSize()
    {
        return prefs.getInt(KEY_IMAGE_CACHE_SIZE, DEFAULT_IMAGE_CACHE_SIZE);
    }

    public void setImageCacheSize(int size) 
    {
        prefs.putInt(KEY_IMAGE_CACHE_SIZE, size);
    }

    // --------------------- Zoom Sensitivity  ----------------------------
    public double getZoomSensitivity()
    {
        return prefs.getDouble(KEY_ZOOM_SENSITIVITY, DEFAULT_ZOOM_SENSITIVITY);
    }

    public void setZoomSensitivity(double factor)
    {
        prefs.putDouble(KEY_ZOOM_SENSITIVITY, factor);
    }

    // --------------------- Backup History  ----------------------------
    public List<String> getBackupHistoryPaths()
    {
        String json = prefs.get(KEY_BACKUP_HISTORY, "[]");
        try
        {
            String[] paths = new Gson().fromJson(json, String[].class);
            return paths != null ? new ArrayList<>(Arrays.asList(paths)) : new ArrayList<>();
        }
        catch (Exception e)
        {
            return new ArrayList<>();
        }
    }

    public void addBackupHistoryPath(String path)
    {
        List<String> paths = getBackupHistoryPaths();
        paths.remove(path); // move to top if already present
        paths.add(0, path);
        if (paths.size() > 50) paths = paths.subList(0, 50); // cap history size
        prefs.put(KEY_BACKUP_HISTORY, new Gson().toJson(paths));
    }

    public void removeBackupHistoryPath(String path)
    {
        List<String> paths = getBackupHistoryPaths();
        paths.remove(path);
        prefs.put(KEY_BACKUP_HISTORY, new Gson().toJson(paths));
    }

    // --------------------- Image Viewer Quality  ----------------------------
    public ImageQuality getImageViewerQuality()
    {
        String saved = prefs.get(KEY_IMAGE_VIEWER_QUALITY, DEFAULT_IMAGE_VIEWER_QUALITY);
        try
        {
            return ImageQuality.valueOf(saved);
        }
        catch (IllegalArgumentException e)
        {
            return ImageQuality.HIGH;
        }
    }

    public void setImageViewerQuality(ImageQuality quality)
    {
        prefs.put(KEY_IMAGE_VIEWER_QUALITY, quality.name());
    }
}