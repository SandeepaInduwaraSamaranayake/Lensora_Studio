package com.lensora.lensorastudio.util;

import com.lensora.lensorastudio.managers.FileListingManager;
import com.lensora.lensorastudio.model.MediaMetadata;
import com.lensora.lensorastudio.services.AppSettings;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snapfx.SnapFX;
import org.snapfx.model.DockNode;

import java.io.File;
import java.util.Map;

public final class MetadataPanel
{
    private static final Logger logger = LoggerFactory.getLogger(MetadataPanel.class);

    private MetadataPanel() {}

    // ─────────────────────────────────────────────────────────────────────
    // Modal version  (kept for fallback)
    // ─────────────────────────────────────────────────────────────────────

    public static void show(Window owner, MediaMetadata metadata)
    {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Metadata - " + new File(metadata.getFilePath()).getName());

        Scene scene = new Scene(buildContent(metadata), 650, 550);
        stage.setScene(scene);
        stage.showAndWait();
    }

    // ─────────────────────────────────────────────────────────────────────
    // SnapFX floating version
    // ─────────────────────────────────────────────────────────────────────

    public static void showFloating(MediaMetadata metadata, SnapFX snapFX)
    {
        if (snapFX == null)
        {
            show(null, metadata);
            return;
        }

        DockNode dockNode = new DockNode(
                "metadata-" + System.currentTimeMillis(),
                buildContent(metadata),
                "Metadata - " + new File(metadata.getFilePath()).getName());

        dockNode.setCloseable(true);
        snapFX.floatNode(dockNode);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Shared UI
    // ─────────────────────────────────────────────────────────────────────

    public static Parent buildContent(MediaMetadata metadata)
    {
        VBox container = new VBox(8);
        container.setPadding(new Insets(10));

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);

        // Preview for images
        if (AppSettings.getInstance().getShowMetadataImagePreview() && metadata.getType() == MediaMetadata.MediaType.IMAGE) 
        {
            try 
            {
                TitledPane previewPane = new TitledPane("Preview", createPreview(new File(metadata.getFilePath()), scrollPane));
                previewPane.setExpanded(true);
                container.getChildren().add(previewPane);
            }
            catch (Exception e) 
            {
                logger.info("Image preview is not available right now. Ignoring ...");
            }
        }

        for (Map.Entry<String, Map<String, String>> group : metadata.getGroups().entrySet())
        {
            TitledPane pane = new TitledPane( group.getKey(), createPropertyGrid(group.getValue()));
            pane.setExpanded(true);
            container.getChildren().add(pane);
        }

        return scrollPane;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Property Grid
    // ─────────────────────────────────────────────────────────────────────

    private static GridPane createPropertyGrid(Map<String, String> values)
    {
        GridPane grid = new GridPane();

        grid.setHgap(12);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));

        ColumnConstraints propertyColumn = new ColumnConstraints();
        propertyColumn.setPrefWidth(220);
        propertyColumn.setMinWidth(Region.USE_PREF_SIZE);
        propertyColumn.setHgrow(Priority.NEVER);

        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(propertyColumn, valueColumn);

        int row = 0;
        int index = 0;

        for (Map.Entry<String, String> entry : values.entrySet())
        {
                Label property = new Label(entry.getKey());
                property.setStyle("-fx-font-weight: bold;");

                Label value =  new Label(entry.getValue());
                
                grid.add(property, 0, row);
                grid.add(value, 1, row);

                if (index != values.size() - 1)
                {
                        row++;
                        Separator separator = new Separator();
                        GridPane.setColumnSpan(separator, 2);
                        grid.add(separator, 0, row);
                }

                row++;
                index++;
        }

        return grid;
    }

    private static Parent createPreview(File file, ScrollPane scrollPane)
    {
        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        

        StackPane pane = new StackPane(imageView);
        pane.setPadding(new Insets(10));
        
        // Load a cached, background-loaded image sized to a sensible max -
        // actual on-screen width is controlled by the binding below, not by
        // the loaded image's native resolution.
        int previewSize = AppSettings.getInstance().getMetadataPreviewSize();
        Image cachedImage = ImageCache.getOrLoad(file, previewSize, 0);
        imageView.setImage(cachedImage);

        // Bind to the ScrollPane's actual VIEWPORT width, not the
        // StackPane's own layout width. The container VBox's width can be
        // forced wider than the viewport by sibling content (e.g. the
        // property grids' fixed-width columns), which triggers ScrollPane's
        // horizontal scrollbar instead of shrinking further. The viewport
        // itself, however, always reflects the real visible width — so
        // binding to it lets the image keep shrinking even after the
        // scrollbar appears, instead of getting stuck at the content's
        // forced minimum width.

        imageView.fitWidthProperty().bind(Bindings.createDoubleBinding(
                () -> {
                    double viewportWidth = scrollPane.getViewportBounds() != null
                            ? scrollPane.getViewportBounds().getWidth()
                            : scrollPane.getWidth();
                    return Math.max(50, viewportWidth - 40); // 40 = left+right padding
                },
                scrollPane.viewportBoundsProperty()
        ));

        return pane;
    }

}