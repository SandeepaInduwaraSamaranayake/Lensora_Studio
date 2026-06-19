package com.lensora.lensorastudio.controller;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import java.util.jar.Manifest;
import java.util.jar.Attributes;


public class AboutController 
{
    @FXML 
    private Label productVersionLabel;

    @FXML 
    private Label buildInfoLabel;
    
    @FXML 
    private Label javafxVersionLabel;
    
    @FXML 
    private Label javaVersionLabel;
    
    @FXML 
    private Label osLabel;
    
    @FXML 
    private Label loggingLabel;
    
    @FXML 
    private Button closeButton;

    @FXML
    public void initialize()
    {
        // Read version & build from MANIFEST.MF
        String version = "Unknown";
        String build = "Unknown";

        try (InputStream is = getClass().getResourceAsStream("/META-INF/MANIFEST.MF")) 
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
            System.err.println("Failed to read manifest: " + e.getMessage());
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

        // 5. Logging (example)
        String userHome = System.getProperty("user.home");
        loggingLabel.setText("Logs stored in " + userHome + "/.lensora/logs");
    }

    @FXML
    private void handleClose() 
    {
        Stage stage = (Stage) closeButton.getScene().getWindow();
        stage.close();
    }
}

