package com.lensora.lensorastudio.services;

import atlantafx.base.theme.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.slf4j.LoggerFactory;

import com.lensora.lensorastudio.util.Resources;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * Applies and live-swaps AtlantaFX themes and font size on a {@link Scene}.
 *
 * Call {@link #apply(Scene)} once at startup, and again whenever the user
 * changes a setting in the preferences panel.
 */
public class ThemeManager
{
    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    private static final List<Consumer<AppSettings.Theme>> themeChangeListeners = new CopyOnWriteArrayList<>();

    private ThemeManager() {}

    // ==================================== PUBLIC API ===============================================

    /**
     * Reads current settings and applies the theme + font size to the scene.
     * Safe to call multiple times — each call fully replaces the previous state.
     */
    public static void apply(Scene scene)
    {
        logger.info("[ThemeManager.apply] Called with scene: " + scene);
        AppSettings settings = AppSettings.getInstance();
        applyTheme(settings.getTheme());
        applyFontSizeToScene(scene, settings.getFontSize()); // direct application
        initializeSceneStyling(scene);
        applyFontSizeToAllWindows(settings.getFontSize());   // in case windows already exist
    }

    /**
     * Switches the global AtlantaFX theme stylesheet.
     * This is a global operation (like {@code setUserAgentStylesheet}) so it
     * automatically affects all open windows.
     */
    public static void applyTheme(AppSettings.Theme theme)
    {
        logger.info("[ThemeManager.applyTheme] Applying theme: " + theme);
        switch (theme)
        {
            case CUPERTINO_DARK  -> Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());
            case CUPERTINO_LIGHT -> Application.setUserAgentStylesheet(new CupertinoLight().getUserAgentStylesheet());
            case NORD_DARK       -> Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
            case PRIMER_DARK     -> Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            case PRIMER_LIGHT    -> Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            case MODENA          -> Application.setUserAgentStylesheet(Application.STYLESHEET_MODENA);
            case CASPIAN         -> Application.setUserAgentStylesheet(Application.STYLESHEET_CASPIAN);
        }

        applyDarkThemeMarker(theme);

        for (Consumer<AppSettings.Theme> listener : themeChangeListeners)
        {
            listener.accept(theme);
        }
    }

    /** One-stop setup for any newly created Scene: font size, icon-size, and dark/light icon marker. */
    public static void initializeSceneStyling(Scene scene)
    {
        if (scene == null || scene.getRoot() == null) return;

        applyCurrentFontSizeToScene(scene);

        String iconCss = Resources.ICON_SIZE_LOCK_STYLE.url().toExternalForm();
        if (!scene.getStylesheets().contains(iconCss))
        {
            scene.getStylesheets().add(iconCss);
        }

        applyIconThemeClass(scene);
    }

    /**
     * Toggles a "dark-theme" style class on every open window's scene root,
     * so app-overrides.css can flip icon (and any other) colors with a plain
     * scoped selector instead of relying on -fx-icon-color variable-lookup
     * fallback syntax, which does not reliably resolve for this
     * Ikonli-specific property under Modena/Caspian.
     */
    private static void applyDarkThemeMarker(AppSettings.Theme theme)
    {
        boolean isDark = isDarkTheme(theme);
        for (Window window : Window.getWindows())
        {
            if (window instanceof Stage stage && stage.getScene() != null && stage.getScene().getRoot() != null)
            {
                setDarkThemeClass(stage.getScene(), isDark);
            }
        }
    }

    public static void applyIconThemeClass(Scene scene)
    {
        if (scene == null || scene.getRoot() == null) return;
        setDarkThemeClass(scene, isDarkTheme(AppSettings.getInstance().getTheme()));
    }

    private static void setDarkThemeClass(Scene scene, boolean isDark)
    {
        var styleClasses = scene.getRoot().getStyleClass();
        styleClasses.remove("dark-theme");
        if (isDark) styleClasses.add("dark-theme");
    }

    private static boolean isDarkTheme(AppSettings.Theme theme)
    {
        return switch (theme)
        {
            case CUPERTINO_DARK, NORD_DARK, PRIMER_DARK -> true;
            case CUPERTINO_LIGHT, PRIMER_LIGHT, MODENA, CASPIAN -> false;
        };
    }

    /**
     * Applies the font size to ALL currently open windows.
     * Useful after the user saves new font size settings.
     */
    public static void applyFontSizeToAllWindows(double size) 
    {
        logger.info("[ThemeManager] Applying font size " + size + " to all open windows");
        int count = 0;
        for (Window window : Window.getWindows()) 
        {
            if (window instanceof Stage) 
            {
                Scene scene = ((Stage) window).getScene();
                if (scene != null) 
                {
                    applyFontSizeToScene(scene, size);
                    count++;
                }
            }
        }
        if (count == 0)
        {
            logger.info("[ThemeManager] No open windows found - font size will be applied when windows appear (ensure you call applyFontSizeToScene for the initial scene)");
        }
    }

    /**
     * Applies the font size by directly setting the style on the root node.
     * This is much more reliable than using a data‑URI stylesheet.
     */
    public static void applyFontSizeToScene(Scene scene, double size)
    {
        if (scene == null || scene.getRoot() == null) 
        {
            logger.info("[ThemeManager] Cannot apply font size: scene or root is null");
            return;
        }
        scene.getRoot().setStyle("-fx-font-size: " + size + "px;");
        logger.info("[ThemeManager] Applied font size " + size + " to scene: " + scene);
    }

    /**
     * Convenience method
     * @param scene specific scene
     */
    public static void applyCurrentFontSizeToScene(Scene scene) 
    {
        applyFontSizeToScene(scene, AppSettings.getInstance().getFontSize());
    }

    /**
     * Back-compat single-listener setter. Replaces any previously
     * registered listeners with just this one — kept so existing call
     * sites (e.g. MainController) don't need to change.
     */
    public static void setThemeChangeListener(Consumer<AppSettings.Theme> listener) 
    {
        themeChangeListeners.clear();
        if (listener != null)
        {
            themeChangeListeners.add(listener);
        }
    }

    /**
     * Adds an additional theme-change listener without disturbing any
     * others already registered (e.g. MainController's dock-sync
     * listener). Used by secondary windows such as the image viewer,
     * which only need to listen while their window is open.
     */
    public static void addThemeChangeListener(Consumer<AppSettings.Theme> listener)
    {
        if (listener != null)
        {
            themeChangeListeners.add(listener);
        }
    }

    /** Removes a previously added listener — call when the owning window closes. */
    public static void removeThemeChangeListener(Consumer<AppSettings.Theme> listener)
    {
        themeChangeListeners.remove(listener);
    }
}