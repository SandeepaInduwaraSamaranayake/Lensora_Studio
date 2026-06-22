package com.lensora.lensorastudio.services;


import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.SplitPane;
import javafx.stage.Stage;
import javafx.stage.Screen;

import javafx.util.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/* ------------------------------ Usage ------------------------------------------
 * In MainController.initialize():
 *   LayoutPersistence.bindSplitPane("main.horizontal", mainSplitPane);
 *   LayoutPersistence.bindSplitPane("detail.vertical", projectWorkspace);
 *   LayoutPersistence.bindSplitPane("file.horizontal", fileSplitPane);
 *
 * In App.start() inside Platform.runLater() after stage.show():
 *   LayoutPersistence.bindWindow(stage);
 */
public class LayoutPersistence 
{
    private static final Logger logger = LoggerFactory.getLogger(LayoutPersistence.class);

    // --------------------- Preference key namespace ---------------------------

    private static final String PREFIX_SPLIT  = "layout.split.";
    private static final String KEY_WIN_X     = "layout.window.x";
    private static final String KEY_WIN_Y     = "layout.window.y";
    private static final String KEY_WIN_W     = "layout.window.width";
    private static final String KEY_WIN_H     = "layout.window.height";
    private static final String KEY_WIN_MAX   = "layout.window.maximised";

    /** Values outside this range are layout drift, not user intent. */
    private static final double MIN_SAVE = 0.10;
    private static final double MAX_SAVE = 0.90;

    private static final Preferences PREFS = Preferences.userNodeForPackage(LayoutPersistence.class);
    private static final List<PaneEntry> registeredPanes = new ArrayList<>();
    private record PaneEntry(String key, SplitPane pane) {}

    // Live DoubleProperty for each registered divider, keyed by "paneKey.dividerIndex"
    private static final Map<String, DoubleProperty> dividerProps = new HashMap<>();


    /**
     * False during the entire startup sequence (FXML load → window show →
     * geometry restore → maximise animation). Set to true by App.start()
     * once everything has settled. Saves are blocked until then.
     */
    private static volatile boolean startupComplete = false;

    /**
     * Set to true while the window is in the process of maximising.
     * Saves are blocked during this window to prevent layout-drift values
     * from overwriting the real saved position.
     */
    private static volatile boolean isMaximising = false;

    private LayoutPersistence() {}

        /**
     * Signals that startup is complete and user-drag saves can now be
     * persisted. Call from App.start() after bindWindow() returns and
     * the maximise animation has had time to settle.
     */
    public static void markStartupComplete()
    {
        startupComplete = true;
        logger.info("[Layout] startup complete — user-drag saves enabled.");
    }

    // =========================================================================
    // SPLIT PANES
    // =========================================================================

    /**
     * Registers a SplitPane for full layout persistence using bidirectional
     * binding. Works for both always-visible and initially-hidden panes.
     *
     * Call from MainController.initialize() for every SplitPane.
     * No separate restore step needed — binding handles everything.
     * 
     * @param key       unique identifier for this pane (e.g. "main.horizontal")
     * @param splitPane the SplitPane to bind
     */
    public static void bindSplitPane(String key, SplitPane splitPane)
    {
        var dividers = splitPane.getDividers();
        logger.info("[Layout] bindSplitPane key=" + key + " dividers=" + dividers.size() + " visible=" + splitPane.isVisible());

        for (int i = 0; i < dividers.size(); i++)
        {
            final String propKey = key + "." + i;
            double saved = PREFS.getDouble(PREFIX_SPLIT + propKey, -1.0);

            // Only use saved value if it's in a sane range
            double seed = (saved >= MIN_SAVE && saved <= MAX_SAVE)
                ? saved
                : dividers.get(i).getPosition();

            logger.info("[Layout] divider[{}] saved={} seed={}",i,"%.4f".formatted(saved),"%.4f".formatted(seed));

            DoubleProperty prop = new SimpleDoubleProperty(seed);
            dividerProps.put(propKey, prop);

            dividers.get(i).positionProperty().bindBidirectional(prop);

            final int idx = i;
            prop.addListener((obs, oldVal, newVal) ->
            {
                double v = newVal.doubleValue();

                // Block ALL saves until startup is fully complete.
                // Block values outside the human-drag range.
                // Block saves while the window is maximising.
                // This prevents any drift from initial layout, window resize,
                // or maximise animation from overwriting the real saved value.
                if (!startupComplete || isMaximising || v < MIN_SAVE || v > MAX_SAVE) 
                {
                    logger.info("[Layout] SAVE BLOCKED key={} divider[{}] {}",propKey,idx,"%.4f".formatted(v));
                    return;  // ignore transient or invalid values
                }

                logger.info("[Layout] SAVE key={} divider[{}] {}",propKey,idx,"%.4f".formatted(v));
                PREFS.putDouble(PREFIX_SPLIT + propKey, v);
            });
        }
        registeredPanes.add(new PaneEntry(key, splitPane));
    }

    // =========================================================================
    // WINDOW SIZE & POSITION
    // =========================================================================

    /**
     * Restores the window's last size/position and attaches listeners to
     * save future changes.
     *
     * Call from App.start() inside {@code Platform.runLater} after
     * {@code stage.show()}.
     *
     * The window is kept on-screen even if the saved position was on a monitor
     * that is no longer connected.
     * 
     * @param stage the main application stage
     * @param onLayoutReady  runnable to call when the layout is ready
     */
    public static void bindWindow(Stage stage, Runnable onLayoutReady)
    {
        // Restore window size/position/maximised (before show)
        restoreWindow(stage);

        // After the window is shown and the maximise animation completes,
        // reapply all dividers to ensure they match the restored geometry.
        stage.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) 
            {
                // We need to wait for the layout to settle.
                // For maximised windows, also wait for the animation.
                if (stage.isMaximized()) 
                {
                    PauseTransition wait = new PauseTransition(Duration.millis(300));
                    wait.setOnFinished(e -> {
                        isMaximising = false;
                        applyDividersAndEnableSaves();
                        onLayoutReady.run();
                    });
                    wait.play();
                } 
                else 
                {
                    // Non-maximised: just wait for the next layout pulse.
                    Platform.runLater(() -> {
                        applyDividersAndEnableSaves();
                        onLayoutReady.run();
                    });
                }
            }
        });

        // Guard against save attempts while the maximise animation is running
        stage.maximizedProperty().addListener((obs, wasMax, isNowMax) -> {
        isMaximising = true;
        PREFS.putBoolean(KEY_WIN_MAX, isNowMax);
        PauseTransition reset = new PauseTransition(Duration.millis(300));
        reset.setOnFinished(e -> {
            isMaximising = false;
            applyDividersAndEnableSaves();
        });
        reset.play();
        });

        // Save window geometry on changes
        attachWindowSaveListeners(stage);
    }

    /**
     * 
     */
    private static void applyDividersAndEnableSaves() 
    {
        reapplyAllDividers();
        startupComplete = true;
    }

    /**
     * Forcibly reapplies all stored divider positions to every registered SplitPane.
     * Used after the window geometry has changed (e.g. after maximising).
     */
    private static void reapplyAllDividers()
    {
        for (PaneEntry entry : registeredPanes)
        {
            var dividers = entry.pane().getDividers();
            for (int i = 0; i < dividers.size(); i++)
            {
                double saved = PREFS.getDouble(PREFIX_SPLIT + entry.key() + "." + i, -1.0);
                if (saved >= MIN_SAVE && saved <= MAX_SAVE)
                {
                    dividers.get(i).setPosition(saved);
                }
            }
        }
    }


    // ----------------------------- Restore window -------------------------------------

    /**
     * Restores the window geometry from preferences.
     * @param stage the main application stage
    */
    private static void restoreWindow(Stage stage)
    {
        boolean wasMaximised = PREFS.getBoolean(KEY_WIN_MAX, true);
        logger.info("[Layout] restoreWindow() - wasMaximised=" + wasMaximised);

        if (wasMaximised)
        {
            stage.setMaximized(true);
            return;
        }

        double savedX = PREFS.getDouble(KEY_WIN_X, Double.NaN);
        double savedY = PREFS.getDouble(KEY_WIN_Y, Double.NaN);
        double savedW = PREFS.getDouble(KEY_WIN_W, Double.NaN);
        double savedH = PREFS.getDouble(KEY_WIN_H, Double.NaN);

        logger.info("[Layout] restoreWindow() - saved x={} y={} w={} h={}",
        "%.1f".formatted(savedX),
        "%.1f".formatted(savedY),
        "%.1f".formatted(savedW),
        "%.1f".formatted(savedH)
        );

        if (Double.isNaN(savedX) || Double.isNaN(savedY) || Double.isNaN(savedW) || Double.isNaN(savedH))
        {
            logger.info("[Layout] restoreWindow() - no saved geometry, skipping.");
            return; // No saved state — leave JavaFX defaults
        }

        // Guard: ensure the window is still visible on the current screen setup.
        // If the saved position is off-screen (disconnected monitor), reset to
        // a safe centered position on the primary screen.
        if (!isOnScreen(savedX, savedY, savedW, savedH))
        {
            javafx.geometry.Rectangle2D primary = Screen.getPrimary().getVisualBounds();
            stage.setX((primary.getWidth()  - savedW) / 2.0);
            stage.setY((primary.getHeight() - savedH) / 2.0);
            logger.info("[Layout] restoreWindow() - saved position off-screen, centered instead.");
        }
        else
        {
            stage.setX(savedX);
            stage.setY(savedY);
            logger.info("[Layout] restoreWindow() - position restored to saved values.");
        }
        stage.setWidth(savedW);
        stage.setHeight(savedH);
    }

    /**
     * Returns true if the top-left corner of the window is visible on at
     * least one currently connected screen.
     */
    private static boolean isOnScreen(double x, double y, double w, double h)
    {
        // A window is "on screen" if its top-left quarter is accessible
        double checkX = x + w * 0.25;
        double checkY = y + h * 0.25;

        return Screen.getScreensForRectangle(checkX, checkY, 1, 1).size() > 0;
    }


    //------------------------- Save window listeners ---------------------------------
    private static void attachWindowSaveListeners(Stage stage)
    {
        // Save size — only when not maximised to avoid storing 1920x1080
        ChangeListener<Number> sizeListener = (obs, old, val) -> {
            if (!stage.isMaximized())
            {
                PREFS.putDouble(KEY_WIN_W, stage.getWidth());
                PREFS.putDouble(KEY_WIN_H, stage.getHeight());
            }
        };
        stage.widthProperty().addListener(sizeListener);
        stage.heightProperty().addListener(sizeListener);

        // Save position — only when not maximised
        ChangeListener<Number> posListener = (obs, old, val) -> {
            if (!stage.isMaximized())
            {
                PREFS.putDouble(KEY_WIN_X, stage.getX());
                PREFS.putDouble(KEY_WIN_Y, stage.getY());
            }
        };
        stage.xProperty().addListener(posListener);
        stage.yProperty().addListener(posListener);
    }
}