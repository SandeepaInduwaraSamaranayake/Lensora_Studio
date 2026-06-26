package com.lensora.lensorastudio.util;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Utility for showing custom themed dialogs (info, warning, confirmation, etc.)
 * that match the Lensora Studio visual style.
 */
public final class Dialogs
{

    private Dialogs() {}

    /**
     * Shows an information dialog (non‑error) with a single OK button.
     *
     * @param owner   Parent window (may be null).
     * @param title   Dialog title (shown in the title bar).
     * @param header  Bold header text (optional, can be null).
     * @param message Main message text.
     */
    public static void showInfo(Window owner, String title, String header, String message) 
    {
        show(owner, title, header, message, "OK", null, null, null);
    }

    /**
     * Shows a confirmation dialog with Yes/No buttons.
     *
     * @param owner       Parent window.
     * @param title       Dialog title.
     * @param header      Bold header (optional).
     * @param message     Main message.
     * @param yesHandler  Runnable to run when "Yes" is clicked.
     * @param noHandler   Runnable to run when "No" is clicked (optional, null to ignore).
     */
    public static void showConfirm(Window owner, String title, String header, String message, 
                                    Runnable yesHandler, Runnable noHandler) 
    {
        show(owner, title, header, message, "Yes", yesHandler, "No", noHandler);
    }

    // ------------------------------- Core builder --------------------------------------

    private static void show(Window owner, String title, String header, String message,
                                    String primaryLabel, Runnable primaryAction, String secondaryLabel, Runnable secondaryAction) 
    {
        if (!Platform.isFxApplicationThread())
        {
            Platform.runLater(() -> show(owner, title, header, message, primaryLabel, primaryAction, secondaryLabel, secondaryAction));
            return;
        }

        Stage dialog = new Stage();
        dialog.initStyle(StageStyle.UNDECORATED);
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(title);

        // -------------- Title bar -------------------
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setSpacing(8);
        titleBar.setPadding(new Insets(8, 12, 8, 14));

        Label titleIcon = new Label("ℹ");
        titleIcon.setFont(Font.font(25));
        Label titleLabel = new Label(title);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.setOnAction(e -> dialog.close());

        titleBar.getChildren().addAll(titleIcon, titleLabel, spacer, closeBtn);

        // Drag the dialog by the title bar
        final double[] dragDelta = {0, 0};
        titleBar.setOnMousePressed(e -> { dragDelta[0] = e.getSceneX(); dragDelta[1] = e.getSceneY(); });
        titleBar.setOnMouseDragged(e -> {
            dialog.setX(e.getScreenX() - dragDelta[0]);
            dialog.setY(e.getScreenY() - dragDelta[1]);
        });

        // ----------------- Body ----------------------
        VBox body = new VBox(12);
        body.setPadding(new Insets(20, 24, 8, 24));

        if (header != null && !header.isEmpty()) 
        {
            Label headerLabel = new Label(header);
            headerLabel.setStyle("-fx-font-weight: bold;");
            body.getChildren().add(headerLabel);
        }

        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(400);
        body.getChildren().add(msgLabel);

        // ----------------- Footer -------------------
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 24, 16, 24));

        if (secondaryLabel != null) 
        {
            Button secondaryBtn = new Button(secondaryLabel);
            secondaryBtn.setOnAction(e -> {
                if (secondaryAction != null) secondaryAction.run();
                dialog.close();
            });
            footer.getChildren().add(secondaryBtn);
        }

        Button primaryBtn = new Button(primaryLabel);
        primaryBtn.setDefaultButton(true);
        primaryBtn.setOnAction(e -> {
            if (primaryAction != null) primaryAction.run();
            dialog.close();
        });
        footer.getChildren().add(primaryBtn);

        // ----------------- Assemble ------------------
        BorderPane root = new BorderPane();
        root.setTop(titleBar);
        root.setCenter(body);
        root.setBottom(footer);

        Scene scene = new Scene(root);
        // inherit same look and feel as the owner
        if (owner != null && owner.getScene() != null) 
        {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }

        dialog.setScene(scene);
        dialog.setMinWidth(420);
        dialog.setMinHeight(150);

        dialog.setOnShown(e -> {
            if (owner != null) {
                dialog.setX(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2.0);
                dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2.0);
            }
        });

        dialog.showAndWait();
    }
}