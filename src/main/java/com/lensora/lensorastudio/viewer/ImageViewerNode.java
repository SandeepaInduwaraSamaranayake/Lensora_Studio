package com.lensora.lensorastudio.viewer;

import com.lensora.lensorastudio.util.ImageCache;
import com.lensora.lensorastudio.util.ImageMetadataExtractor;

import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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

    private final ObjectProperty<File> currentFile = new SimpleObjectProperty<>();
    private List<File> siblings = List.of();
    private int currentIndex = -1;

    private Button prevBtn;
    private Button nextBtn;

    public ImageViewerNode(File imageFile)
    {
        // ─── Image ──────────────────────────────────────────────
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

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

        loadImage(imageFile, true);
    }



    private void loadImage(File file, boolean resolveSiblings)
    {
        Image image = ImageCache.getOrLoad(file, 1600, 0);
        imageView.setImage(image);
        currentFile.set(file);

        // Fresh view state for each newly displayed image.
        zoom.set(1.0);
        rotate.set(0.0);

        if (resolveSiblings)
        {
            siblings = resolveSiblingImages(file);
            currentIndex = siblings.indexOf(file);
        }

        updateNavigationButtons();
    }

    /** All supported images in the same folder, sorted the same way the file table sorts them. */
    private List<File> resolveSiblingImages(File file)
    {
        File parent = file.getParentFile();
        if (parent == null) return List.of(file);

        File[] found = parent.listFiles(f -> f.isFile() && ImageMetadataExtractor.isSupportedImage(f));
        if (found == null || found.length == 0) return List.of(file);

        List<File> list = new ArrayList<>(Arrays.asList(found));
        list.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    private void goToPrevious()
    {
        if (currentIndex > 0)
        {
            currentIndex--;
            loadImage(siblings.get(currentIndex), false);
        }
    }

    private void goToNext()
    {
        if (currentIndex >= 0 && currentIndex < siblings.size() - 1)
        {
            currentIndex++;
            loadImage(siblings.get(currentIndex), false);
        }
    }

    private void updateNavigationButtons()
    {
        if (prevBtn != null) prevBtn.setDisable(currentIndex <= 0);
        if (nextBtn != null) nextBtn.setDisable(currentIndex < 0 || currentIndex >= siblings.size() - 1);
    }

    public ObjectProperty<File> currentFileProperty()
    {
        return currentFile;
    }

    private HBox buildToolbar()
    {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(4, 6, 4, 6));
        toolbar.setAlignment(Pos.CENTER);
        toolbar.setStyle("-fx-background-color: -color-bg-subtle; -fx-border-color: -color-border-default; -fx-border-width: 0 0 1 0;");

        // ─── Buttons ──────────────────────────────────────────
        prevBtn = createIconButton("fas-chevron-left", "Previous Image");
        prevBtn.setOnAction(e -> goToPrevious());

        nextBtn = createIconButton("fas-chevron-right", "Next Image");
        nextBtn.setOnAction(e -> goToNext());

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


        Region leftSpacer = new Region();
        Region rightSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
                prevBtn, leftSpacer, zoomOutBtn, zoomInBtn, zoomResetBtn,
                rotateLeftBtn, rotateRightBtn,
                fullScreenBtn, rightSpacer, nextBtn
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