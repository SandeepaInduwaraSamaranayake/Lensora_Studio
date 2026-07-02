package com.lensora.lensorastudio.controller;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.services.ThemeManager;
import com.lensora.lensorastudio.util.Dialogs;
import com.lensora.lensorastudio.util.StartupManager;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.scene.Node;

public class SettingsController implements DialogController
{
    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);

    @FXML 
    private ComboBox<AppSettings.Theme> themeCombo;

    @FXML 
    private Spinner<Double> fontSizeSpinner;

    @FXML
    private Button btnCancel, btnSave, btnApply, btnRestoreDefaults, btnBrowseDefaultRoot, btnBrowseLogDir;

    @FXML 
    private HBox prefHeaderBar;

    @FXML 
    private TextField projectRootField, logDirField;

    @FXML
    private CheckBox openOnStartupCheck;

    private final AppSettings settings = AppSettings.getInstance();

    // Temporary copies to revert on Cancel
    private AppSettings.Theme   tempTheme;
    private double              tempFontSize;
    private String              tempProjectRoot;
    private String              tempLogDir;
    private boolean             tempOpenOnStartup;

    @Override
    public Node getHeaderNode()
    {
        return prefHeaderBar;
    }

    @FXML
    public void initialize()
    {
        logger.info("Initializing SettingsController...");
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
        tempTheme         = settings.getTheme();
        tempFontSize      = settings.getFontSize();
        tempProjectRoot   = settings.getDefaultProjectRoot();
        tempLogDir        = settings.getDefaultLogDir();


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


        // set project root directory
        projectRootField.setText(tempProjectRoot);

        // set log directory
        logDirField.setText(tempLogDir);

        // ------------------------ Open On Starup -----------------------------------
        boolean startupEnabled = StartupManager.isStartupEnabled();
        openOnStartupCheck.setSelected(startupEnabled);

        // Sync preferences with reality
        settings.setOpenOnStartup(startupEnabled);
        tempOpenOnStartup = startupEnabled;

        // ------------------------ Button Actions ------------------------------------
        btnCancel.setOnAction(e -> cancelAndClose());
        btnSave.setOnAction(e -> saveAndClose());
        btnApply.setOnAction(e -> applyChanges());
        btnRestoreDefaults.setOnAction(e -> restoreDefaults());

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

        // save project root folder
        String newRoot = projectRootField.getText().trim();
        if (!newRoot.equals(settings.getDefaultProjectRoot())) 
        {
            settings.setDefaultProjectRoot(newRoot);
        }

        // save log directory
        String newLogDir = logDirField.getText().trim();
        if (!newLogDir.equals(settings.getDefaultLogDir())) 
        {
            settings.setDefaultLogDir(newLogDir);
            // when log directory changes, restart required
            showRestartRequiredAlert();
        }

        // Open on Startup toggle
        boolean newValue = openOnStartupCheck.isSelected();
        if (newValue != settings.getOpenOnStartup()) 
        {
            boolean success = newValue ? StartupManager.addToStartup() : StartupManager.removeFromStartup();
            if (success) 
            {
                settings.setOpenOnStartup(newValue);
                tempOpenOnStartup = newValue;
                logger.info(newValue ? " [Settings Controller] Lensora Studio will now start automatically when you log in."
                        : "[Settings Controller] Lensora Studio will no longer start automatically.");
            }
            else 
            {
                openOnStartupCheck.setSelected(settings.getOpenOnStartup());
                // If in development mode, provide a helpful message
                if (StartupManager.isDevelopmentMode()) 
                {
                    logger.info("This feature is only available after packaging the application. Please use the installed version to enable automatic startup.");
                }
                else
                {
                    logger.error("Failed to update startup setting. Please check permissions and try again.");
                }
            }
        }
    }

    private void showRestartRequiredAlert() 
    {
        Stage owner = (Stage) logDirField.getScene().getWindow();
                Dialogs.showInfo(owner,
                "Restart Required",
                "Log directory updated",
                "The new log directory will take effect after you restart the application."
            );
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

        // Revert project root (just for UI consistency)
        projectRootField.setText(settings.getDefaultProjectRoot());

        // Revert log directory
        logDirField.setText(settings.getDefaultLogDir());

        // Revert open on startup
        openOnStartupCheck.setSelected(settings.getOpenOnStartup());

        closeWindow();
    }

    private void restoreDefaults()
    {
        // Reset temporary values to defaults
        tempTheme         = AppSettings.DEFAULT_THEME;
        tempFontSize      = AppSettings.DEFAULT_FONT_SIZE;
        tempProjectRoot   = AppSettings.DEFAULT_PROJECT_ROOT;
        tempLogDir        = AppSettings.DEFAULT_LOG_DIR;
        tempOpenOnStartup = AppSettings.DEFAULT_OPEN_ON_STARTUP;

        // Update UI
        themeCombo.setValue(tempTheme);
        fontSizeSpinner.getValueFactory().setValue(tempFontSize);
        projectRootField.setText(tempProjectRoot);
        logDirField.setText(tempLogDir);

        // Immediately preview the defaults
        ThemeManager.applyFontSizeToScene(fontSizeSpinner.getScene(), tempFontSize);
        // Also apply default theme to this window (but not save)
        ThemeManager.applyTheme(tempTheme);
        ThemeManager.applyFontSizeToAllWindows(tempFontSize);

        // Reset open on startup
        openOnStartupCheck.setSelected(tempOpenOnStartup);
    }

    @FXML
    private void closeWindow() 
    {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void onBrowseDefaultRoot() 
    {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Default Project Root Folder");
        // Start from the current value if it exists
        String current = projectRootField.getText();
        if (current != null && !current.isEmpty()) 
        {
            File currentDir = new File(current);
            if (currentDir.exists()) 
            {
                chooser.setInitialDirectory(currentDir);
            }
        }
        File selected = chooser.showDialog(projectRootField.getScene().getWindow());
        if (selected != null)
        {
            projectRootField.setText(selected.getAbsolutePath());
            // Optionally keep the temporary variable in sync with what’s 
            // displayed in the text field
            tempProjectRoot = selected.getAbsolutePath();
        }
    }

    @FXML
    private void onBrowseLogDir() 
    {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Default Log Directory");
        String current = logDirField.getText();
        if (current != null && !current.isEmpty()) 
        {
            File currentDir = new File(current);
            if (currentDir.exists()) 
            {
                chooser.setInitialDirectory(currentDir);
            }
        }
        File selected = chooser.showDialog(logDirField.getScene().getWindow());
        if (selected != null) 
        {
            logDirField.setText(selected.getAbsolutePath());
            // Optionally keep the temporary variable in sync with what’s 
            // displayed in the text field
            tempLogDir = selected.getAbsolutePath();
        }
    }
}