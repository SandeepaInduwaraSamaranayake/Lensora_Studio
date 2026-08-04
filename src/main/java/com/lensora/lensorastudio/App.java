package com.lensora.lensorastudio;

import atlantafx.base.theme.CupertinoDark;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import com.lensora.lensorastudio.controller.MainController;
import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.services.DatabaseManager;
import com.lensora.lensorastudio.services.LayoutPersistence;
import com.lensora.lensorastudio.services.ThemeManager;
import com.lensora.lensorastudio.util.AppIconUtil;
import com.lensora.lensorastudio.util.ErrorHandler;
import com.lensora.lensorastudio.util.Resources;
import com.lensora.lensorastudio.util.SplashScreen;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Main application entry point.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Install global exception handlers and display splash screen</li>
 *   <li>Asynchronously initialise the database schema off the UI thread</li>
 *   <li>Load the main FXML and apply the dark theme</li>
 *   <li>Restore window geometry (size/position/maximised) via {@link LayoutPersistence}</li>
 * </ul>
 */
public class App extends Application
{
    static
    {
        try
        {
            // Set log directory before ANY loggers are created
            String logDir = AppSettings.getInstance().getDefaultLogDir();
            System.setProperty("LOG_DIR", logDir);
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
    public void init()
    {
        logger.info("[Lensora] Application starting up...");
    }

    @Override
    public void start(Stage stage)
    {
        ErrorHandler.installGlobalHandler();

        // Splash screen renders immediately
        SplashScreen.show();

        // Begin startup pipeline
        initializeDatabaseAsync(stage);
    }

    /**
     * Runs long-running database initialisation off the UI thread.
     */
    private void initializeDatabaseAsync(Stage stage)
    {
        CompletableFuture.runAsync(() -> {
            SplashScreen.update("Initializing database...", 0.20);
            DatabaseManager.initializeDatabase();
        })
        .thenRun(() -> Platform.runLater(() -> {
            try
            {
                initializeUI(stage);
            }
            catch (Exception e)
            {
                handleStartupFailure(e);
            }
        }))
        .exceptionally(ex -> {
            Platform.runLater(() -> handleStartupFailure(ex.getCause() != null ? ex.getCause() : ex));
            return null;
        });
    }

    /**
     * Builds and presents the JavaFX primary stage once asynchronous prerequisites complete.
     */
    private void initializeUI(Stage stage) throws IOException
    {
        SplashScreen.update("Applying theme...", 0.40);
        applyTheme(stage);

        SplashScreen.update("Loading workspace UI...", 0.65);
        FXMLLoader fxmlLoader = new FXMLLoader(Resources.MAIN_VIEW.url());
        Parent root = fxmlLoader.load();
        Scene scene = new Scene(root);

        configureStage(stage, scene);

        MainController mainController = fxmlLoader.getController();
        configureController(mainController, stage);

        SplashScreen.update("Restoring workspace layout...", 0.90);
        restoreLayout(stage);

        ThemeManager.apply(scene);

        installShutdownHandler(stage, mainController);

        stage.show();
    }

    @SuppressWarnings("deprecation")
    private void applyTheme(Stage stage)
    {
        Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());
        stage.initStyle(StageStyle.EXTENDED);
    }

    private void configureStage(Stage stage, Scene scene)
    {
        AppIconUtil.setAppIconReplace(stage);
        stage.setTitle("Lensora Studio");
        stage.setScene(scene);
        
        // Hide window until layout geometry calculations complete
        stage.setOpacity(0.0);
    }

    private void configureController(MainController controller, Stage stage)
    {
        controller.setStage(stage);
        controller.getDockingService().initialize(stage);
        controller.getDockingService().registerThemeListener();
    }

    private void restoreLayout(Stage stage)
    {
        // Restore window geometry (size, position, maximised) BEFORE showing.
        LayoutPersistence.bindWindow(stage, () -> {
            stage.setOpacity(1.0);
            SplashScreen.close();
        });
    }

    /**
    * centralized close request handler for all windows (e.g. to prompt "Save changes?" on exit) 
    */
    private void installShutdownHandler(Stage stage, MainController controller)
    {
        stage.setOnCloseRequest(event -> {
            controller.getDockingService().saveLayout();
            logger.info("[Lensora] Application intercepting shutdown sequence. Cleaning up pools...");

                // Check for unsaved work here if needed (uncomment below to test)
                /*
                if (hasUnsavedChanges) {
                    event.consume(); // Cancels the exit completely if the user clicks 'Cancel'
                    return;
                }
                */
            
            // Shut down background processes safely
            Platform.exit();            // Closes the JavaFX Toolkit cleanly
            System.exit(0);      // Terminates the JVM instance completely
        });
    }

    /**
     * Centralized startup failure handler ensuring clean UI state before logging and exit.
     */
    private void handleStartupFailure(Throwable throwable)
    {
        SplashScreen.close();
        logger.error("[Lensora] Application startup failed", throwable);

        Exception errorDialogException = throwable instanceof Exception 
                ? (Exception) throwable 
                : new RuntimeException(throwable);

        ErrorHandler.show(null, "Lensora Studio failed to start.", errorDialogException);
        Platform.exit();
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