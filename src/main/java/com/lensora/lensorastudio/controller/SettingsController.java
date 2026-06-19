package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.services.ThemeManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.stage.Stage;
import javafx.util.StringConverter;

public class SettingsController
{
    // @FXML 
    // private ComboBox<AppSettings.Theme> themeCombo;

    // @FXML 
    // private Slider fontSizeSlider;

    // @FXML 
    // private Label fontSizeLabel;

    @FXML
    private Button btnCancel;

    // private final AppSettings settings = AppSettings.getInstance();

    @FXML
    public void initialize()
    {
        // // ── Theme combo ───────────────────────────────────────────────────────
        // themeCombo.getItems().addAll(AppSettings.Theme.values());

        // // Show displayName instead of enum name in the dropdown
        // themeCombo.setConverter(new StringConverter<>()
        // {
        //     @Override public String toString(AppSettings.Theme t)   { return t == null ? "" : t.displayName; }
        //     @Override public AppSettings.Theme fromString(String s) { return null; }
        // });

        // themeCombo.setValue(settings.getTheme());

        // // Live preview on selection change
        // themeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
        //     if (newVal == null) return;
        //     settings.setTheme(newVal);
        //     ThemeManager.applyTheme(newVal);
        // });

        btnCancel.setOnAction(e -> closeWindow());

        // // ── Font size slider (10–18px range) ──────────────────────────────────
        // fontSizeSlider.setMin(10);
        // fontSizeSlider.setMax(18);
        // fontSizeSlider.setValue(settings.getFontSize());
        // fontSizeSlider.setMajorTickUnit(2);
        // fontSizeSlider.setMinorTickCount(1);
        // fontSizeSlider.setSnapToTicks(true);

        // updateFontLabel(settings.getFontSize());

        // fontSizeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
        //     double size = newVal.doubleValue();
        //     updateFontLabel(size);
        //     settings.setFontSize(size);

        //     // Live preview — apply to the scene this node belongs to
        //     if (fontSizeSlider.getScene() != null)
        //         ThemeManager.applyFontSize(fontSizeSlider.getScene(), size);
        // });


    }

    // private void updateFontLabel(double size)
    // {
    //     fontSizeLabel.setText(String.format("%.0fpx", size));
    // }

    @FXML
    private void closeWindow() 
    {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }
}