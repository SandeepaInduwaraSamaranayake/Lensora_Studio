package com.lensora.lensorastudio.core.config;

import atlantafx.base.theme.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;

/**
 * Centralised theme and styling manager for the entire application.
 * 
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Apply the global user-agent stylesheet (AtlantaFX / Modena / Caspian).</li>
 *   <li>Apply custom CSS overrides ({@code theme-overrides.css}) to all scenes.</li>
 *   <li>Apply font size consistently across all windows (main, dialogs, floating).</li>
 *   <li>Add CSS classes to the root node for theme-specific styling:
 *       <ul>
 *         <li>{@code dark-theme} – indicates a dark theme is active.</li>
 *         <li>{@code theme-<name>} – e.g., {@code theme-cupertino-dark}, {@code theme-modena}.</li>
 *         <li>{@code theme-styled} – internal marker to prevent duplicate styling.</li>
 *       </ul>
 *   </li>
 *   <li>Automatically style any new window (including SnapFX floating windows).</li>
 *   <li>Notify registered listeners when the theme changes.</li>
 * </ul>
 * 
 * <p><b>Usage:</b></p>
 * <ul>
 *   <li>Call {@link #apply(Scene)} once at startup from {@code App.start()}.</li>
 *   <li>Call {@link #applyFontSizeToAllWindows(double)} when the user changes font size.</li>
 *   <li>Call {@link #applyTheme(AppSettings.Theme)} when the user changes theme.</li>
 *   <li>For newly created dialogs, call {@link #initializeSceneStyling(Scene)}.</li>
 * </ul>
 * 
 * <p><b>Threading:</b> All public methods are thread-safe and will delegate to the
 * JavaFX Application Thread if called from a background thread.</p>
 */
public class ThemeManager
{
    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    private static final List<Consumer<AppSettings.Theme>> themeChangeListeners = new CopyOnWriteArrayList<>();

    // ---------- Global window listener – styles every new Stage -----------

    /**
     * Static initialiser that installs a global listener on {@link Window#getWindows()}.
     * 
     * <p>This listener intercepts every new {@code Stage} as soon as it is created
     * (including SnapFX floating windows, popups, and dialogs) and applies all
     * theme styling automatically.</p>
     * 
     * <p>If the scene is not yet attached, it waits for the {@code sceneProperty}
     * to be set before styling.</p>
     */
    static 
    {
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) 
            {
                if (change.wasAdded()) 
                {
                    for (Window window : change.getAddedSubList())
                    {
                        if (window instanceof Stage stage)
                        {
                            // Attempt to style immediately if the scene is ready
                            if (stage.getScene() != null) 
                            {
                                applyFullStyles(stage);
                            } 
                            else 
                            {
                                 // If not ready yet, style it as soon as the scene is attached
                                stage.sceneProperty().addListener(new ChangeListener<Scene>() 
                                {
                                    @Override
                                    public void changed(ObservableValue<? extends Scene> obs,
                                                        Scene oldScene, Scene newScene) 
                                    {
                                        if (newScene != null)
                                        {
                                            applyFullStyles(stage);
                                            stage.sceneProperty().removeListener(this);
                                        }
                                    }
                                });
                            }
                        }
                    }
                }
            }
        });
        logger.info("[ThemeManager] Global window listener installed.");
    }

    private ThemeManager() {}

// ==================================== PUBLIC API ===============================================    

    /**
     * Initialises the theme system for the main application scene.
     * 
     * <p>This is the entry point called once from {@code App.start()}.</p>
     * 
     * <p>It applies the global theme, styles the main scene, and then checks
     * for any existing windows that were created before the global listener
     * became active (e.g., SnapFX floating windows restored from a saved layout).</p>
     * 
     * @param scene the main application scene (must not be {@code null})
     */
    public static void apply(Scene scene)
    {
        logger.info("[ThemeManager.apply] Called with scene: {}", scene);
        AppSettings settings = AppSettings.getInstance();
        applyTheme(settings.getTheme());
        initializeSceneStyling(scene);          // styles the main scene
        styleAllUnstyledWindows();              // styles any SnapFX floating windows already open
    }

    /**
     * Switches the global user-agent stylesheet to the specified theme.
     * 
     * <p>This is a global operation - it affects all open windows automatically.</p>
     * 
     * <p>After switching, it updates the CSS classes on all existing windows
     * and notifies all registered listeners.</p>
     * 
     * @param theme the theme to apply (from {@link AppSettings.Theme})
     */
    public static void applyTheme(AppSettings.Theme theme) 
    {
        logger.info("[ThemeManager.applyTheme] Applying theme: {}", theme);
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

        applyThemeToAllWindows(theme);
        for (Consumer<AppSettings.Theme> listener : themeChangeListeners) 
        {
            listener.accept(theme);
        }
    }

    /**
     * One‑stop styling for any scene that is created explicitly in code.
     * 
     * <p>This method applies the stylesheet, theme classes, and font size to the scene.
     * It also marks the scene as {@code theme-styled} to prevent the global listener
     * from re‑styling it.</p>
     * 
     * <p>Use this for dialogs, custom windows, and any scene you create manually.</p>
     * 
     * @param scene the scene to style (must not be {@code null})
     */
    public static void initializeSceneStyling(Scene scene) 
    {
        applyFullStyles(scene);
    }

    /**
     * Applies the current font size to every open window.
     * 
     * <p>This is called when the user changes the font size in Preferences.</p>
     * 
     * <p>Unlike {@link #applyFullStyles(Scene)}, this method does NOT check the
     * {@code theme-styled} marker - it updates the font size on all windows
     * regardless of whether they were already styled.</p>
     * 
     * @param size the font size in points (e.g., 11.0, 12.5)
     */
    public static void applyFontSizeToAllWindows(double size) 
    {
        logger.info("[ThemeManager] Applying font size {} to all open windows", size);
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
            logger.info("[ThemeManager] No open windows found");
        }
    }

    /**
     * Applies the font size to a single scene by setting an inline style on the root node.
     * 
     * <p>This method does NOT check the {@code theme-styled} marker – it always applies
     * the font size. Use this for direct font-size updates.</p>
     * 
     * @param scene the scene to update (must not be {@code null})
     * @param size  the font size in points
     */
    public static void applyFontSizeToScene(Scene scene, double size) 
    {
        if (scene == null || scene.getRoot() == null) 
        {
            logger.debug("[ThemeManager] Cannot apply font size: scene or root is null");
            return;
        }
        scene.getRoot().setStyle("-fx-font-size: " + size + "px;");
        logger.debug("[ThemeManager] Applied font size {} to scene: {}", size, scene);
    }

    /**
     * Convenience method - applies the currently configured font size to a single scene.
     * 
     * @param scene the scene to update (must not be {@code null})
     */
    public static void applyCurrentFontSizeToScene(Scene scene) 
    {
        applyFontSizeToScene(scene, AppSettings.getInstance().getFontSize());
    }

    // ─── INTERNAL CORE ──────────────────────────────────────────────────

    /**
     * The unified styling method.
     * 
     * <p>This applies the custom stylesheet, theme classes, and font size to a scene,
     * but only if the scene has not already been styled (checked via the
     * {@code theme-styled} marker).</p>
     * 
     * <p>After styling, the scene is marked as {@code theme-styled} so that subsequent
     * calls to this method (e.g., from the global listener) are skipped.</p>
     * 
     * @param scene the scene to style (must not be {@code null})
     */
    private static void applyFullStyles(Scene scene) 
    {
        if (scene == null || scene.getRoot() == null) 
        {
            logger.debug("[ThemeManager] Cannot style - scene or root is null");
            return;
        }

        if (scene.getRoot().getStyleClass().contains("theme-styled")) 
        {
            logger.debug("[ThemeManager] Skipping already-styled scene: {}", scene);
            return;
        }

        AppSettings settings = AppSettings.getInstance();
        AppSettings.Theme currentTheme = settings.getTheme();

        String themeOverrides = Resources.THEME_OVERRIDES.url().toExternalForm();
        if (!scene.getStylesheets().contains(themeOverrides)) 
        {
            scene.getStylesheets().add(themeOverrides);
        }

        boolean isDark = isDarkTheme(currentTheme);
        setDarkThemeClass(scene, isDark);

        // Also apply the theme-specific class to this new scene
        applyThemeClassToScene(currentTheme, scene);

        // Apply font size
        applyCurrentFontSizeToScene(scene);

        // MARK: This scene has been fully styled, skip the global listener for it.
        scene.getRoot().getStyleClass().add("theme-styled");

        logger.info("[ThemeManager] Fully styled scene: {}", scene);
    }

    /**
     * Convenience overload for {@link Stage} - extracts the scene and calls
     * {@link #applyFullStyles(Scene)}.
     * 
     * <p>This is used by the global window listener.</p>
     * 
     * @param stage the stage to style (must not be {@code null})
     */
    private static void applyFullStyles(Stage stage) 
    {
        if (stage == null) return;
        if (!Platform.isFxApplicationThread()) 
        {
            Platform.runLater(() -> applyFullStyles(stage));
            return;
        }
        Scene scene = stage.getScene();
        if (scene != null) 
        {
            applyFullStyles(scene);
        }
    }

    /**
     * Styles any open window whose scene does not have the {@code theme-styled} marker.
     * 
     * <p>This is called once at startup to catch windows that were created before
     * the global listener was installed (e.g., SnapFX floating windows restored
     * from a saved layout).</p>
     */
    private static void styleAllUnstyledWindows() 
    {
        for (Window window : Window.getWindows()) 
        {
            if (window instanceof Stage stage) 
            {
                Scene scene = stage.getScene();
                if (scene != null && scene.getRoot() != null) 
                {
                    if (!scene.getRoot().getStyleClass().contains("theme-styled")) 
                    {
                        applyFullStyles(scene);
                    }
                }
            }
        }
    }

    // ─── HELPERS for theme classes ─────────────────────────────────────

    /**
     * Updates the {@code dark-theme} and {@code theme-*} classes on all open windows.
     * 
     * <p>This is called when the user changes the theme via Preferences.</p>
     * 
     * @param theme the new theme
     */
    private static void applyThemeToAllWindows(AppSettings.Theme theme) 
    {
        boolean isDark = isDarkTheme(theme);
        String themeClass = themeClassName(theme);
        for (Window window : Window.getWindows()) 
        {
            if (window instanceof Stage stage && stage.getScene() != null && stage.getScene().getRoot() != null) 
            {
                var styleClasses = stage.getScene().getRoot().getStyleClass();
                // Update dark-theme marker
                styleClasses.remove("dark-theme");
                if (isDark) styleClasses.add("dark-theme");
                // Update theme-specific class
                styleClasses.removeIf(cls -> cls.startsWith("theme-"));
                styleClasses.add(themeClass);
            }
        }
    }

    /**
     * Applies the per‑theme class (e.g., {@code theme-cupertino-dark}) to a single scene.
     * 
     * <p>Removes any previous {@code theme-*} class before adding the new one.</p>
     * 
     * @param theme the current theme
     * @param scene the scene to update
     */
    private static void applyThemeClassToScene(AppSettings.Theme theme, Scene scene) 
    {
        if (scene == null || scene.getRoot() == null) return;
        var styleClasses = scene.getRoot().getStyleClass();
        // Remove any existing theme-* class
        styleClasses.removeIf(cls -> cls.startsWith("theme-"));
        styleClasses.add(themeClassName(theme));
    }

    /**
     * Adds or removes the {@code dark-theme} class on a scene's root node.
     * 
     * @param scene  the scene to update
     * @param isDark {@code true} for dark themes, {@code false} for light
     */
    private static void setDarkThemeClass(Scene scene, boolean isDark) 
    {
        var styleClasses = scene.getRoot().getStyleClass();
        styleClasses.remove("dark-theme");
        if (isDark) styleClasses.add("dark-theme");
    }

    /**
     * Converts a {@link AppSettings.Theme} enum value to a CSS class name.
     * 
     * <p>Examples:</p>
     * <ul>
     *   <li>{@code CUPERTINO_DARK} -> {@code theme-cupertino-dark}</li>
     *   <li>{@code MODENA} -> {@code theme-modena}</li>
     * </ul>
     * 
     * @param theme the theme enum value
     * @return the CSS class name (lowercase, underscores replaced with hyphens)
     */
    private static String themeClassName(AppSettings.Theme theme) 
    {
        return "theme-" + theme.name().toLowerCase().replace('_', '-');
    }

    /**
     * Determines whether a theme is considered "dark" for the purpose of
     * applying the {@code dark-theme} CSS class.
     * 
     * @param theme the theme to check
     * @return {@code true} for dark themes, {@code false} for light themes
     */
    private static boolean isDarkTheme(AppSettings.Theme theme) 
    {
        return switch (theme) 
        {
            case CUPERTINO_DARK, NORD_DARK, PRIMER_DARK -> true;
            case CUPERTINO_LIGHT, PRIMER_LIGHT, MODENA, CASPIAN -> false;
        };
    }

    // ─── Theme Change Listeners ────────────────────────────────────────

    /**
     * Replaces all registered theme-change listeners with a single listener.
     * 
     * <p>This is a backward‑compatibility method for legacy call sites.</p>
     * 
     * @param listener the new listener (or {@code null} to clear all listeners)
     */
    public static void setThemeChangeListener(Consumer<AppSettings.Theme> listener) 
    {
        themeChangeListeners.clear();
        if (listener != null) themeChangeListeners.add(listener);
    }

    /**
     * Adds a listener that is notified whenever the theme is changed.
     * 
     * <p>Use this for components that need to react to theme changes (e.g., SnapFX docking).</p>
     * 
     * @param listener the listener to add (must not be {@code null})
     */
    public static void addThemeChangeListener(Consumer<AppSettings.Theme> listener) 
    {
        if (listener != null) themeChangeListeners.add(listener);
    }

    /**
     * Removes a previously registered theme-change listener.
     * 
     * <p>Call this when the component no longer needs to receive theme updates
     * (e.g., when a window closes).</p>
     * 
     * @param listener the listener to remove
     */
    public static void removeThemeChangeListener(Consumer<AppSettings.Theme> listener) 
    {
        themeChangeListeners.remove(listener);
    }
}