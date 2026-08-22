package com.lensora.lensorastudio.core.config;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.stage.Stage;
import javafx.stage.Screen;

import javafx.util.Duration;
import java.util.prefs.Preferences;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/* ------------------------------ Usage ------------------------------------------
 *
 *  In App.start()
 *      LayoutPersistence.bindWindow(stage);
 */
public class LayoutPersistence 
{
    private static final Logger logger = LoggerFactory.getLogger(LayoutPersistence.class);

    // --------------------- Preference key namespace ---------------------------

    private static final String KEY_WIN_X     = "layout.window.x";
    private static final String KEY_WIN_Y     = "layout.window.y";
    private static final String KEY_WIN_W     = "layout.window.width";
    private static final String KEY_WIN_H     = "layout.window.height";
    private static final String KEY_WIN_MAX   = "layout.window.maximised";

    private static final Preferences PREFS = Preferences.userNodeForPackage(LayoutPersistence.class);

    private LayoutPersistence() {}

    // =========================================================================
    // WINDOW SIZE & POSITION
    // =========================================================================

    /**
     * Restores the window's last size/position and attaches listeners to
     * save future changes.
     *
     * Call from App.start()
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
                    PauseTransition wait = new PauseTransition(Duration.millis(100));
                    wait.setOnFinished(e -> 
                        onLayoutReady.run()
                    );
                    wait.play();
                }
                else 
                {
                    // Non-maximised: just wait for the next layout pulse.
                    Platform.runLater(
                        onLayoutReady
                    );
                }
            }
        });

        // Guard against save attempts while the maximise animation is running
        stage.maximizedProperty().addListener((obs, wasMax, isNowMax) -> {
            PREFS.putBoolean(KEY_WIN_MAX, isNowMax);
        });

        // Save window geometry on changes
        attachWindowSaveListeners(stage);
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
            return; // No saved state - leave JavaFX defaults
        }

        // Guard: ensure the window is still visible on the current screen setup.
        // If the saved position is off-screen (disconnected monitor), reset to
        // a safe centered position on the primary screen.
        if (!isOnScreen(savedX, savedY, savedW, savedH))
        {
            Rectangle2D primary = Screen.getPrimary().getVisualBounds();
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
        // Save size - only when not maximised to avoid storing 1920x1080
        ChangeListener<Number> sizeListener = (obs, old, val) -> {
            if (!stage.isMaximized())
            {
                PREFS.putDouble(KEY_WIN_W, stage.getWidth());
                PREFS.putDouble(KEY_WIN_H, stage.getHeight());
            }
        };
        stage.widthProperty().addListener(sizeListener);
        stage.heightProperty().addListener(sizeListener);

        // Save position - only when not maximised
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