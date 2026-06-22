package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.services.ThemeManager;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.scene.Node;

public class SettingsController implements DialogController
{
    @FXML 
    private ComboBox<AppSettings.Theme> themeCombo;

    @FXML 
    private Spinner<Double> fontSizeSpinner;

    @FXML
    private Button btnCancel,  btnSave, btnApply, btnrestoreDefaults;

    @FXML 
    private HBox prefHeaderBar;

    private final AppSettings settings = AppSettings.getInstance();

    // Temporary copies to revert on Cancel
    private AppSettings.Theme tempTheme;
    private double tempFontSize;

    @FXML
    public void initialize()
    {
        //------------------------ Theme combo ---------------------------------------
        themeCombo.getItems().addAll(AppSettings.Theme.values());

        // Show displayName instead of enum name in the dropdown
        themeCombo.setConverter(new StringConverter<>()
        {
            @Override 
            public String toString(AppSettings.Theme t)   
            { 
                return t == null ? "" : t.displayName; 
            }

            @Override 
            public AppSettings.Theme fromString(String s) 
            { 
                return null; 
            }
        });

        // Load current settings into temp variables
        tempTheme = settings.getTheme();
        tempFontSize = settings.getFontSize();
        themeCombo.setValue(tempTheme);

        // Apply current font size to this scene (so the initial state matches)
        ThemeManager.applyFontSizeToScene(fontSizeSpinner.getScene(), tempFontSize);

        // ----------------------- Font Size Spinner ----------------------------------
        // Set range and step
        SpinnerValueFactory<Double> valueFactory = new SpinnerValueFactory.DoubleSpinnerValueFactory(8, 24, tempFontSize, 0.5);
        fontSizeSpinner.setValueFactory(valueFactory);
        // Optionally add a custom converter to show "12 px"
        fontSizeSpinner.getEditor().setText(String.format("%.1f px", tempFontSize));
        // Update the temporary value on spinner changes (but don't persist yet)
        fontSizeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            tempFontSize = newVal;
            // Live preview: apply to the current settings window only
            ThemeManager.applyFontSizeToScene(fontSizeSpinner.getScene(), tempFontSize);
        });

        // ------------------------ Button Actions ------------------------------------
        btnCancel.setOnAction(e -> cancelAndClose());
        btnSave.setOnAction(e -> saveAndClose());
        btnApply.setOnAction(e -> applyChanges());
        btnrestoreDefaults.setOnAction(e -> restoreDefaults());

    }

    // -------- Save changes to preferences and apply to all windows ----------------
    private void applyChanges() 
    {
        // Save theme if changed
        AppSettings.Theme selectedTheme = themeCombo.getValue();
        if (selectedTheme != null && !selectedTheme.equals(settings.getTheme())) 
        {
            settings.setTheme(selectedTheme);
            ThemeManager.applyTheme(selectedTheme);
            ThemeManager.applyFontSizeToAllWindows(tempFontSize);
        }

        // Save font size (already stored in tempFontSize)
        if (tempFontSize != settings.getFontSize()) 
        {
            settings.setFontSize(tempFontSize);
            // Apply to all open windows
            ThemeManager.applyFontSizeToAllWindows(tempFontSize);
        }
    }

    private void saveAndClose() 
    {
        applyChanges();
        closeWindow();
    }

    private void cancelAndClose() 
    {
        // Revert any live preview back to the saved settings
        ThemeManager.applyFontSizeToAllWindows(settings.getFontSize());
        ThemeManager.applyTheme(settings.getTheme()); // revert theme if live preview changed
        closeWindow();
    }

    private void restoreDefaults() 
    {
        // Reset temporary values to defaults
        tempTheme = AppSettings.DEFAULT_THEME;
        tempFontSize = AppSettings.DEFAULT_FONT_SIZE;
        themeCombo.setValue(tempTheme);
        fontSizeSpinner.getValueFactory().setValue(tempFontSize);
        // Immediately preview the defaults
        ThemeManager.applyFontSizeToScene(fontSizeSpinner.getScene(), tempFontSize);
        // Also apply default theme to this window (but not save)
        ThemeManager.applyTheme(tempTheme);
        ThemeManager.applyFontSizeToAllWindows(tempFontSize);
    }

    @FXML
    private void closeWindow() 
    {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    @Override
    public Node getHeaderNode()
    {
        return prefHeaderBar;
    }
}