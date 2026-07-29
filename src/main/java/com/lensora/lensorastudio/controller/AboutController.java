package com.lensora.lensorastudio.controller;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.jar.Manifest;

import java.util.jar.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.util.Resources;


public class AboutController implements DialogController
{
    private static final Logger logger = LoggerFactory.getLogger(AboutController.class);

    @FXML 
    private Label       productVersionLabel, 
                        buildInfoLabel, 
                        javafxVersionLabel, 
                        javaVersionLabel,
                        osLabel, 
                        loggingLabel,
                        applicationProjectRootLabel;
    
    @FXML
    private Button      closeButton;

    @FXML
    private HBox        aboutHeaderBar;
    

    @FXML
    public void initialize()
    {
        logger.info("[AboutController] Initializing AboutController...");
        // Read version & build from MANIFEST.MF
        String version = "Unknown";
        String build = "Unknown";

        try (InputStream is = Resources.MANIFEST.getResourceAsStream())
        {
            if (is != null) 
            {
                Manifest manifest = new Manifest(is);
                Attributes attrs = manifest.getMainAttributes();
                String implVersion = attrs.getValue("Implementation-Version");
                if (implVersion != null) version = implVersion;
                String implBuild = attrs.getValue("Implementation-Build");
                if (implBuild != null) build = implBuild;
            }
        }
        catch (IOException e) 
        {
            logger.error("Failed to read manifest: " + e.getMessage());
        }

        // Fallback if running from IDE (no manifest)
        if ("Unknown".equals(version))
        {
            version = "Development Build";
            build = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        }

        productVersionLabel.setText("Lensora Studio " + version);
        buildInfoLabel.setText(build);

        // 2. JavaFX runtime version
        javafxVersionLabel.setText(System.getProperty("javafx.runtime.version", "Unknown"));

        // 3. Java runtime
        String javaVersion = System.getProperty("java.version");
        String javaRuntime = System.getProperty("java.runtime.name", "OpenJDK");
        javaVersionLabel.setText(javaVersion + ", " + javaRuntime);

        // 4. OS
        String osName = System.getProperty("os.name");
        String osArch = System.getProperty("os.arch");
        String osVersion = System.getProperty("os.version");
        osLabel.setText(osName + " " + osArch + " " + osVersion);

        // project root
        String projectRoot = AppSettings.getInstance().getDefaultProjectRoot();
        applicationProjectRootLabel.setText(projectRoot);
        
        // 5. Logging (example)
        String logDir = com.lensora.lensorastudio.services.AppSettings.getInstance().getDefaultLogDir();
        loggingLabel.setText("Logs stored in " + logDir);
    }

    @FXML
    private void handleClose() 
    {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        if (stage != null) stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }
}