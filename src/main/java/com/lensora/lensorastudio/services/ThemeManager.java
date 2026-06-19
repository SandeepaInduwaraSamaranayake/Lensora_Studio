package com.lensora.lensorastudio.services;

import atlantafx.base.theme.*;
import javafx.application.Application;
import javafx.scene.Scene;

/**
 * Applies and live-swaps AtlantaFX themes and font size on a {@link Scene}.
 *
 * Call {@link #apply(Scene)} once at startup, and again whenever the user
 * changes a setting in the preferences panel.
 */
public class ThemeManager
{
    private ThemeManager() {}

    /**
     * Reads current settings and applies the theme + font size to the scene.
     * Safe to call multiple times — each call fully replaces the previous state.
     */
    public static void apply(Scene scene)
    {
        AppSettings settings = AppSettings.getInstance();
        applyTheme(settings.getTheme());
        applyFontSize(scene, settings.getFontSize());
    }

    /**
     * Switches the global AtlantaFX theme stylesheet.
     * This is a global operation (like {@code setUserAgentStylesheet}) so it
     * automatically affects all open windows.
     */
    public static void applyTheme(AppSettings.Theme theme)
    {
        String stylesheet = switch (theme)
        {
            case CUPERTINO_DARK  -> new CupertinoDark().getUserAgentStylesheet();
            case CUPERTINO_LIGHT -> new CupertinoLight().getUserAgentStylesheet();
            case NORD_DARK       -> new NordDark().getUserAgentStylesheet();
            case PRIMER_DARK     -> new PrimerDark().getUserAgentStylesheet();
            case PRIMER_LIGHT    -> new PrimerLight().getUserAgentStylesheet();
        };
        Application.setUserAgentStylesheet(stylesheet);
    }

    /**
     * Updates the font-size override stylesheet on the given scene.
     * Uses an inline data URI so no external CSS file is needed at runtime.
     */
    public static void applyFontSize(Scene scene, double size)
    {
        // Remove any previously injected font-size override
        scene.getStylesheets().removeIf(s -> s.startsWith("data:text/css"));

        // Inject new override as an inline data URI — always wins the cascade
        String css = ".root { -fx-font-size: " + size + "px; }";
        String uri = "data:text/css," + css.replace(" ", "%20")
                                           .replace("{", "%7B")
                                           .replace("}", "%7D")
                                           .replace(":", "%3A")
                                           .replace(";", "%3B");
        scene.getStylesheets().add(uri);
    }
}