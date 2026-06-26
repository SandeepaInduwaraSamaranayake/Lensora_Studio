package com.lensora.lensorastudio.util;


import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Central error-handling utility for Lensora Studio.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // From any controller or service — shows dialog on the FX thread:
 * ErrorHandler.show(owner, "Failed to load projects", e);
 *
 * // Convenience — no owner, centers on screen:
 * ErrorHandler.show("Could not save settings", e);
 *
 * // Install as global uncaught-exception handler in App.start():
 * ErrorHandler.installGlobalHandler();
 * }</pre>
 *
 * The dialog matches the dark Lensora Studio visual style and shows:
 *  - A short user-readable message
 *  - The exception class + message
 *  - An expandable stack trace
 *  - Copy to Clipboard button
 */
public final class ErrorHandler
{
    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    private ErrorHandler() {}

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Shows an error dialog, ensuring it runs on the JavaFX Application Thread.
     *
     * @param owner   Parent window for modal anchoring — may be {@code null}.
     * @param message Short user-readable description of what went wrong.
     * @param cause   The exception — may be {@code null} for message-only errors.
     */
    public static void show(Window owner, String message, Throwable cause)
    {
        log.error(message, cause);

        if (Platform.isFxApplicationThread())
            showDialog(owner, message, cause);
        else
            Platform.runLater(() -> showDialog(owner, message, cause));
    }

    /** Convenience overload — no owner window. */
    public static void show(String message, Throwable cause)
    {
        show(null, message, cause);
    }

    /** Convenience overload — message only, no exception. */
    public static void show(Window owner, String message)
    {
        show(owner, message, null);
    }

    /**
     * Installs a thread-level uncaught-exception handler that catches anything
     * not explicitly handled in try/catch blocks and shows the error dialog.
     *
     * Call once from {@code App.start()} after the primary stage is shown.
     */
    public static void installGlobalHandler()
    {
        // FX Application Thread exceptions
        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) ->
            show("An unexpected error occurred on the JavaFX thread.", throwable));

        // All other threads
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
            show("An unexpected error occurred in thread: " + thread.getName(), throwable));

        log.info("[Lensora] Global uncaught-exception handler installed.");
    }

    // ─── Dialog builder ───────────────────────────────────────────────────────

    private static void showDialog(Window owner, String message, Throwable cause)
    {
        Stage dialog = new Stage();

        // borderless — we draw our own chrome
        dialog.initStyle(StageStyle.UNDECORATED);   
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Error - Lensora Studio");

        // ── Title bar ─────────────────────────────────────────────────────────
        HBox titleBar = new HBox();
        titleBar.setAlignment(Pos.CENTER_LEFT);
        titleBar.setSpacing(8);
        titleBar.setPadding(new Insets(8, 12, 8, 14));

        Label titleIcon = new Label("⚠");
        titleIcon.setFont(Font.font(25));
        Label titleLabel = new Label("Error");

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        Button closeBtn = new Button("✕");
        closeBtn.setOnAction(e -> dialog.close());

        titleBar.getChildren().addAll(titleIcon, titleLabel, titleSpacer, closeBtn);

        // Allow dragging the dialog by its title bar
        final double[] dragDelta = {0, 0};
        titleBar.setOnMousePressed(e -> { dragDelta[0] = e.getSceneX(); dragDelta[1] = e.getSceneY(); });
        titleBar.setOnMouseDragged(e -> {
            dialog.setX(e.getScreenX() - dragDelta[0]);
            dialog.setY(e.getScreenY() - dragDelta[1]);
        });

        // ── Body ──────────────────────────────────────────────────────────────
        VBox body = new VBox(14);
        body.setPadding(new Insets(20, 24, 8, 24));

        // User-readable message
        Label msgLabel = new Label(message);
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(460);

        body.getChildren().add(msgLabel);

        // Exception summary
        if (cause != null)
        {
            String exSummary = cause.getClass().getSimpleName()
                + (cause.getMessage() != null ? ": " + cause.getMessage() : "");

            Label exLabel = new Label(exSummary);
            exLabel.setWrapText(true);
            exLabel.setStyle("""
                -fx-text-fill: #e05c5c;
                -fx-font-size: 11px;
                -fx-font-family: monospace;
                -fx-background-color: #1e1010;
                -fx-background-radius: 4;
                -fx-border-color: #3a1a1a;
                -fx-border-radius: 4;
                -fx-padding: 6 10;
                """);
            body.getChildren().add(exLabel);

            // Expandable stack trace
            String stackTrace = getStackTrace(cause);

            TitledPane tracePane = new TitledPane();
            tracePane.setText("Stack Trace");
            tracePane.setExpanded(false);

            TextArea traceArea = new TextArea(stackTrace);
            traceArea.setEditable(false);
            traceArea.setWrapText(false);
            traceArea.setPrefRowCount(12);

            tracePane.setContent(traceArea);
            body.getChildren().add(tracePane);

            // DISABLING ANIMATION: JavaFX TitledPane animations interfere with stage resizing.
            // By disabling it, the layout updates immediately, allowing the stage to resize correctly.
            tracePane.setAnimated(false);

            tracePane.expandedProperty().addListener((obs, wasExpanded, isExpanded) -> {
                Platform.runLater(dialog::sizeToScene);
            });
        }

        // ── Footer ────────────────────────────────────────────────────────────
        HBox footer = new HBox(10);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 24, 16, 24));

        if (cause != null)
        {
            Button copyBtn = new Button("Copy to Clipboard");
            String fullText = message + "\n\n" + getStackTrace(cause);
            copyBtn.setOnAction(e -> {
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(fullText);
                javafx.scene.input.Clipboard.getSystemClipboard().setContent(content);
                copyBtn.setText("Copied ✓");
                copyBtn.setDisable(true);
            });
            footer.getChildren().add(copyBtn);
        }

        Button okBtn = new Button("OK");
        okBtn.setDefaultButton(true);
        okBtn.setOnAction(e -> dialog.close());
        footer.getChildren().add(okBtn);

        // ── Assemble ──────────────────────────────────────────────────────────
        BorderPane root = new BorderPane();
        root.setTop(titleBar);
        root.setCenter(body);
        root.setBottom(footer);

        Scene scene = new Scene(root);
        // Inherit app stylesheets if available
        if (owner != null && owner.getScene() != null)
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());

        dialog.setScene(scene);
        dialog.setMinWidth(500);
        dialog.setMinHeight(200);

        // Center on owner or screen
        dialog.setOnShown(e -> {
            if (owner != null)
            {
                dialog.setX(owner.getX() + (owner.getWidth()  - dialog.getWidth())  / 2.0);
                dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2.0);
            }
        });

        dialog.showAndWait();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String getStackTrace(Throwable t)
    {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}