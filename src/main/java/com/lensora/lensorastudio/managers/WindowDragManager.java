package com.lensora.lensorastudio.managers;

import org.slf4j.LoggerFactory;

import com.lensora.lensorastudio.controller.MainController;

import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Attaches borderless-window drag behaviour to any JavaFX node used as a
 * title bar, replicating the feel of native window chrome including:
 * <ul>
 *   <li>Free drag to move the window</li>
 *   <li>Drag to top edge → snap-maximise</li>
 *   <li>Pull-down from maximised → restore with proportional X anchor</li>
 *   <li>Double-click → toggle maximise on the correct monitor</li>
 *   <li>Aero Snap compatibility (Windows) via {@code AeroSnapHelper}</li>
 * </ul>
 *
 * <h3>Root cause of the NPE that was reported</h3>
 * The previous implementation resolved {@code stage} in the constructor by
 * calling {@code node.getScene().getWindow()} immediately. When
 * {@code WindowDragManager} is instantiated inside a
 * {@code sceneProperty} listener, the scene has just been attached to the
 * node tree but {@code scene.getWindow()} is still {@code null} because
 * {@code stage.setScene(scene)} hasn't returned yet — the window property
 * is set in a subsequent step. The fix defers stage resolution to a nested
 * {@code windowProperty} listener, which fires only after the stage has
 * been assigned.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // In any controller's initialize():
 * WindowDragManager.attach(headerBarNode);
 * }</pre>
 */
public class WindowDragManager
{
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // ─── Snap / restore thresholds ────────────────────────────────────────────

    /** Pixels from the screen top edge that trigger snap-maximise on drag. */
    private static final double SNAP_THRESHOLD    = 4.0;

    /**
     * Pixels the cursor must travel below the top edge before a
     * pull-down restore is initiated.
     */
    private static final double RESTORE_THRESHOLD = 15.0;

    private static final double WINDOW_SCALE = 0.70;

    /**
     * Fixed Y offset used when placing the restored window under the cursor
     * after a pull-down. A stable constant avoids the DPI/border
     * recalculation warp that {@code e.getSceneY()} would produce the
     * instant the window un-maximises.
     */
    private static final double RESTORED_Y_ANCHOR = 15.0;

    // ─── State ────────────────────────────────────────────────────────────────

    private final Node  titleBarNode;

    /** Resolved lazily via windowProperty listener — never accessed before that. */
    private Stage  stage;

    /** Fraction of the primary screen used for the restored window size. */
    private double savedWidth, savedHeight, savedX, savedY;

    /**
     * Offset between the cursor's scene-local position and the window origin
     * at the moment a drag begins.
     */
    private double dragOffsetX, dragOffsetY;

    /** Whether the window is currently in the maximised state. */
    private boolean isMaximised = false;

    /**
     * True while a snap-maximise is in flight. Blocks re-entry on platforms
     * where {@code maximizedProperty} fires asynchronously (Wayland, macOS).
     */
    private boolean isSnapping = false;

    // ─── Constructor ─────────────────────────────────────────────────────────

    /**
     * Creates a manager and immediately wires scene/window listeners so that
     * drag handlers are attached as soon as the node is added to a showing
     * window. Safe to call from {@code initialize()} before the stage exists.
     *
     * @param titleBarNode The node that acts as the draggable title bar.
     */
    public WindowDragManager(Node titleBarNode)
    {
        this.titleBarNode = titleBarNode;
        wireSceneListener();
    }

    /**
     * Convenience factory — same as {@code new WindowDragManager(node)}.
     *
     * @param titleBarNode The node to use as the draggable title bar.
     * @return A new {@link WindowDragManager} already wired to the node.
     */
    public static WindowDragManager attach(Node titleBarNode)
    {
        return new WindowDragManager(titleBarNode);
    }

    // ─── Wiring ──────────────────────────────────────────────────────────────

    /**
     * Listens for the node's scene to become available, then delegates to
     * {@link #wireWindowListener(javafx.scene.Scene)}.
     */
    private void wireSceneListener()
    {
        titleBarNode.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
            if (newScene == null)
            {
                logger.info("[Lensora] Initializing native window style handlers...");
                return;
            }
            wireWindowListener(newScene);
        });

        // Handle the case where the node is already in a scene when this is called
        if (titleBarNode.getScene() != null)
            wireWindowListener(titleBarNode.getScene());
    }

    /**
     * Listens for the scene's window to become available, then calls
     * {@link #bind(Stage)}.
     *
     * <p>This is the critical deferred step that fixes the NPE: at the time
     * the {@code sceneProperty} listener fires, {@code scene.getWindow()}
     * is still {@code null}. We must wait for {@code windowProperty} to fire
     * before we can safely reference the stage.
     *
     * @param scene The scene that has just been assigned to the node.
     */
    private void wireWindowListener(javafx.scene.Scene scene)
    {
        scene.windowProperty().addListener((obsWindow, oldWindow, newWindow) -> {
            if (!(newWindow instanceof Stage s)) return;
            bind(s);
        });

        // Handle the case where the scene is already attached to a window
        if (scene.getWindow() instanceof Stage s)
            bind(s);
    }

    /**
     * Attaches all mouse event handlers to {@code titleBarNode} and wires the
     * maximised-state listener. Called exactly once, after the stage is known.
     *
     * @param resolvedStage The stage that owns this title bar's scene.
     */
    private void bind(Stage resolvedStage)
    {
        // Guard against being called more than once (e.g. if both the
        // sceneProperty shortcut and the listener fire for the same stage)
        if (this.stage != null) return;

        this.stage = resolvedStage;

        // Sync our flag with whatever state the stage starts in
        isMaximised = stage.isMaximized();

        // Keep flag in sync; clear isSnapping once the OS confirms the state
         // clear the snap guard once the OS confirms the transition.
        stage.maximizedProperty().addListener((obs, wasMax, isNowMax) -> {
            isMaximised = isNowMax;
            isSnapping  = false;
        });


        // Seeds {@code savedWidth/Height/X/Y} from the primary screen so the
        // window always has a sensible restored size before the user has
        // manually resized or moved it.
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        savedWidth  = screen.getWidth()  * WINDOW_SCALE;
        savedHeight = screen.getHeight() * WINDOW_SCALE;
        savedX      = (screen.getWidth()  - savedWidth)  / 2.0;
        savedY      = (screen.getHeight() - savedHeight) / 2.0;

        //Attaches all mouse-event handlers to {@code dragNode} for borderless
        //window dragging, snap-maximise, and double-click toggle.
        titleBarNode.setOnMousePressed(this::onPressed);
        titleBarNode.setOnMouseDragged(this::onDragged);
        titleBarNode.setOnMouseReleased(this::onReleased);
        titleBarNode.setOnMouseClicked(this::onClicked);
    }

    // ─── Mouse event handlers ─────────────────────────────────────────────────

    private void onPressed(MouseEvent e)
    {
        if (!e.isPrimaryButtonDown() || isInteractiveControl(e)) return;

        if (isMaximised)
        {
            // Offsets are computed proportionally in onDragged at restore time
            dragOffsetX = 0;
            dragOffsetY = 0;
        }
        else
        {
            dragOffsetX = e.getSceneX();
            dragOffsetY = e.getSceneY();

            // Cache current geometry so we can restore it later
            savedWidth  = stage.getWidth();
            savedHeight = stage.getHeight();
            savedX      = stage.getX();
            savedY      = stage.getY();
        }
    }

    private void onDragged(MouseEvent e)
    {
        if (!e.isPrimaryButtonDown() || isInteractiveControl(e)) return;

        Rectangle2D screen = screenFor(e.getScreenX(), e.getScreenY());

        // ── Pull-down restore ──────────────────────────────────────────────
        if (isMaximised && e.getScreenY() > screen.getMinY() + RESTORE_THRESHOLD)
        {
            // Proportional X anchor: preserves the cursor's relative position
            // across the title bar instead of always snapping to the centre
            dragOffsetX = savedWidth * (e.getSceneX() / stage.getWidth());
            dragOffsetY = RESTORED_Y_ANCHOR;

            restoreToNormal();
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
            return;
        }

        // ── Top-edge snap ──────────────────────────────────────────────────
        if (!isMaximised && !isSnapping && e.getScreenY() <= screen.getMinY() + SNAP_THRESHOLD)
        {
            snapToMaximise(screen);
            return;
        }

        // ── Free movement ──────────────────────────────────────────────────
        if (!isMaximised && !isSnapping)
        {
            stage.setX(e.getScreenX() - dragOffsetX);
            stage.setY(e.getScreenY() - dragOffsetY);
        }
    }

    private void onReleased(MouseEvent e)
    {
        if (e.getButton() == MouseButton.PRIMARY)
        {
            dragOffsetX = 0;
            dragOffsetY = 0;
        }
    }

    private void onClicked(MouseEvent e)
    {
        if (e.getClickCount() != 2
                || e.getButton() != MouseButton.PRIMARY
                || isInteractiveControl(e)) return;

        if (isMaximised)
            restoreToNormal();
        else
            // Use cursor position so double-click maximises on the correct
            // monitor when the window spans two screens
            snapToMaximise(screenFor(e.getScreenX(), e.getScreenY()));
    }

    // ─── Window state transitions ─────────────────────────────────────────────

    /**
     * Expands the window to fill {@code screen} and marks it as maximised.
     *
     * <p>Sets {@code isSnapping = true} immediately so that subsequent
     * {@code onDragged} events (which can fire before the OS confirms the
     * maximised state) do not re-enter this method and corrupt
     * {@code savedWidth/savedHeight}.  The flag is cleared by the
     * {@code maximizedProperty} listener once the OS acknowledges the change.
     */
    private void snapToMaximise(Rectangle2D screen)
    {
        if (!isMaximised)
        {
            savedWidth  = stage.getWidth();
            savedHeight = stage.getHeight();
            savedX      = stage.getX();
            savedY      = stage.getY();
        }

        // block re-entry before the async listener fires
        isSnapping = true;

        stage.setX(screen.getMinX());
        stage.setY(screen.getMinY());
        stage.setWidth(screen.getWidth());
        stage.setHeight(screen.getHeight());
        isMaximised = true;
        stage.setMaximized(true);
    }

    /** Restores the window to the last saved bounds. */
    private void restoreToNormal()
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
     * Returns the visual bounds of the screen containing the given point,
     * falling back to the primary screen if no screen contains it.
     */
    private static Rectangle2D screenFor(double screenX, double screenY)
    {
        var screens = Screen.getScreensForRectangle(screenX, screenY, 1, 1);
        return (screens.isEmpty() ? Screen.getPrimary() : screens.get(0))
                .getVisualBounds();
    }

    /**
     * Returns {@code true} when the event target is an interactive control
     * (button, text field, menu button) that should consume its own clicks
     * rather than triggering window drag behaviour.
     *
     * Walks the scene-graph node tree upward so controls nested inside
     * layout containers are correctly detected.
     */
    private static boolean isInteractiveControl(MouseEvent e)
    {
        if (!(e.getTarget() instanceof Node node)) return false;
        Node current = node;
        while (current != null)
        {
            if (current instanceof Button
                    || current instanceof MenuButton
                    || current instanceof TextField)
                return true;
            current = current.getParent();
        }
        return false;
    }
}