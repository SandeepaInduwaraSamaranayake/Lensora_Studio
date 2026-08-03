package com.lensora.lensorastudio.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SplashScreen
{
    private static final Logger logger = LoggerFactory.getLogger(SplashScreen.class);

    private static Stage splashStage;
    private static Label statusLabel;
    private static ProgressBar progressBar;

    private SplashScreen() {}

    public static void show()
    {
        try
        {
            Image splashImage = new Image(Resources.SPLASH_SCREEN.getResourceAsStream());

            ImageView imageView = new ImageView(splashImage);
            imageView.setFitWidth(700);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            statusLabel = new Label("Starting Lensora Studio...");
            statusLabel.setStyle(
                "-fx-text-fill: #e0e0e5;" +
                "-fx-font-size: 11px;" +
                "-fx-font-weight: bold;" +
                "-fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.8), 3, 0, 0, 1);"
            );

            progressBar = new ProgressBar(0.0);
            progressBar.setMaxWidth(Double.MAX_VALUE);
            progressBar.setPrefHeight(10);
            progressBar.setStyle(
                "-fx-accent: #ff7300;" +
                "-fx-control-inner-background: rgba(20, 20, 22, 0.7);" +
                "-fx-text-box-border: transparent;"
            );

            VBox overlayBox = new VBox(6);
            overlayBox.setAlignment(Pos.BOTTOM_LEFT);
            overlayBox.setPadding(new Insets(0, 16, 16, 16));
            overlayBox.getChildren().addAll(statusLabel, progressBar);

            StackPane root = new StackPane();
            root.getChildren().addAll(imageView, overlayBox);
            StackPane.setAlignment(overlayBox, Pos.BOTTOM_CENTER);

            splashStage = new Stage();
            splashStage.initStyle(StageStyle.UNDECORATED);
            IconUtils.setAppIconReplace(splashStage);
            splashStage.setScene(new Scene(root));
            splashStage.centerOnScreen();
            splashStage.show();
        }
        catch (Exception e)
        {
            logger.warn("[SplashScreen] Could not initialize splash screen", e);
        }
    }

    /**
     * Updates status text and progress bar. Thread-safe.
     */
    public static void update(String status, double progress)
    {
        Platform.runLater(() -> {
            if (statusLabel != null)
            {
                statusLabel.setText(status);
            }
            if (progressBar != null)
            {
                progressBar.setProgress(progress);
            }
        });
    }

    /**
     * Closes and disposes of the splash screen stage. Thread-safe.
     */
    public static void close()
    {
        Platform.runLater(() -> {
            if (splashStage != null)
            {
                splashStage.close();
                splashStage = null;
                statusLabel = null;
                progressBar = null;
            }
        });
    }
}