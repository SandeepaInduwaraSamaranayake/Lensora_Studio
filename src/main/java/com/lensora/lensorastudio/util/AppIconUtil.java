package com.lensora.lensorastudio.util;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppIconUtil 
{
    private static final Logger logger = LoggerFactory.getLogger(AppIconUtil.class);
    private static final List<Image> APP_ICONS = new ArrayList<>();

    static 
    {
        // Load once and cache
        loadIcon(Resources.APP_ICON_16);
        loadIcon(Resources.APP_ICON_32);
        loadIcon(Resources.APP_ICON_64);
        loadIcon(Resources.APP_ICON_512);
    }

    private static void loadIcon(Resources resource) 
    {
        try
        {
            APP_ICONS.add(new Image(resource.getResourceAsStream()));
        } 
        catch (Exception e) 
        {
            // Log warning and continue – missing icons are not fatal
            logger.error("Failed to load icon: " + resource.name());
        }
    }

    /** Adds the application icon to the given Stage. If no icon loaded, does nothing. */
    public static void setAppIcon(Stage stage) 
    {
        if (!APP_ICONS.isEmpty()) 
        {
            stage.getIcons().addAll(APP_ICONS);
        }
    }

    /** Convenience method that clears existing icons first. */
    public static void setAppIconReplace(Stage stage) 
    {
        stage.getIcons().clear();
        setAppIcon(stage);
    }
}