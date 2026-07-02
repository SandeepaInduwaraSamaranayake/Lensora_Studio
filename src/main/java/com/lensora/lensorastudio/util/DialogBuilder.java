package com.lensora.lensorastudio.util;

import com.lensora.lensorastudio.controller.DialogController;
import com.lensora.lensorastudio.services.ThemeManager;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
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

    private boolean resizable = false;
    private Modality modality = Modality.APPLICATION_MODAL;

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

    /**
     * Builds the dialog, loads the FXML, applies styles, and sets up dragging.
     * @return the created {@link Stage} (already shown)
     */
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
            Parent root = loader.load();
            Object controller = loader.getController();
            if (controllerConsumer != null) controllerConsumer.accept(controller);


            // Create a new secondary stage (window)
            Stage stage = new Stage();
            stage.initStyle(StageStyle.UNDECORATED);
            stage.initModality(modality);
            stage.setTitle(title);
            stage.setResizable(resizable);

            // Anchors this window directly to owner window
            if(owner != null) stage.initOwner(owner);
            
            Scene scene = new Scene(root);
            stage.setScene(scene);

            // Apply current font size to this new scene
            ThemeManager.applyCurrentFontSizeToScene(scene);

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


            // --- Set up dragging ---
            Node header = null;

            if (controller instanceof DialogController) 
            {
                header = ((DialogController) controller).getHeaderNode();
                if (header == null) 
                {
                    logger.warn("DialogController returned null header for {}", title);
                }
            }
            else
            {
                logger.warn("Controller does not implement DialogController – no drag support for {}", title);
            }

            // Set up dragging for the given header Node
            if (header != null) 
            {
                setupWindowDrag(stage, header);
            }

            // Show the window
            stage.show();
            return stage;

        } 
        catch (IOException ex) 
        {
            logger.error("Failed to load FXML: {}", fxmlUrl, ex);
            return null;
        }
    }

    public DialogBuilder withControllerConsumer(Consumer<Object> consumer) 
    {
        this.controllerConsumer = consumer;
        return this;
    }

    // --- DRAG SETUP ---
    private void setupWindowDrag(Stage stage, Node header) 
    {
        final double[] dragDelta = new double[2];
        header.setOnMousePressed(e -> {
            dragDelta[0] = e.getSceneX();
            dragDelta[1] = e.getSceneY();
        });
        header.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragDelta[0]);
            stage.setY(e.getScreenY() - dragDelta[1]);
        });
    }
}
