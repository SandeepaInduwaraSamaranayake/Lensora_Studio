package com.lensora.lensorastudio.ui.dialogs;

import com.lensora.lensorastudio.core.config.ThemeManager;
import com.lensora.lensorastudio.feature.explorer.workspace.WindowDragManager;
import com.lensora.lensorastudio.ui.controller.DialogController;

import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

/**
 * Builder for creating undecorated modal dialogs with drag support and font-size inheritance.
 */
public class DialogBuilder
{
    private static final Logger logger = LoggerFactory.getLogger(DialogBuilder.class);

    private final URL fxmlUrl;
    private final String title;
    private final Stage owner;

    private Consumer<Object> controllerConsumer;
    private String icon = "🗔";
    private boolean resizable = false;
    private Modality modality = Modality.APPLICATION_MODAL;
    private double minWidth = 300;
    private double minHeight = 150;

    private DialogBuilder(URL fxmlUrl, String title, Stage owner) 
    {
        this.fxmlUrl = fxmlUrl;
        this.title = title;
        this.owner = owner;
    }

    public static DialogBuilder of(URL fxmlUrl, String title, Stage owner)
    {
        return new DialogBuilder(fxmlUrl, title, owner);
    }

    public DialogBuilder resizable(boolean resizable) 
    {
        this.resizable = resizable;
        return this;
    }

    public DialogBuilder modality(Modality modality)
    {
        this.modality = modality;
        return this;
    }

    /** Sets the header icon glyph/emoji shown to the left of the title. Defaults to "🗔". */
    public DialogBuilder icon(String icon)
    {
        this.icon = icon;
        return this;
    }

    public DialogBuilder minSize(double minWidth, double minHeight)
    {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        return this;
    }

    public DialogBuilder withControllerConsumer(Consumer<Object> consumer) 
    {
        this.controllerConsumer = consumer;
        return this;
    }

    /**
     * Builds the dialog, loads the FXML, applies styles, and sets up dragging.
     * @return the created {@link Stage} (already shown)
     */
    @SuppressWarnings("deprecation")
    public Stage build() 
    {
        try 
        {
            logger.info("[Lensora] Dialog Builder is starting :" + title + " Window");
            logger.info("[Lensora] FXML URL = " + fxmlUrl);

            if (fxmlUrl == null)
            {
                logger.error("[Lensora] ERROR: resource not found at :" + fxmlUrl);
                return null;
            }

            // Load the FXML file (absolute resource path)
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent bodyContent  = loader.load();
            Object controller = loader.getController();
            if (controllerConsumer != null) controllerConsumer.accept(controller);


            // Create a new secondary stage (window)
            Stage stage = new Stage();
            stage.initStyle(StageStyle.EXTENDED);
            stage.initModality(modality);
            stage.setTitle(title);
            stage.setResizable(resizable);

            // Anchors this window directly to owner window
            if(owner != null) stage.initOwner(owner);
            
            // Build the shared header bar 
            HBox header = buildHeader(stage, controller);

            BorderPane root = new BorderPane();
            root.setTop(header);
            root.setCenter(bodyContent);

            Scene scene = new Scene(root);
            stage.setScene(scene);
            ThemeManager.initializeSceneStyling(scene);
            stage.setMinWidth(minWidth);
            stage.setMinHeight(minHeight);

            // Center relative to owner
            // Registers a callback that runs after the dialog window is actually visible on screen.
            stage.setOnShown(e -> {
                // if there’s no owner window, we cannot center relative to it, so skip positioning.
                if (owner == null) return;
                double w = stage.getWidth(), h = stage.getHeight();
                if (Double.isNaN(w) || Double.isNaN(h)) 
                {
                    // Forces a layout pass on the root node. 
                    root.layout();
                    // Ask the root for its preferred width and height.
                    w = root.prefWidth(-1);
                    h = root.prefHeight(-1);
                    // If the preferred size is still invalid (≤0 or NaN), use sensible defaults (600×400).
                    if (w <= 0 || Double.isNaN(w)) w = 600;
                    if (h <= 0 || Double.isNaN(h)) h = 400;
                    stage.setWidth(w);
                    stage.setHeight(h);
                }
                // Center the stage over the owner
                stage.setX(owner.getX() + (owner.getWidth() - w) / 2);
                stage.setY(owner.getY() + (owner.getHeight() - h) / 2);
            });


            // Route the OS-level close request (Alt+F4, taskbar close, etc.)
            // through the same canClose()/onClosing() hook as the header button.
            stage.setOnCloseRequest(event -> {
                if (!requestClose(controller, stage))
                {
                    logger.info("[Lensora] Dialog Builder: Close request denied by controller for " + title + " window.");
                    event.consume();
                }
            });

            setupWindowDragMaximize(header);

            stage.show();
            return stage;

        } 
        catch (IOException ex)
        {
            logger.error("Failed to load FXML: {}", fxmlUrl, ex);
            return null;
        }
    }

    // ─── Header construction ────────────────────────────────────────────────

    private HBox buildHeader(Stage stage, Object controller)
    {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setSpacing(8);
        header.setPadding(new Insets(0, 8, 0, 8));

        Label iconLabel = new Label(icon);
        iconLabel.setFont(Font.font(25));

        Label titleLabel = new Label(title);

        header.getChildren().addAll(iconLabel, titleLabel);
        return header;
    }

    /** Asks the controller (if it opts in) whether closing is allowed, then closes if so. */
    private boolean requestClose(Object controller, Stage stage)
    {
        if (controller instanceof DialogController dc && !dc.canClose())
        {
            return false;
        }
        if (controller instanceof DialogController dc)
        {
            dc.onClosing();
        }
        logger.info("[Lensora] Dialog Builder: Closing " + title + " window.");
        stage.close();
        return true;
    }

    // --- DRAG & MAXIMIZE SETUP ---
    private void setupWindowDragMaximize(Node header)
    {
        WindowDragManager.attach(header)
            .withDrag(true)
            .withDoubleClickMaximize(resizable)
            .withSnapToMaximize(resizable)
            .withPullDownRestore(resizable);
    }
}
