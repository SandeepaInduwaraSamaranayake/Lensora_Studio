package com.lensora.lensorastudio.util;

import java.io.InputStream;
import java.net.URL;

public enum Resources
{
    MAIN_VIEW("/com/lensora/lensorastudio/views/main-view.fxml"),
    SETTINGS_VIEW("/com/lensora/lensorastudio/views/settings-view.fxml"),
    ABOUT_VIEW("/com/lensora/lensorastudio/views/about-view.fxml"),
    NEW_PROJECT_VIEW("/com/lensora/lensorastudio/views/new-project-view.fxml"),
    LOG_VIEWER_VIEW("/com/lensora/lensorastudio/views/log-viewer.fxml"),

    PROJECT_LIST_VIEW("/com/lensora/lensorastudio/views/project-list-view.fxml"),
    PROJECT_DETAILS_VIEW("/com/lensora/lensorastudio/views/project-details-view.fxml"),
    FILE_EXPLORER_VIEW("/com/lensora/lensorastudio/views/file-explorer-view.fxml"),
    STATUS_BAR_VIEW("/com/lensora/lensorastudio/views/status-bar-view.fxml"),
    FOLDER_TEMPLATE_MANAGER_VIEW("/com/lensora/lensorastudio/views/folder-template-manager-view.fxml"),

    SQL_SCHEMA("/com/lensora/lensorastudio/database/schema.sql"),
    MANIFEST("/META-INF/MANIFEST.MF"),
    
    APP_ICON_16("/com/lensora/lensorastudio/images/lensora_16x16.png"),
    APP_ICON_24("/com/lensora/lensorastudio/images/lensora_24x24.png"),
    APP_ICON_32("/com/lensora/lensorastudio/images/lensora_32x32.png"),
    APP_ICON_64("/com/lensora/lensorastudio/images/lensora_64x64.png"),
    APP_ICON_512("/com/lensora/lensorastudio/images/lensora_512x512.png"),
    SPLASH_SCREEN("/com/lensora/lensorastudio/images/lensora_splash.png"),

    ICON_SIZE_LOCK_STYLE("/com/lensora/lensorastudio/styles/theme-overrides.css");

    private final String path;
    Resources(String path) { this.path = path; }
    public String getResource() { return path; }
    public InputStream getResourceAsStream() { return Resources.class.getResourceAsStream(path); }
    public URL url() { return Resources.class.getResource(path); }
}