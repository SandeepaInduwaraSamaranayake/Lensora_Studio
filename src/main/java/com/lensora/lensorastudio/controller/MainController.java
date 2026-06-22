package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.services.LayoutPersistence;
import com.lensora.lensorastudio.services.ThemeManager;
import com.lensora.lensorastudio.util.DialogBuilder;
import com.lensora.lensorastudio.util.Resources;

import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class MainController
{
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // ─── Constants ────────────────────────────────────────────────────────────

    /** Fraction of the primary screen used for the restored window size. */
    private static final double WINDOW_SCALE      = 0.70;

    /** Pixels from the top edge that trigger a snap-maximise on drag. */
    private static final double SNAP_THRESHOLD    = 4.0;

    /** Pixels the cursor must travel below the top edge to trigger pull-down restore. */
    private static final double RESTORE_THRESHOLD = 15.0;

    /**
     * Fixed Y anchor used when placing the restored window under the cursor
     * after a pull-down.  Chosen to feel like the cursor is sitting in the
     * middle of a typical title bar regardless of DPI or OS theme changes.
     */
    private static final double RESTORED_Y_ANCHOR = 15.0;

    // ─── State ────────────────────────────────────────────────────────────────

    /** Saved width / height of the window before it was maximised. */
    private double savedWidth, savedHeight;

    /** Saved X / Y position of the window before it was maximised. */
    private double savedX, savedY;

    /**
     * Offset between the cursor's scene-local position and the window origin
     * at the moment a drag begins.
     */
    private double dragOffsetX, dragOffsetY;

    /** Whether the window is currently in the maximised state. */
    private boolean isMaximised = false;

    /**
     * Guard flag that blocks re-entry into {@link #snapToMaximise} while a
     * snap operation is already in flight.
     *
     * <p>On Linux/Wayland and macOS, {@code stage.maximizedProperty()} fires
     * asynchronously — there can be several frames between us calling
     * {@code stage.setMaximized(true)} and the listener setting
     * {@code isMaximised = true}.  During that window, {@code onDragged} would
     * otherwise see {@code isMaximised == false} and call {@code snapToMaximise}
     * again, overwriting the correct {@code savedWidth/savedHeight} with the
     * already-maximised dimensions.
     */
    private boolean isSnapping = false;

    // ─── FXML ─────────────────────────────────────────────────────────────────

    @FXML
    private HBox headerBar;

    @FXML
    private MenuItem mnu_btn_exit;

    @FXML
    private MenuItem mnu_btn_preferences;

    @FXML
    private MenuItem mnu_btn_about;

    @FXML 
    private SplitPane mainSplitPane;        // the root SplitPane

    @FXML 
    private SplitPane projectWorkspace;     // vertical split in right panel

    @FXML 
    private SplitPane fileSplitPane;        // folder tree | file table



    // ─── Initialisation ───────────────────────────────────────────────────────

    @FXML
    public void initialize()
    {
        /**
         * ####################################################################
         * ######################### TITLE BAR INIT ###########################
         * ####################################################################
         */
        logger.info("[Lensora] Initializing native window style handlers...");

        if (headerBar == null)
        {
            logger.error("[Lensora] ERROR: headerBar FXML injection failed!");
            return;
        }

        // Walk scene → window once both are available
        headerBar.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
            if (newScene == null) return;

            newScene.windowProperty().addListener((obsWindow, oldWindow, newWindow) -> {
                if (!(newWindow instanceof Stage stage)) return;

                // Keep our flag in sync with the native maximised state, and
                // clear the snap guard once the OS confirms the transition.
                stage.maximizedProperty().addListener((obsMax, wasMax, isNowMax) -> {
                    isMaximised = isNowMax;
                    isSnapping  = false;   // OS has acknowledged — safe to snap again
                });

                initSavedBounds(stage);
                bindDragHandlers(stage);
            });
        });

        /**
         * ####################################################################
         * ######################### MENU BTN EXIT ############################
         * ####################################################################
         */

        // Exit menu item handler
        if (mnu_btn_exit != null)
        {
            mnu_btn_exit.setOnAction(e -> {
                logger.info("[Lensora] Exit menu item clicked, forwarding close request...");
                
                // Get the window instance safely
                var window = headerBar.getScene().getWindow();
                if (window != null) {
                    // Fire a formal close request to simulate the user clicking the OS close button
                    window.fireEvent(new javafx.stage.WindowEvent(window, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                }
            });
        }

        
        /**
         * ####################################################################
         * ######################### MENU BTN PREFERENCES #####################
         * ####################################################################
         */

        // Preferences menu item handler
        if (mnu_btn_preferences != null)
        {
            mnu_btn_preferences.setOnAction(e -> showPreferencesWindow());
        }

        /**
         * ####################################################################
         * ######################### ABOUT BTN  ###############################
         * ####################################################################
         */

        // Preferences menu item handler
        if (mnu_btn_about != null)
        {
            mnu_btn_about.setOnAction(e -> showAboutWindow());
        }


        /**
         * ####################################################################
         * ######################### KEYBOARD SHORTCUTS  ######################
         * ####################################################################
         */

        // Preferences: Ctrl + N
        mnu_btn_preferences.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));

        // Exit: Ctrl + Q (or Ctrl+W, Alt+F4 – choose common)
        mnu_btn_exit.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));

        // About: Ctrl + Shift + A
        mnu_btn_about.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));



        /**
         * ####################################################################
         * ############  REGISTER SPLITPANES FOR LAYOUT SAVING   ##############
         * ####################################################################
         */

        LayoutPersistence.bindSplitPane("main.horizontal", mainSplitPane);
        LayoutPersistence.bindSplitPane("detail.vertical", projectWorkspace);
        LayoutPersistence.bindSplitPane("file.horizontal", fileSplitPane);
    }

    /**
     * #######################################################################
     * #######################################################################
     * ######################### TITLE BAR CONTROLS ##########################
     * #######################################################################
     * #######################################################################
     */

    /**
     * Seeds {@code savedWidth/Height/X/Y} from the primary screen so the
     * window always has a sensible restored size before the user has
     * manually resized or moved it.
     */
    private void initSavedBounds(Stage stage)
    {
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        savedWidth  = screen.getWidth()  * WINDOW_SCALE;
        savedHeight = screen.getHeight() * WINDOW_SCALE;
        savedX      = (screen.getWidth()  - savedWidth)  / 2.0;
        savedY      = (screen.getHeight() - savedHeight) / 2.0;
    }

    // ─── Drag Handlers ────────────────────────────────────────────────────────

    /**
     * Attaches all mouse-event handlers to {@code headerBar} for borderless
     * window dragging, snap-maximise, and double-click toggle.
     */
    private void bindDragHandlers(Stage stage)
    {
        headerBar.setOnMousePressed(e  -> onPressed(e, stage));
        headerBar.setOnMouseDragged(e  -> onDragged(e, stage));
        headerBar.setOnMouseReleased(e -> onReleased(e, stage));
        headerBar.setOnMouseClicked(e  -> onClicked(e, stage));
    }

    private void onPressed(MouseEvent e, Stage stage)
    {
        if (!e.isPrimaryButtonDown() || isInteractiveControl(e)) return;

        if (isMaximised)
        {
            // dragOffsetX will be re-anchored to savedWidth/2 the moment a
            // drag is confirmed in onDragged.  dragOffsetY is intentionally
            // NOT captured here because the scene-Y coordinate is invalid the
            // instant the window un-maximises (DPI/border recalculation).
            // It is set to RESTORED_Y_ANCHOR at restore time instead.
            dragOffsetX = savedWidth / 2.0;
            dragOffsetY = RESTORED_Y_ANCHOR;
        }
        else
        {
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();

            // Cache current bounds so we can restore them later
            savedWidth  = stage.getWidth();
            savedHeight = stage.getHeight();
            savedX      = stage.getX();
            savedY      = stage.getY();
        }
    }


    private void onDragged(MouseEvent e, Stage stage)
    {
        if (!e.isPrimaryButtonDown() || isInteractiveControl(e)) return;
 
        Rectangle2D screen = screenFor(e.getScreenX(), e.getScreenY());
 
        // ── Pull-down restore ──────────────────────────────────────────────
        if (isMaximised && e.getScreenY() > screen.getMinY() + RESTORE_THRESHOLD)
        {
            // Map the cursor's horizontal position proportionally from the
            // maximised width to the restored width, so the window appears
            // directly under where the user was holding it — matching native
            // Windows title-bar drag-restore behaviour.
            double proportionalX = e.getSceneX() / stage.getWidth();
            dragOffsetX = savedWidth * proportionalX;
 
            // Y uses a fixed anchor: scene-Y is invalid the instant the window
            // un-maximises due to DPI/border recalculation.
            dragOffsetY = RESTORED_Y_ANCHOR;
 
            restoreToNormal(stage);
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
            return;
        }
 
        // ── Top-edge snap ──────────────────────────────────────────────────
        // isSnapping guards against re-entry on platforms where
        // maximizedProperty fires asynchronously (Wayland, macOS).
        if (!isMaximised && !isSnapping && e.getScreenY() <= screen.getMinY() + SNAP_THRESHOLD)
        {
            snapToMaximise(stage, screen);
            return;
        }
 
        // ── Free movement ──────────────────────────────────────────────────
        if (!isMaximised && !isSnapping)
        {
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        }
    }


    /** Cleans up any transient drag state when the primary button is released. */
    private void onReleased(MouseEvent e, Stage stage)
    {
        if (e.getButton() != MouseButton.PRIMARY) return;
        dragOffsetX = 0;
        dragOffsetY = 0;
    }

    private void onClicked(MouseEvent e, Stage stage)
    {
        if (e.getClickCount() != 2
                || e.getButton() != MouseButton.PRIMARY
                || isInteractiveControl(e)) return;

        if (isMaximised)
        {
            restoreToNormal(stage);
        }
        else
        {
            Rectangle2D screen = screenFor(e.getScreenX(), e.getScreenY());
            snapToMaximise(stage, screen);
        }
    }

    // ─── Window State Transitions ─────────────────────────────────────────────

    /**
     * Expands the window to fill {@code screen} and marks it as maximised.
     *
     * <p>Sets {@code isSnapping = true} immediately so that subsequent
     * {@code onDragged} events (which can fire before the OS confirms the
     * maximised state) do not re-enter this method and corrupt
     * {@code savedWidth/savedHeight}.  The flag is cleared by the
     * {@code maximizedProperty} listener once the OS acknowledges the change.
     */
    private void snapToMaximise(Stage stage, Rectangle2D screen)
    {
        // Persist bounds only if transitioning from normal → maximised
        if (!isMaximised)
        {
            savedWidth  = stage.getWidth();
            savedHeight = stage.getHeight();
            savedX      = stage.getX();
            savedY      = stage.getY();
        }

        isSnapping  = true;   // block re-entry before the async listener fires

        stage.setX(screen.getMinX());
        stage.setY(screen.getMinY());
        stage.setWidth(screen.getWidth());
        stage.setHeight(screen.getHeight());
        isMaximised = true;
        stage.setMaximized(true);
    }

    /** Restores the window to the last saved bounds. */
    private void restoreToNormal(Stage stage)
    {
        isMaximised = false;
        isSnapping  = false;
        stage.setMaximized(false);
        stage.setWidth(savedWidth);
        stage.setHeight(savedHeight);
        stage.setX(savedX);
        stage.setY(savedY);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Returns the {@link Rectangle2D} visual bounds of the screen containing
     * the given point, falling back to the primary screen if none is found.
     */
    private static Rectangle2D screenFor(double screenX, double screenY)
    {
        var screens = Screen.getScreensForRectangle(screenX, screenY, 1, 1);
        Screen target = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
        return target.getVisualBounds();
    }

    /**
     * Returns {@code true} when the event originates from an interactive
     * control (menus, buttons, text fields) that should not trigger window
     * drag behaviour.
     */
    private static boolean isInteractiveControl(MouseEvent e)
    {
        if (!(e.getTarget() instanceof javafx.scene.Node node)) return false;

        javafx.scene.Node current = node;
        while (current != null)
        {
            if (current instanceof javafx.scene.control.MenuButton
                    || current instanceof javafx.scene.control.Button
                    || current instanceof javafx.scene.control.TextField)
            {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }

    /**
     * #######################################################################
     * #######################################################################
     * ######################### PREFERENCES WINDOW ##########################
     * #######################################################################
     * #######################################################################
     */

    private void showPreferencesWindow()
    {
        Stage mainStage = (Stage) headerBar.getScene().getWindow();
        DialogBuilder.of( Resources.SETTINGS_VIEW.url(), "Preferences", mainStage)
            .resizable(false)
            .build();
    }

    /**
     * #######################################################################
     * #######################################################################
     * ########################### About WINDOW ##############################
     * #######################################################################
     * #######################################################################
     */
    private void showAboutWindow()
    {
        Stage mainStage = (Stage) headerBar.getScene().getWindow();
        DialogBuilder.of( Resources.ABOUT_VIEW.url(), "About Lensora Studio", mainStage)
            .resizable(false)
            .build();
    }
}