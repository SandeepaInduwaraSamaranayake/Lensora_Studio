package com.lensora.lensorastudio.viewer;

import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.services.ThemeManager;
import com.lensora.lensorastudio.util.AppIconUtil;
import com.lensora.lensorastudio.util.ImageMetadataExtractor;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final Map<String, DockNode> openNodes = new LinkedHashMap<>();
    private DockNode lastDockedNode;
    private Parent currentLayoutNode;

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
            logger.warn("[ImageViewerWindowService] File open request detected for excessive number of files: {}", files.size());
            files = files.subList(0, MAX_OPENED_IMAGES);
            logger.warn("[ImageViewerWindowService] Request granted for first {} files", files.size());
        }

        List<File> imagesOnly = files.stream()
                .filter(ImageMetadataExtractor::isSupportedImage)
                .toList();

        if (imagesOnly.isEmpty())
        {
            logger.info("[ImageViewerWindowService] No supported image files in selection — nothing to open.");
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
        String key = file.getAbsolutePath();
        
        ImageViewerNode viewerNode = new ImageViewerNode(file);

        DockNode node = new DockNode(
                "imgviewer-" + key.hashCode(),
                viewerNode.getNode(),
                file.getName()
        );
        node.setCloseable(true);

        // Let the Service handle state synchronization when the viewer node changes its image
        viewerNode.currentFileProperty().addListener((obs, oldFile, newFile) -> {
            onViewerImageChanged(node, oldFile, newFile);
        });

        DockNode target = findDockTarget();
        if (target == null)
        {
            snapFX.getDockGraph().setRoot(node);
        }
        else
        {
            snapFX.dock(node, target, DockPosition.RIGHT);
        }

        openNodes.put(key, node);
        lastDockedNode = node;
    }

    private void refreshLayout()
    {
        if (stage == null || stage.getScene() == null) return;
        
        StackPane host = (StackPane) stage.getScene().getRoot();
        Parent newLayout = snapFX.buildLayout();

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
    }

    /**
     * If the root is a horizontal DockSplitPane containing all images,
     * set its dividers so that each child gets an equal share of the width.
     */
    private void equalizeDividers() 
    {
        DockElement root = snapFX.getDockGraph().getRoot();
        if (!(root instanceof DockSplitPane splitPane)) return;
        if (splitPane.getOrientation() != javafx.geometry.Orientation.HORIZONTAL) return;

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

        try
        {
            AppIconUtil.setAppIconReplace(stage);
        }
        catch (Exception ignored) {}

        snapFX = new SnapFX();
        snapFX.setUserAgentThemeMode(DockUserAgentThemeMode.AUTO);
        snapFX.setDropVisualizationMode(org.snapfx.dnd.DockDropVisualizationMode.DEFAULT);

        StackPane host = new StackPane();
        Scene scene = new Scene(host);
        setupDropTarget(host);
        stage.setScene(scene);
        ThemeManager.initializeSceneStyling(scene);

        stage.setOnHidden(e -> teardown());

        snapFX.initialize(stage);

        // Run cleanup on Platform.runLater to ensure SnapFX graph mutations complete first
        snapFX.setOnCloseHandled(result -> Platform.runLater(() -> {
            if (snapFX == null || snapFX.getDockGraph() == null) return;
            
            DockElement root = snapFX.getDockGraph().getRoot();

            // Purge nodes no longer present in the physical graph
            openNodes.entrySet().removeIf(entry -> !isNodeInGraph(entry.getValue(), root));

            lastDockedNode = findDockTarget();

            if (root == null || openNodes.isEmpty()) 
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

    /**
     * Safely updates window-level node tracking and tab titles when an image
     * is replaced inside an existing viewer node.
     */
    private void onViewerImageChanged(DockNode node, File oldFile, File newFile)
    {
        if (node == null) return;

        if (newFile != null)
        {
            node.setTitle(newFile.getName());
        }

        // Only remove the old mapping if it specifically references THIS node instance
        if (oldFile != null)
        {
            String oldKey = oldFile.getAbsolutePath();
            if (openNodes.get(oldKey) == node)
            {
                openNodes.remove(oldKey);
            }
        }

        // Map the new file to this node
        if (newFile != null)
        {
            openNodes.put(newFile.getAbsolutePath(), node);
        }
    }

    private void teardown()
    {
        logger.info("[ImageViewerWindowService] Viewer window closed — resetting state.");
        ThemeManager.removeThemeChangeListener(themeListener);
        openNodes.clear();
        lastDockedNode = null;
        currentLayoutNode = null;
        snapFX = null;
        stage = null;
    }
}