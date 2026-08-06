package com.lensora.lensorastudio.util;

import com.lensora.lensorastudio.model.MediaMetadata;
import com.lensora.lensorastudio.services.AppSettings;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
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

                // Format entry text as "Key: Value" for clipboard copying
                String copyText = entry.getKey() + ": " + entry.getValue();
                makeCopyable(property, copyText);
                makeCopyable(value, copyText);
                
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

        int previewSize = AppSettings.getInstance().getMetadataPreviewSize();
        Image cachedImage = ImageCache.getOrLoad(file, previewSize, 0);
        imageView.setImage(cachedImage);

        // Force the StackPane to track the ScrollPane's viewport width exactly
        // - this breaks the circular sizing dependency (pane sized by content,
        // content sized by pane) that prevents shrinking. minWidth(0) is
        // essential: without it, Region defaults minWidth to prefWidth, which
        // is exactly what was blocking shrink-below-content-size before.
        pane.minWidthProperty().set(0);
        pane.prefWidthProperty().bind(scrollPane.viewportBoundsProperty()
                .map(b -> b.getWidth()));

        imageView.fitWidthProperty().bind(Bindings.createDoubleBinding(
                () -> {
                    double insetLeft = pane.getInsets().getLeft();
                    double insetRight = pane.getInsets().getRight();
                    double viewportWidth = scrollPane.getViewportBounds() != null
                            ? scrollPane.getViewportBounds().getWidth()
                            : scrollPane.getWidth();
                    double available = viewportWidth - (insetLeft + insetRight + 20);  // added 20 as the padding
                    return Math.max(50, available);
                },
                scrollPane.viewportBoundsProperty()
        ));

        return pane;
    }

    private static void makeCopyable(Label label, String textToCopy)
    {
        label.setCursor(Cursor.HAND);
        Tooltip tooltip = new Tooltip("Click to copy");
        label.setTooltip(tooltip);

        label.setOnMouseClicked(event -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(textToCopy);
            clipboard.setContent(content);

            // Show floating toast notification
            NotificationUtil.showToast(label, "Copied to clipboard");
        });
    }
}