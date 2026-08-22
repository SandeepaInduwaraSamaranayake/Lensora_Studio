package com.lensora.lensorastudio.feature.viewer;

import com.lensora.lensorastudio.core.config.AppSettings;
import com.lensora.lensorastudio.media.cache.ImageCache;
import com.lensora.lensorastudio.media.service.ImageValidator;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ImageViewerNode
{
    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 10.0;
    private static final Duration ZOOM_ANIM_DURATION = Duration.millis(120);

    private final ImageView imageView;
    private final DoubleProperty zoom;
    private final DoubleProperty rotate;
    private final ScrollPane scrollPane;
    private final StackPane imageHolder;
    private final BorderPane root;

    private final ObjectProperty<File> currentFile = new SimpleObjectProperty<>();
    private final ComboBox<AppSettings.ImageQuality> qualityCombo = new ComboBox<>();
    private List<File> siblings = List.of();
    private int currentIndex = -1;

    private Button prevBtn;
    private Button nextBtn;

    // Zoom-independent anchor: WHERE in the image (as a 0..1 fraction of
    // its current laid-out size) should stay under WHERE in the viewport
    // (also a 0..1 fraction). Because both are fractions, they remain
    // valid reference points regardless of how large/small the image
    // becomes as zoom changes.
    private double anchorImageFractionX = 0.5;
    private double anchorImageFractionY = 0.5;
    private double anchorViewportFractionX = 0.5;
    private double anchorViewportFractionY = 0.5;
    private boolean anchorArmed = false;

    private Timeline zoomAnimation;

    public ImageViewerNode(File imageFile)
    {
        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        zoom = new SimpleDoubleProperty(1.0);
        rotate = new SimpleDoubleProperty(0.0);
        imageView.rotateProperty().bind(rotate);

        scrollPane = new ScrollPane();
        scrollPane.setPannable(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);

        // Ensure key events register when clicking anywhere inside the image viewport
        scrollPane.setOnMouseClicked(e -> scrollPane.requestFocus());

        imageHolder = new StackPane(imageView);
        imageHolder.setPadding(new Insets(10));
        imageHolder.setStyle("-fx-background-color: -color-bg-default;");
        scrollPane.setContent(imageHolder);

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

        // Whenever the content's ACTUAL laid-out size changes (which
        // happens continuously as zoom animates), re-apply the anchor
        // using fresh, real layout data - this is what makes the anchor
        // correction reliable instead of racing stale frame data.
        imageHolder.widthProperty().addListener((obs, o, n) -> { if (anchorArmed) restoreZoomAnchor(); });
        imageHolder.heightProperty().addListener((obs, o, n) -> { if (anchorArmed) restoreZoomAnchor(); });

        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown())
            {
                captureZoomAnchor(event.getSceneX(), event.getSceneY());

                double sensitivity = AppSettings.getInstance().getZoomSensitivity();
                double factor = event.getDeltaY() > 0 ? sensitivity : 1.0 / sensitivity;
                double targetZoom = clamp(zoom.get() * factor);

                animateZoomTo(targetZoom);
                event.consume();
            }
        });

        // --- Keyboard Shortcuts (Ctrl + Left / Right for Navigation) ---
        scrollPane.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown())
            {
                if (event.getCode() == KeyCode.LEFT)
                {
                    goToPrevious();
                    event.consume();
                }
                else if (event.getCode() == KeyCode.RIGHT)
                {
                    goToNext();
                    event.consume();
                }
            }
        });

        HBox toolbar = buildToolbar();

        root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(scrollPane);

        loadImage(imageFile, true);
    }

    // ─── Cursor-anchored zoom mechanics ─────────────────────────────────────

    /**
     * Captures the cursor's current position as two zoom-independent
     * fractions: where it sits within the image content (0..1 of
     * imageHolder's current size) and where it sits within the visible
     * viewport (0..1 of viewport size). Uses scene coordinates converted
     * via sceneToLocal(...) on each node directly - this sidesteps any
     * ambiguity in ScrollEvent's own getX()/getY() semantics, which was
     * the root cause of the anchor always resolving to the top-left
     * corner regardless of actual cursor position.
     */
    private void captureZoomAnchor(double sceneX, double sceneY)
    {
        var viewport = scrollPane.getViewportBounds();
        if (viewport == null || viewport.getWidth() <= 0 || viewport.getHeight() <= 0) return;
        if (imageHolder.getWidth() <= 0 || imageHolder.getHeight() <= 0) return;

        Point2D scenePoint = new Point2D(sceneX, sceneY);

        Point2D contentLocal = imageHolder.sceneToLocal(scenePoint);
        Point2D viewportLocal = scrollPane.sceneToLocal(scenePoint);

        anchorImageFractionX = clamp01(contentLocal.getX() / imageHolder.getWidth());
        anchorImageFractionY = clamp01(contentLocal.getY() / imageHolder.getHeight());

        anchorViewportFractionX = clamp01(viewportLocal.getX() / viewport.getWidth());
        anchorViewportFractionY = clamp01(viewportLocal.getY() / viewport.getHeight());

        anchorArmed = true;
    }

    /** Scrolls so the captured image fraction sits back under the captured viewport fraction, using CURRENT layout sizes. */
    private void restoreZoomAnchor()
    {
        var viewport = scrollPane.getViewportBounds();
        if (viewport == null || viewport.getWidth() <= 0 || viewport.getHeight() <= 0) return;

        double contentWidth = imageHolder.getWidth();
        double contentHeight = imageHolder.getHeight();

        double newContentX = anchorImageFractionX * contentWidth;
        double newContentY = anchorImageFractionY * contentHeight;

        double desiredViewportX = anchorViewportFractionX * viewport.getWidth();
        double desiredViewportY = anchorViewportFractionY * viewport.getHeight();

        double targetScrollX = newContentX - desiredViewportX;
        double targetScrollY = newContentY - desiredViewportY;

        double scrollableX = Math.max(0, contentWidth - viewport.getWidth());
        double scrollableY = Math.max(0, contentHeight - viewport.getHeight());

        scrollPane.setHvalue(scrollableX > 0 ? clamp01(targetScrollX / scrollableX) : 0);
        scrollPane.setVvalue(scrollableY > 0 ? clamp01(targetScrollY / scrollableY) : 0);
    }

    private void animateZoomTo(double targetZoom)
    {
        if (zoomAnimation != null)
        {
            zoomAnimation.stop();
        }

        zoomAnimation = new Timeline(
                new KeyFrame(ZOOM_ANIM_DURATION,
                        new KeyValue(zoom, targetZoom, Interpolator.EASE_OUT))
        );
        zoomAnimation.setOnFinished(e -> anchorArmed = false);
        zoomAnimation.play();
    }

    private double clamp(double value)
    {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    private double clamp01(double value)
    {
        return Math.max(0, Math.min(1, value));
    }

    // ─── Toolbar zoom actions - anchor to viewport center ──────────────────

    private void zoomTowardCenter(double factor)
    {
        var viewport = scrollPane.getViewportBounds();
        if (viewport == null) return;

        Point2D centerScene = scrollPane.localToScene(viewport.getWidth() / 2.0, viewport.getHeight() / 2.0);
        captureZoomAnchor(centerScene.getX(), centerScene.getY());
        animateZoomTo(clamp(zoom.get() * factor));
    }

    private void resetZoom()
    {
        var viewport = scrollPane.getViewportBounds();
        if (viewport != null)
        {
            Point2D centerScene = scrollPane.localToScene(viewport.getWidth() / 2.0, viewport.getHeight() / 2.0);
            captureZoomAnchor(centerScene.getX(), centerScene.getY());
        }
        animateZoomTo(1.0);
    }

    // ─── Image loading / navigation (unchanged) ─────────────────────────────

    private void loadImage(File file, boolean resolveSiblings)
    {
        if (zoomAnimation != null)
        {
            zoomAnimation.stop();
        }

        currentFile.set(file);
        AppSettings.ImageQuality quality = AppSettings.getInstance().getImageViewerQuality();
        
        Image image = ImageCache.getOrLoad(file, quality.maxDimension, 0);
        imageView.setImage(image);

        zoom.set(1.0);
        anchorArmed = false;
        rotate.set(0.0);

        if (resolveSiblings)
        {
            siblings = resolveSiblingImages(file);
            currentIndex = siblings.indexOf(file);
        }

        updateNavigationButtons();
    }

    private List<File> resolveSiblingImages(File file)
    {
        File parent = file.getParentFile();
        if (parent == null) return List.of(file);

        File[] found = parent.listFiles(f -> f.isFile() && ImageValidator.isJavaFXLoadable(f));
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

    // ─── Toolbar (unchanged aside from wiring above) ───────────────────────

    private HBox buildToolbar()
    {
        HBox toolbar = new HBox(10);
        toolbar.setPadding(new Insets(4, 6, 4, 6));
        toolbar.setAlignment(Pos.CENTER);
        toolbar.getStyleClass().add("viewer-toolbar");

        // Enable drag and drop specifically on the toolbar
        setupToolbarDropTarget(toolbar);

        prevBtn = createIconButton("fas-chevron-left", "Previous Image");
        prevBtn.setOnAction(e -> goToPrevious());

        nextBtn = createIconButton("fas-chevron-right", "Next Image");
        nextBtn.setOnAction(e -> goToNext());

        Button zoomInBtn = createIconButton("fas-search-plus", "Zoom In (Ctrl+Scroll)");
        zoomInBtn.setOnAction(e -> zoomTowardCenter(AppSettings.getInstance().getZoomSensitivity()));

        Button zoomOutBtn = createIconButton("fas-search-minus", "Zoom Out (Ctrl+Scroll)");
        zoomOutBtn.setOnAction(e -> zoomTowardCenter(1.0 / AppSettings.getInstance().getZoomSensitivity()));

        Button zoomResetBtn = createIconButton("fas-compress", "Reset Zoom");
        zoomResetBtn.setOnAction(e -> resetZoom());

        Button rotateLeftBtn = createIconButton("fas-undo-alt", "Rotate Left");
        rotateLeftBtn.setOnAction(e -> rotate.set(rotate.get() - 90));

        Button rotateRightBtn = createIconButton("fas-redo-alt", "Rotate Right");
        rotateRightBtn.setOnAction(e -> rotate.set(rotate.get() + 90));

        Button fullScreenBtn = createIconButton("fas-expand", "Toggle Full Screen");
        fullScreenBtn.setOnAction(e -> toggleFullScreen());

        setupQualityCombo();

        Region leftSpacer = new Region();
        Region rightSpacer = new Region();
        HBox.setHgrow(leftSpacer, Priority.ALWAYS);
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        toolbar.getChildren().addAll(
                prevBtn, leftSpacer, zoomOutBtn, zoomInBtn, zoomResetBtn,
                rotateLeftBtn, rotateRightBtn,
                fullScreenBtn, qualityCombo, rightSpacer, nextBtn
        );

        return toolbar;
    }

    private Button createIconButton(String iconLiteral, String tooltipText)
    {
        Button btn = new Button();
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("icon-size-12");
        btn.setGraphic(icon);
        btn.setTooltip(new Tooltip(tooltipText));
        btn.setStyle("-fx-background-color: transparent; -fx-padding: 1 2;");
        return btn;
    }

    private void toggleFullScreen()
    {
        if (root.getScene() == null) return;
        Stage stage = (Stage) root.getScene().getWindow();
        stage.setFullScreen(!stage.isFullScreen());
    }

    private void setupQualityCombo()
    {
        qualityCombo.getItems().addAll(AppSettings.ImageQuality.values());
        qualityCombo.setValue(AppSettings.getInstance().getImageViewerQuality());
        qualityCombo.setPrefWidth(150);
        Tooltip.install(qualityCombo, new Tooltip("Image Quality"));

        qualityCombo.valueProperty().addListener((obs, old, newQuality) -> {
            if (newQuality == null) return;
            AppSettings.getInstance().setImageViewerQuality(newQuality);
            reloadCurrentImageAtQuality(newQuality);
        });
    }

    private void reloadCurrentImageAtQuality(AppSettings.ImageQuality quality)
    {
        File file = currentFile.get();
        if (file == null) return;

        Image image = ImageCache.getOrLoad(file, quality.maxDimension, 0);
        imageView.setImage(image);
        // Zoom/rotation/pan state is intentionally preserved - the user is
        // just requesting a sharper/lighter version of the same view, not
        // resetting their place in the image.
    }

    public Parent getNode()
    {
        return root;
    }

    /**
     * Public API to replace the current image displayed in this viewer node.
     */
    public void replaceImage(File file)
    {
        if (file != null && ImageValidator.isJavaFXLoadable(file))
        {
            loadImage(file, true);
        }
    }

    private void setupToolbarDropTarget(HBox toolbar)
    {
        toolbar.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles())
            {
                boolean hasSupportedImage = event.getDragboard().getFiles().stream()
                        .anyMatch(ImageValidator::isJavaFXLoadable);
                if (hasSupportedImage)
                {
                    event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                }
            }
            event.consume(); // Prevents top-level window host from creating a new tab
        });

        toolbar.setOnDragEntered(event -> {
            if (event.getDragboard().hasFiles())
            {
                toolbar.getStyleClass().add("toolbar-drop-hover");
            }
        });

        toolbar.setOnDragExited(event -> {
            toolbar.getStyleClass().remove("toolbar-drop-hover");
        });

        toolbar.setOnDragDropped(event -> {
            toolbar.getStyleClass().remove("toolbar-drop-hover");
            var db = event.getDragboard();
            boolean success = db.hasFiles();

            event.setDropCompleted(success);
            event.consume();

            if (success)
            {
                // Takes the first supported image to replace this specific viewer in-place
                db.getFiles().stream()
                        .filter(ImageValidator::isJavaFXLoadable)
                        .findFirst()
                        .ifPresent(file -> Platform.runLater(() -> replaceImage(file)));
            }
        });
    }
}