package com.lensora.lensorastudio;

import atlantafx.base.theme.CupertinoDark;

import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.slf4j.Logger;

import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.services.DatabaseManager;
import com.lensora.lensorastudio.services.LayoutPersistence;
import com.lensora.lensorastudio.services.ThemeManager;
import com.lensora.lensorastudio.util.ErrorHandler;
import com.lensora.lensorastudio.util.Resources;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Main application entry point.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Initialise the database schema</li>
 *   <li>Install global exception handlers</li>
 *   <li>Load the main FXML and apply the dark theme</li>
 *   <li>Restore window geometry (size/position/maximised) via {@link LayoutPersistence}</li>
 * </ul>
 */
public class App extends Application 
{
    static
    {
        // Set log directory before ANY loggers are created
        String logDir = AppSettings.getInstance().getDefaultLogDir();
        System.setProperty("LOG_DIR", logDir);
        try
        {
            // Ensure the directory exists
            Files.createDirectories(Paths.get(logDir));
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    /**
     * Runs <strong>before</strong> the JavaFX toolkit starts.
     * Used for heavy initialisation that doesn't need the UI thread.
     */
    @Override
    public void init() throws Exception 
    {
        DatabaseManager.initializeDatabase();
    }

    /**
     * The main entry point for the JavaFX UI thread.
     * Builds the primary stage, sets up the scene, and restores the layout.
     */
    @Override
    public void start(Stage stage)
    {
        // Install the global uncaught-exception handler.
        ErrorHandler.installGlobalHandler();

        try
        {
            //------------------------------- Theme and window style ------------------------------------
            Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());
            stage.initStyle(StageStyle.EXTENDED);
            
            //------------------------------------- Load FXML -------------------------------------------
            FXMLLoader fxmlLoader = new FXMLLoader(Resources.MAIN_VIEW.url());
            Parent root = fxmlLoader.load();

            //------------------------------------- Scene ----------------------------------------------
            Scene scene = new Scene(root);
            // Load font-size override after the AtlantaFX stylesheet so it wins
            // scene.getStylesheets().add(
            //     App.class.getResource("styles/app-overrides.css").toExternalForm()
            // );

            //------------------------------------- Stage Setup ---------------------------------------
            // Clear any default icons just in case
            stage.getIcons().clear();

            // Add multiple resolutions for Windows Taskbar scaling (Highly Recommended)
            // Windows uses 16x16 for the top corner, 32x32 for the taskbar / minimize state
            stage.getIcons().addAll(
                new Image(Resources.APP_ICON.getResourceAsStream())
            );
            stage.setTitle("Lensora Studio");
            stage.setScene(scene);
            
            // Hide the window until layout is ready
            stage.setOpacity(0.0);

            // Restore window geometry (size, position, maximised) BEFORE showing.
            // Bind window - the callback will reveal it after dividers are set
            LayoutPersistence.bindWindow(stage, () -> {
                // Now the layout is stable, make the window visible
                stage.setOpacity(1.0);
            });

            // Apply saved theme and font size
            ThemeManager.apply(scene);

            stage.show();
            

            // centralized close request handler for all windows (e.g. to prompt "Save changes?" on exit)
            stage.setOnCloseRequest(event -> {
                logger.info("[Lensora] Application intercepting shutdown sequence. Cleaning up pools...");
                
                // 1. Check for unsaved work here if needed (uncomment below to test)
                /*
                if (hasUnsavedChanges) {
                    event.consume(); // Cancels the exit completely if the user clicks 'Cancel'
                    return;
                }
                */
                
                // 2. Shut down background processes safely
                Platform.exit(); // Closes the JavaFX Toolkit cleanly
                System.exit(0);  // Terminates the JVM instance completely
            });
        }
        catch (Exception e)
        {
            logger.error("[Lensora] Failed to start application: " + e.getMessage());
            // Show the error dialog before giving up — the stage may not be
            // visible yet so we pass null as the owner.
            ErrorHandler.show(null, "Lensora Studio failed to start.", e);
            Platform.exit();
        }
    }

    public static void main(String[] args)
    {
        try
        {
            App.launch();
        }
        catch(Throwable t)
        {
            System.err.println("Fatal error during launch: " + t.getMessage());
            t.printStackTrace();
        }
    }
}