package com.lensora.lensorastudio.feature.viewer;

import com.lensora.lensorastudio.core.config.AppSettings;
import com.lensora.lensorastudio.core.config.Resources;
import com.lensora.lensorastudio.core.config.ThemeManager;
import com.lensora.lensorastudio.media.service.ImageValidator;
import com.lensora.lensorastudio.ui.util.AppIconUtil;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snapfx.DockUserAgentThemeMode;
import org.snapfx.SnapFX;
import org.snapfx.model.DockElement;
import org.snapfx.model.DockNode;
import org.snapfx.model.DockPosition;
import org.snapfx.model.DockSplitPane;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Lazily-created secondary top-level window dedicated to viewing images
 * side by side using SnapFX docking layout.
 */
public final class ImageViewerWindowService
{
    private static final Logger logger = LoggerFactory.getLogger(ImageViewerWindowService.class);
    private static ImageViewerWindowService instance;

    private Stage stage;
    private SnapFX snapFX;

    private record ViewerInstance(DockNode dockNode, ImageViewerNode viewerNode) {}
    private final List<ViewerInstance> viewers = new ArrayList<>();

    private DockNode lastDockedNode;
    private Parent currentLayoutNode;

    // Maximization overlay state
    private ImageViewerNode maximizedViewer;
    private Pane originalParent;
    private int originalIndex = -1;
    private StackPane overlayPane;

    private static final int MAX_OPENED_IMAGES = 10;

    private final Consumer<AppSettings.Theme> themeListener = this::syncTheme;

    private ImageViewerWindowService() {}

    public static synchronized ImageViewerWindowService getInstance()
    {
        if (instance == null) instance = new ImageViewerWindowService();
        return instance;
    }

    public boolean isOpen()
    {
        return stage != null && stage.isShowing();
    }

    /** Opens one or more image files in the viewer window, creating the window if needed. */
    public void openImages(List<File> files)
    {
        if (files == null || files.isEmpty()) return;
        if (files.size() > MAX_OPENED_IMAGES) 
        {
            logger.warn("[ImageViewerWindowService] File open request detected for excessive number of files. Granting open request for maximum number of :" + MAX_OPENED_IMAGES);
            files = files.subList(0, MAX_OPENED_IMAGES);
            logger.info("[ImageViewerWindowService] File open request granted for only first " + files.size() +  " files");
        }

        List<File> imagesOnly = files.stream()
                .filter(ImageValidator::isJavaFXLoadable)
                .toList();

        if (imagesOnly.isEmpty())
        {
            logger.info("[ImageViewerWindowService] No supported image files in selection - nothing to open.");
            return;
        }

        if (!isOpen())
        {
            createWindow();
        }

        for (File file : imagesOnly)
        {
            addImage(file);
        }

        equalizeDividers();
        refreshLayout();

        stage.toFront();
        stage.requestFocus();
    }

    /** Maximizes a single ImageViewerNode within the window host pane. */
    public void toggleMaximize(ImageViewerNode viewer)
    {
        if (viewer == null || stage == null || stage.getScene() == null) return;

        if (maximizedViewer == viewer)
        {
            restoreMaximized();
        }
        else
        {
            if (maximizedViewer != null)
            {
                restoreMaximized();
            }

            Node contentNode = viewer.getNode();
            if (contentNode.getParent() instanceof Pane parent)
            {
                originalParent = parent;
                originalIndex = parent.getChildren().indexOf(contentNode);

                if (overlayPane == null)
                {
                    overlayPane = new StackPane();
                    overlayPane.getStyleClass().add("image-viewer-overlay");
                }

                overlayPane.getChildren().setAll(contentNode);

                StackPane host = (StackPane) stage.getScene().getRoot();
                if (!host.getChildren().contains(overlayPane))
                {
                    host.getChildren().add(overlayPane);
                }

                maximizedViewer = viewer;
                viewer.setMaximizedState(true);
            }
        }
    }

    /** Restores the currently maximized viewer node back to its original dock position. */
    public void restoreMaximized()
    {
        if (maximizedViewer == null || stage == null || stage.getScene() == null) return;

        StackPane host = (StackPane) stage.getScene().getRoot();
        Node contentNode = maximizedViewer.getNode();

        if (overlayPane != null)
        {
            overlayPane.getChildren().clear();
            host.getChildren().remove(overlayPane);
        }

        if (originalParent != null)
        {
            if (originalIndex >= 0 && originalIndex <= originalParent.getChildren().size())
            {
                originalParent.getChildren().add(originalIndex, contentNode);
            }
            else
            {
                originalParent.getChildren().add(contentNode);
            }
            originalParent.requestLayout();
        }

        maximizedViewer.setMaximizedState(false);
        maximizedViewer = null;
        originalParent = null;
        originalIndex = -1;
    }

    /**
     * Recursively verifies if a specific DockNode exists anywhere inside 
     * the active SnapFX dock graph tree.
     */
    private boolean isNodeInGraph(DockNode target, DockElement current)
    {
        if (current == null || target == null) return false;
        if (current == target) return true;
        
        if (current instanceof DockSplitPane splitPane)
        {
            for (DockElement child : splitPane.getChildren())
            {
                if (isNodeInGraph(target, child)) return true;
            }
        }
        return false;
    }

    /**
     * Recursively traverses the dock graph to locate the first active leaf DockNode.
     */
    private DockNode findFirstDockNode(DockElement element)
    {
        if (element == null) return null;
        if (element instanceof DockNode node)
        {
            return node;
        }
        if (element instanceof DockSplitPane splitPane)
        {
            for (DockElement child : splitPane.getChildren())
            {
                DockNode found = findFirstDockNode(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Determines a guaranteed-valid docking target by inspecting the graph directly.
     */
    private DockNode findDockTarget()
    {
        if (snapFX == null || snapFX.getDockGraph() == null) return null;
        DockElement root = snapFX.getDockGraph().getRoot();
        if (root == null) return null;

        // Prefer lastDockedNode if it is still present in the dock graph
        if (lastDockedNode != null && isNodeInGraph(lastDockedNode, root))
        {
            return lastDockedNode;
        }

        // Otherwise, locate any active node directly from the dock tree structure
        return findFirstDockNode(root);
    }

    private void addImage(File file)
    {
        ImageViewerNode viewerNode = new ImageViewerNode(file);

        DockNode dockNode = new DockNode(
                "imgviewer-" + UUID.randomUUID(),
                viewerNode.getNode(),
                file.getName()
        );
        dockNode.setCloseable(true);

        // Let the Service handle state synchronization when the viewer node changes its image
        viewerNode.currentFileProperty().addListener((obs, oldFile, newFile) -> {
            if (newFile != null) dockNode.setTitle(newFile.getName());
        });

        DockNode target = findDockTarget();
        if (target == null)
        {
            snapFX.getDockGraph().setRoot(dockNode);
        }
        else
        {
            snapFX.dock(dockNode, target, DockPosition.RIGHT);
        }

        viewers.add(new ViewerInstance(dockNode, viewerNode));
        lastDockedNode = dockNode;
    }

    private void refreshLayout()
    {
        if (stage == null || stage.getScene() == null) return;
        
        // Ensure any active maximization overlay is restored before rebuilding dock tree
        if (maximizedViewer != null)
        {
            restoreMaximized();
        }

        StackPane host = (StackPane) stage.getScene().getRoot();
        Parent newLayout = snapFX.buildLayout();

        if (currentLayoutNode instanceof Region oldRegion)
        {
            oldRegion.prefWidthProperty().unbind();
            oldRegion.prefHeightProperty().unbind();
        }

        if (newLayout instanceof Region region)
        {
            region.prefWidthProperty().bind(host.widthProperty());
            region.prefHeightProperty().bind(host.heightProperty());
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
        }

        if (currentLayoutNode != null && host.getChildren().contains(currentLayoutNode))
        {
            int idx = host.getChildren().indexOf(currentLayoutNode);
            host.getChildren().set(idx, newLayout);
        }
        else
        {
            // First build, or the previous node was already removed elsewhere -
            // insert at index 0 so any overlay panes SnapFX manages
            // independently (added during initialize()) stay on top/preserved
            // as later siblings instead of being wiped by a setAll() call.
            host.getChildren().add(0, newLayout);
        }

        currentLayoutNode = newLayout;
        host.requestLayout();
        Platform.runLater(host::requestLayout);
    }

    /**
     * If the root is a horizontal DockSplitPane containing all images,
     * set its dividers so that each child gets an equal share of the width.
     */
    private void equalizeDividers() 
    {
        DockElement root = snapFX.getDockGraph().getRoot();
        if (!(root instanceof DockSplitPane splitPane)) return;
        if (splitPane.getOrientation() != Orientation.HORIZONTAL) return;

        int childCount = splitPane.getChildren().size();
        if (childCount < 2) return; // no dividers to set

        // We need to set divider i to (i+1)/childCount for i = 0 .. childCount-2
        for (int i = 0; i < childCount - 1; i++) 
        {
            double position = (double) (i + 1) / childCount;
            splitPane.setDividerPosition(i, position);
        }
    }

    private void createWindow()
    {
        stage = new Stage();
        stage.setTitle("Image Viewer - Lensora Studio");
        stage.setWidth(1200);
        stage.setHeight(800);

        try
        {
            AppIconUtil.setAppIconReplace(stage);
        }
        catch (Exception ignored) {}

        snapFX = new SnapFX();
        snapFX.setUserAgentThemeMode(DockUserAgentThemeMode.AUTO);
        snapFX.setDropVisualizationMode(org.snapfx.dnd.DockDropVisualizationMode.DEFAULT);

        StackPane host = new StackPane();
        host.setStyle("-fx-background-color: -color-bg-default;");
        Scene scene = new Scene(host);
        scene.setFill(Color.web("#1e1e1e"));

        // Press ESC to exit node maximization mode
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && maximizedViewer != null)
            {
                restoreMaximized();
                event.consume();
            }
        });

        setupDropTarget(host);
        stage.setScene(scene);
        ThemeManager.initializeSceneStyling(scene);

        // Load image-viewer.css on the scene
        String imageViewerCss = Resources.IMAGE_VIEWER_STYLE.url().toExternalForm();
        if (!scene.getStylesheets().contains(imageViewerCss))
        {
            scene.getStylesheets().add(imageViewerCss);
        }

        stage.setOnHidden(e -> teardown());

        snapFX.initialize(stage);

        // Run cleanup
        snapFX.setOnCloseHandled(result -> Platform.runLater(() -> {
            if (snapFX == null || snapFX.getDockGraph() == null) return;
            
            DockElement root = snapFX.getDockGraph().getRoot();

            // Dispose viewer nodes that were closed and removed from the dock graph
            viewers.stream()
                    .filter(instance -> !isNodeInGraph(instance.dockNode(), root))
                    .forEach(instance -> instance.viewerNode().dispose());

            // Remove closed instances from the tracking list
            viewers.removeIf(instance -> !isNodeInGraph(instance.dockNode(), root));
            lastDockedNode = viewers.isEmpty() ? null : viewers.get(viewers.size() - 1).dockNode();

            if (root == null || viewers.isEmpty()) 
            {
                stage.close();
            } 
            else
            {
                equalizeDividers();
                refreshLayout();
            }
        }));

        ThemeManager.addThemeChangeListener(themeListener);
        syncTheme(AppSettings.getInstance().getTheme());
        
        stage.show();
    }

    private void syncTheme(AppSettings.Theme theme)
    {
        if (snapFX == null) return;
        snapFX.setUserAgentThemeMode(theme.atlantaFxBased
                ? DockUserAgentThemeMode.ATLANTAFX_COMPAT
                : DockUserAgentThemeMode.MODENA);
        snapFX.refreshUserAgentThemeIntegration();
    }

    private void setupDropTarget(StackPane host)
    {
        host.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles())
            {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });

        host.setOnDragDropped(event -> {
            var db = event.getDragboard();
            boolean success = db.hasFiles();

            event.setDropCompleted(success);
            event.consume();

            if (success)
            {
                List<File> files = db.getFiles();
                Platform.runLater(() -> openImages(files));
            }
        });
    }

    private void teardown()
    {
        logger.info("[ImageViewerWindowService] Viewer window closed - resetting state.");
        ThemeManager.removeThemeChangeListener(themeListener);

        if (maximizedViewer != null)
        {
            restoreMaximized();
        }

        if (currentLayoutNode instanceof Region region)
        {
            region.prefWidthProperty().unbind();
            region.prefHeightProperty().unbind();
        }

        // Dispose all remaining viewer nodes
        for (ViewerInstance instance : viewers)
        {
            instance.viewerNode().dispose();
        }

        viewers.clear();
        lastDockedNode = null;
        currentLayoutNode = null;
        overlayPane = null;
        snapFX = null;
        stage = null;
    }
}