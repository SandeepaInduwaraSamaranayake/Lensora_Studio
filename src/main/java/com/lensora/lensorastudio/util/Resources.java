package com.lensora.lensorastudio.util;

import java.io.InputStream;
import java.net.URL;

public enum Resources
{
    
    //############################################# Views ########################################################
    MAIN_VIEW("/com/lensora/lensorastudio/views/main-view.fxml"),
    SETTINGS_VIEW("/com/lensora/lensorastudio/views/settings-view.fxml"),
    ABOUT_VIEW("/com/lensora/lensorastudio/views/about-view.fxml"),
    NEW_PROJECT_VIEW("/com/lensora/lensorastudio/views/new-project-view.fxml"),

    //########################################### SQL Scripts ####################################################
    SQL_SCHEMA("/com/lensora/lensorastudio/database/schema.sql"),

    //########################################### MANIFEST.MF ####################################################
    MANIFEST("/META-INF/MANIFEST.MF"),
    
    //########################################### ICONS & IMAGES #################################################
    APP_ICON("/com/lensora/lensorastudio/images/lensora_32x32.png");


    private final String path;

    Resources(String path)
    {
        this.path = path;
    }

    public String getResource() 
    {
        return path;
    }

    public InputStream getResourceAsStream()
    {
        return Resources.class.getResourceAsStream(path);
    }

    public URL url() 
    {
        return Resources.class.getResource(path);
    }

}