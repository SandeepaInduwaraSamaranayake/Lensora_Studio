package com.lensora.lensorastudio.util;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Reusable utility for displaying modern floating toast notifications in JavaFX.
 */
public final class NotificationUtil
{
    private static final Duration FADE_DURATION = Duration.millis(200);
    private static final Duration DISPLAY_DURATION = Duration.millis(1800);

    // Literal colors only - no -color-* variable lookups. Those only
    // resolve under AtlantaFX themes; under Modena/Caspian the lookup
    // fails and the property is left unset entirely (not "falls back to
    // the literal"), which is why the pill background and icon were both
    // invisible under Modena specifically.
    private static final String BG_LIGHT        = "#f4f4f4";
    private static final String BG_DARK         = "#2b2b2b";
    private static final String BORDER_LIGHT    = "#d0d0d0";
    private static final String BORDER_DARK     = "#3c3f41";
    private static final String TEXT_LIGHT      = "#2d3436";
    private static final String TEXT_DARK       = "#dcdcdc";

    private NotificationUtil() {}

    public static void showToast(Node node, String message)
    {
        if (node == null || node.getScene() == null) return;
        showToast(node.getScene().getWindow(), message, "fas-check-circle");
    }

    public static void showToast(Node node, String message, String iconLiteral)
    {
        if (node == null || node.getScene() == null) return;
        showToast(node.getScene().getWindow(), message, iconLiteral);
    }

    public static void showToast(Window window, String message)
    {
        showToast(window, message, "fas-check-circle");
    }

    public static void showToast(Window window, String message, String iconLiteral)
    {
        if (window == null || !window.isShowing()) return;

        Platform.runLater(() -> {
            boolean isDark = isDarkTheme(window);

            String bg = isDark ? BG_DARK : BG_LIGHT;
            String border = isDark ? BORDER_DARK : BORDER_LIGHT;
            String text = isDark ? TEXT_DARK : TEXT_LIGHT;

            Popup popup = new Popup();
            popup.setAutoFix(true);

            Label label = new Label(message);
            label.setStyle("-fx-text-fill: " + text + "; -fx-font-weight: bold;");

            HBox content = new HBox(8);
            content.setAlignment(Pos.CENTER);
            content.setPadding(new Insets(8, 16, 8, 16));

            content.setStyle(
                "-fx-background-color: " + bg + "; " +
                "-fx-border-color: " + border + "; " +
                "-fx-border-width: 1px; " +
                "-fx-background-radius: 20px; " +
                "-fx-border-radius: 20px; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 10, 0, 0, 4);"
            );

            if (iconLiteral != null && !iconLiteral.isBlank())
            {
                FontIcon icon = new FontIcon(iconLiteral);
                icon.getStyleClass().addAll("icon-size-14", "toast-icon");
                content.getChildren().add(icon);
            }

            content.getChildren().add(label);

            StackPane container = new StackPane(content);
            container.setOpacity(0.0);
            popup.getContent().add(container);

            popup.setOnShown(e -> {
                double x = window.getX() + (window.getWidth() - popup.getWidth()) / 2.0;
                double y = window.getY() + window.getHeight() - popup.getHeight() - 40;
                popup.setX(x);
                popup.setY(y);
            });

            FadeTransition fadeIn = new FadeTransition(FADE_DURATION, container);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            PauseTransition delay = new PauseTransition(DISPLAY_DURATION);

            FadeTransition fadeOut = new FadeTransition(FADE_DURATION, container);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            SequentialTransition animation = new SequentialTransition(fadeIn, delay, fadeOut);
            animation.setOnFinished(e -> popup.hide());

            popup.show(window);
            animation.play();
        });
    }

    /**
     * Determines dark/light by checking the "dark-theme" marker class
     * ThemeManager already applies to each scene's root — same source of
     * truth used for icon and menu-bar coloring elsewhere in the app, so
     * this toast always matches the currently active theme correctly,
     * including for windows other than the main one (dialogs, image viewer).
     */
    private static boolean isDarkTheme(Window window)
    {
        if (window == null) return false;

        Scene scene = window.getScene();
        if (scene == null || scene.getRoot() == null) return false;

        return scene.getRoot().getStyleClass().contains("dark-theme");
    }
}