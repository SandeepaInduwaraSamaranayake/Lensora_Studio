package com.lensora.lensorastudio.viewer;

import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.services.ThemeManager;
import com.lensora.lensorastudio.util.AppIconUtil;
import com.lensora.lensorastudio.util.ImageMetadataExtractor;
import com.lensora.lensorastudio.util.Resources;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
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
 * side by side. Does not exist until the first image is opened into it;
 * once created it stays open and accepts more images (context menu or
 * drag-and-drop) until the user closes the window, at which point it's
 * torn down — the next open request creates a fresh one.
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

    private final int MAXOPENEDIMAGES = 10;

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
        if (files.size() > MAXOPENEDIMAGES) 
        {
            logger.warn("File open request detected for excessive number of files :" + files.size());
            files = files.subList(0, MAXOPENEDIMAGES);
            logger.warn("Request granted for first" + files.size() + " files");
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

        stage.toFront();
        stage.requestFocus();
    }

    private void addImage(File file)
    {
        String key = file.getAbsolutePath();

        if (openNodes.containsKey(key))
        {
            return; // already open — nothing further to do
        }

        ImageViewerNode viewerNode = new ImageViewerNode(file);

        DockNode node = new DockNode(
                "imgviewer-" + key.hashCode(),
                viewerNode.getNode(),
                file.getName()
        );
        node.setCloseable(true);

        // Keep the dock tab/header title in sync when the user navigates
        // to a different image via the </> buttons inside this same node.
        viewerNode.currentFileProperty().addListener((obs, old, newFile) -> {
            if (newFile != null) node.setTitle(newFile.getName());
        });

        if (lastDockedNode == null)
        {
            snapFX.getDockGraph().setRoot(node);
        }
        else
        {
            // Side-by-side: each new image docks to the right of the
            // previously docked one, building a horizontal split chain
            // so images compare side by side instead of stacking as tabs.
            snapFX.dock(node, lastDockedNode, DockPosition.RIGHT);
        }

        openNodes.put(key, node);
        lastDockedNode = node;

        // --- Equalize the split pane dividers ---
        equalizeDividers();
        refreshLayout();
    }

    private void refreshLayout()
    {
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

        stage.setOnHidden(e -> teardown());

        snapFX.initialize(stage);

        // ─── Close handler ──────────────────────────────────────────
        snapFX.setOnCloseHandled(result -> {
            // If the graph root is null, close the window
            if (snapFX.getDockGraph().getRoot() == null) 
            {
                Platform.runLater(() -> stage.close());
            } 
            else
            {
                Platform.runLater(this::refreshLayout);
            }
        });

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

            // Complete the native DnD loop first, exactly as established
            // for the main file explorer's drop targets — never do
            // follow-up work synchronously inside a drop handler.
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
        logger.info("[ImageViewerWindowService] Viewer window closed — resetting state.");
        ThemeManager.removeThemeChangeListener(themeListener);
        openNodes.clear();
        lastDockedNode = null;
        currentLayoutNode = null;
        snapFX = null;
        stage = null;
    }
}