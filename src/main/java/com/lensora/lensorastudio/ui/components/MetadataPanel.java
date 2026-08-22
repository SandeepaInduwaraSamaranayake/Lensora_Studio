package com.lensora.lensorastudio.ui.components;

import com.lensora.lensorastudio.core.config.AppSettings;
import com.lensora.lensorastudio.media.cache.ImageCache;
import com.lensora.lensorastudio.media.metadata.MediaMetadata;
import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
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

    private static Double cachedVerticalScrollbarWidth = null;
    private static final Tooltip SHARED_COPY_TOOLTIP = new Tooltip("Click to copy");

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

        pane.minWidthProperty().set(0);

        imageView.fitWidthProperty().bind(Bindings.createDoubleBinding(() -> {
                    double insetLeft = pane.getInsets().getLeft();
                    double insetRight = pane.getInsets().getRight();
                    double scrollbarWidth = getVerticalScrollbarWidth(scrollPane); // measured once, cached - not reactive

                    double available = scrollPane.getWidth() - (insetLeft + insetRight + scrollbarWidth + 20);
                    return Math.max(50, available);
                },
                scrollPane.widthProperty()
        ));

        return pane;
    }

    /**
     * Measures the ScrollPane skin's actual vertical scrollbar width via CSS
     * lookup, instead of hardcoding a platform-specific guess. This is
     * intentionally NOT a reactive binding — it's read once (and cached
     * statically, since scrollbar width is a theme/platform constant, not
     * something that varies per-instance) — so it can never participate in
     * the same layout feedback loop that viewportBounds caused.
     */
    private static double getVerticalScrollbarWidth(ScrollPane scrollPane)
    {
        if (cachedVerticalScrollbarWidth != null) 
        {
            return cachedVerticalScrollbarWidth;
        }

        // Guard: if the ScrollPane isn't in a Scene yet, CSS lookup won't work.
        if (scrollPane.getScene() == null) 
        {
            return 16.0; // fallback, not cached
        }

        // Force CSS to be applied (one-time cost, safe off the layout pulse)
        scrollPane.applyCss();

        // The scrollbar Node only exists in the scene graph after the skin
        // has been applied (i.e., after this ScrollPane has had at least one
        // layout/CSS pass). If it's not available yet, fall back to a
        // conservative estimate for THIS call only - nothing is cached yet,
        // so the next call will look it up fresh once the skin is ready.
        Node vbar = scrollPane.lookup(".scroll-bar:vertical");
        if (vbar instanceof Region region) 
        {
            double w = region.getWidth();
            if (w <= 0) 
            {
                // The skin knows its intrinsic width even when hidden
                w = region.prefWidth(-1);
            }
            if (w > 0) 
            {
                cachedVerticalScrollbarWidth = w;
                return w;
            }
        }

        // Temporary fallback – not cached
        return 16.0;
    }

    private static void makeCopyable(Label label, String textToCopy)
    {
        label.setCursor(Cursor.HAND);
        label.setTooltip(SHARED_COPY_TOOLTIP);

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