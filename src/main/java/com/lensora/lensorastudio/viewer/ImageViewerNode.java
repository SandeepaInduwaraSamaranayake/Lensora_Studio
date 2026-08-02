package com.lensora.lensorastudio.viewer;

import com.lensora.lensorastudio.util.ImageCache;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;

/**
 * A single image viewer node with a toolbar for zoom, rotate, full-screen.
 */
public class ImageViewerNode 
{
    private final ImageView imageView;
    private final DoubleProperty zoom;
    private final DoubleProperty rotate;
    private final ScrollPane scrollPane;
    private final BorderPane root;
    private final Stage stage;

    public ImageViewerNode(File imageFile)
    {
        // ─── Image ──────────────────────────────────────────────
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        Image image = ImageCache.getOrLoad(imageFile, 1600, 0);
        imageView.setImage(image);

        // ─── Zoom & rotate properties ─────────────────────────
        zoom = new SimpleDoubleProperty(1.0);
        rotate = new SimpleDoubleProperty(0.0);
        imageView.rotateProperty().bind(rotate);

        // ─── ScrollPane ────────────────────────────────────────
        scrollPane = new ScrollPane();
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        // StackPane holds the image (with padding)
        StackPane imageHolder = new StackPane(imageView);
        imageHolder.setPadding(new Insets(10));
        imageHolder.setStyle("-fx-background-color: -color-bg-default;");
        scrollPane.setContent(imageHolder);

        // ─── Fit width binding (with zoom) ────────────────────
        imageView.fitWidthProperty().bind(Bindings.createDoubleBinding(() -> {
                            double vw = scrollPane.getViewportBounds() != null
                                    ? scrollPane.getViewportBounds().getWidth()
                                    : scrollPane.getWidth();
                            return Math.max(50, vw * zoom.get());
                        },
                        scrollPane.viewportBoundsProperty(),
                        zoom
                )
        );

        // ─── Mouse wheel zoom (Ctrl+Scroll) ──────────────────
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) 
            {
                double delta = event.getDeltaY();
                double factor = delta > 0 ? 1.1 : 1.0 / 1.1;
                double newZoom = zoom.get() * factor;
                newZoom = Math.max(0.1, Math.min(10.0, newZoom));
                zoom.set(newZoom);
                event.consume();
            }
        });

        // ─── Toolbar ───────────────────────────────────────────
        HBox toolbar = buildToolbar();

        // ─── Root (toolbar on top, scrollPane in center) ─────
        root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(scrollPane);

        // Store stage reference for full‑screen toggle (will be set later)
        this.stage = null; // we'll set it via setStage if needed, or we can find it from scene
    }

    private HBox buildToolbar()
    {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(4, 6, 4, 6));
        toolbar.setAlignment(Pos.CENTER);
        toolbar.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");

        // ─── Buttons ──────────────────────────────────────────
        Button zoomInBtn = createIconButton("fas-search-plus", "Zoom In (Ctrl+Scroll)");
        zoomInBtn.setOnAction(e -> zoom.set(Math.min(10.0, zoom.get() * 1.2)));

        Button zoomOutBtn = createIconButton("fas-search-minus", "Zoom Out (Ctrl+Scroll)");
        zoomOutBtn.setOnAction(e -> zoom.set(Math.max(0.1, zoom.get() / 1.2)));

        Button zoomResetBtn = createIconButton("fas-compress", "Reset Zoom");
        zoomResetBtn.setOnAction(e -> zoom.set(1.0));

        Button rotateLeftBtn = createIconButton("fas-undo-alt", "Rotate Left");
        rotateLeftBtn.setOnAction(e -> rotate.set(rotate.get() - 90));

        Button rotateRightBtn = createIconButton("fas-redo-alt", "Rotate Right");
        rotateRightBtn.setOnAction(e -> rotate.set(rotate.get() + 90));

        Button fullScreenBtn = createIconButton("fas-expand", "Toggle Full Screen");
        fullScreenBtn.setOnAction(e -> toggleFullScreen());

        toolbar.getChildren().addAll(
                zoomOutBtn, zoomInBtn, zoomResetBtn,
                rotateLeftBtn, rotateRightBtn,
                fullScreenBtn
        );

        return toolbar;
    }

    private Button createIconButton(String iconLiteral, String tooltipText) 
    {
        Button btn = new Button();
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(8);
        btn.setGraphic(icon);
        btn.setTooltip(new Tooltip(tooltipText));
        btn.setStyle("-fx-background-color: transparent; -fx-padding: 1 2;");
        return btn;
    }

    private void toggleFullScreen() 
    {
        // Find the stage from the scene (the node is in the scene graph)
        if (root.getScene() == null) return;
        Stage stage = (Stage) root.getScene().getWindow();
        stage.setFullScreen(!stage.isFullScreen());
    }

    /** Returns the root node to be placed inside a DockNode. */
    public Parent getNode() 
    {
        return root;
    }
}