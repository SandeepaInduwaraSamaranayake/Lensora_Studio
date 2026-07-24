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

    // ----------------------------- FXML fields --------------------------------

    @FXML 
    private ComboBox<AppSettings.Theme>     themeCombo;

    @FXML 
    private Spinner<Double>                 fontSizeSpinner;

    @FXML private Spinner<Integer>          searchDebounceSpinner;

    @FXML
    private Button                          btnCancel, 
                                            btnSave, 
                                            btnApply, 
                                            btnRestoreDefaults, 
                                            btnBrowseDefaultRoot, 
                                            btnBrowseLogDir;

    @FXML 
    private HBox                            prefHeaderBar;

    @FXML 
    private TextField                       projectRootField, 
                                            logDirField;

    @FXML
    private CheckBox                        openOnStartupCheck, 
                                            openLastProjectCheck, 
                                            clearSearchOnProjectSelectCheck,
                                            resetStatusOnClearSearchCheck,
                                            showMetadataImagePreviewCheck;

    private final AppSettings settings = AppSettings.getInstance();

    private Runnable            onSettingsApplied;

    // ---------------- Temporary copies to revert on Cancel --------------------
    private AppSettings.Theme   tempTheme;
    private double              tempFontSize;
    private String              tempProjectRoot;
    private String              tempLogDir;
    private boolean             tempOpenOnStartup;
    private boolean             tempClearSearchOnSelect;
    private boolean             tempOpenLastProject;
    private boolean             tempResetStatusOnClearSearch;
    private int                 tempSearchDebounce;
    private boolean             tempShowImagePreviewInMetadata;

    // ----------------------------- DialogController ---------------------------
    @Override
    public Node getHeaderNode()
    {
        return prefHeaderBar;
    }

    // ----------------------------- Initialisation ----------------------------
    @FXML
    public void initialize()
    {
        logger.info("[SettingsController] Initializing SettingsController...");

        loadCurrentSettingsIntoTemp();
        setupThemeCombo();
        setupFontSizeSpinner();
        setupSearchDebounceSpinner();
        updateUIFromTemp();
        setupButtonActions();
    }

    private void loadCurrentSettingsIntoTemp() 
    {
        // Load current settings into temp variables
        tempTheme                       = settings.getTheme();
        tempFontSize                    = settings.getFontSize();
        tempProjectRoot                 = settings.getDefaultProjectRoot();
        tempLogDir                      = settings.getDefaultLogDir();
        tempOpenOnStartup               = settings.getOpenOnStartup();
        tempOpenLastProject             = settings.getOpenLastProject();
        tempClearSearchOnSelect         = settings.getClearSearchOnProjectSelect();
        tempResetStatusOnClearSearch    = settings.getResetStatusOnClearSearch();
        tempSearchDebounce              = settings.getSearchDebounceMs();
        tempShowImagePreviewInMetadata  = settings.getShowMetadataImagePreview();
    }

    private void setupThemeCombo() 
    {
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

        themeCombo.setValue(tempTheme);
    }

    private void setupFontSizeSpinner() 
    {
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
    }


    private void setupSearchDebounceSpinner() 
    {
        SpinnerValueFactory<Integer> debounceFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1000, tempSearchDebounce, 50);
        searchDebounceSpinner.setValueFactory(debounceFactory);

        searchDebounceSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            tempSearchDebounce = newVal;
        });
    }

    private void updateUIFromTemp() 
    {
        // set project root directory
        projectRootField.setText(tempProjectRoot);

        // set log directory
        logDirField.setText(tempLogDir);

        // Reset Status On Clear Search
        resetStatusOnClearSearchCheck.setSelected(tempResetStatusOnClearSearch);

        // Clear Search On Project Select
        clearSearchOnProjectSelectCheck.setSelected(tempClearSearchOnSelect);

        // set image preview in metedata
        showMetadataImagePreviewCheck.setSelected(tempShowImagePreviewInMetadata);

        // Open Last Project
        openLastProjectCheck.setSelected(tempOpenLastProject);

        // Sync the actual open-on-startup with the system (in case it changed externally)
        boolean actual = StartupManager.isStartupEnabled();
        if (actual != tempOpenOnStartup) 
        {
            tempOpenOnStartup = actual;
            openOnStartupCheck.setSelected(actual);
            settings.setOpenOnStartup(actual); // keep preferences aligned
        }
    }

    private void setupButtonActions() 
    {
        btnCancel.setOnAction(e -> cancelAndClose());
        btnSave.setOnAction(e -> saveAndClose());
        btnApply.setOnAction(e -> applyChanges());
        btnRestoreDefaults.setOnAction(e -> restoreDefaults());
        btnBrowseDefaultRoot.setOnAction(e -> browseFolder(projectRootField, "Select Default Project Root Folder"));
        btnBrowseLogDir.setOnAction(e -> browseFolder(logDirField, "Select Default Log Directory"));
    }



    // -------- Save changes to preferences and apply to all windows ----------------
    private void applyChanges()
    {
        applyThemeAndFont();
        applySearchDebounce();
        applyProjectRoot();
        applyLogDirectory();
        applyStartupBehaviour();
        applyUiBehaviour();
        if (onSettingsApplied != null)  onSettingsApplied.run();
        logger.info("[SettingsController] Settings applied.");
    }

    private void applyThemeAndFont() 
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

    private void applySearchDebounce() 
    {
        // Save search debounce (already stored in tempSearchDebounce)
        if (tempSearchDebounce != settings.getSearchDebounceMs()) 
        {
            settings.setSearchDebounceMs(tempSearchDebounce);
        }
    }

    private void applyProjectRoot() 
    {
        // save project root folder
        String newRoot = projectRootField.getText().trim();
        if (!newRoot.equals(settings.getDefaultProjectRoot())) 
        {
            settings.setDefaultProjectRoot(newRoot);
        }
    }

    private void applyLogDirectory() 
    {
        // save log directory
        String newLogDir = logDirField.getText().trim();
        if (!newLogDir.equals(settings.getDefaultLogDir())) 
        {
            settings.setDefaultLogDir(newLogDir);
            // when log directory changes, restart required
            Dialogs.showInfo(logDirField.getScene().getWindow(),
                "Restart Required",
                "Log directory updated",
                "The new log directory will take effect after you restart the application."
            );
        }
    }

    private void applyStartupBehaviour() 
    {
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

    private void applyUiBehaviour() 
    {
        // Clear search on project select toggle
        settings.setClearSearchOnProjectSelect(clearSearchOnProjectSelectCheck.isSelected());

        // Open last project toggle
        settings.setOpenLastProject(openLastProjectCheck.isSelected());

        // Reset status on clear search toggle
        settings.setResetStatusOnClearSearch(resetStatusOnClearSearchCheck.isSelected());

        // apply show imeage preview in metadata section
        settings.setShowMetadataImagePreview(showMetadataImagePreviewCheck.isSelected());
    }

    private void saveAndClose()
    {
        applyChanges();
        closeWindow();
    }

    private void cancelAndClose() 
    {
        // Revert temporary state to saved settings
        loadCurrentSettingsIntoTemp();
        updateUIFromTemp();

        // Reapply theme and font to this window only (live preview undone)
        ThemeManager.applyFontSizeToAllWindows(settings.getFontSize());
        ThemeManager.applyTheme(settings.getTheme());

        closeWindow();
    }

    private void restoreDefaults()
    {
        // Reset temporary values to defaults
        tempTheme                        = AppSettings.DEFAULT_THEME;
        tempFontSize                     = AppSettings.DEFAULT_FONT_SIZE;
        tempProjectRoot                  = AppSettings.DEFAULT_PROJECT_ROOT;
        tempLogDir                       = AppSettings.DEFAULT_LOG_DIR;
        tempOpenOnStartup                = AppSettings.DEFAULT_OPEN_ON_STARTUP;
        tempOpenLastProject              = AppSettings.DEFAULT_OPEN_LAST_PROJECT;
        tempClearSearchOnSelect          = AppSettings.DEFAULT_CLEAR_SEARCH_ON_PROJECT_SELECT;
        tempResetStatusOnClearSearch     = AppSettings.DEFAULT_RESET_STATUS_ON_CLEAR_SEARCH;
        tempSearchDebounce               = AppSettings.DEFAULT_SEARCH_DEBOUNCE_MS;
        tempShowImagePreviewInMetadata   = AppSettings.DEFAULT_SHOW_METADATA_PREVIEW;

        // Update UI
        themeCombo.setValue(tempTheme);

        // Font-size 
        fontSizeSpinner.getValueFactory().setValue(tempFontSize);

        // project root
        projectRootField.setText(tempProjectRoot);

        // log directory
        logDirField.setText(tempLogDir);

        // Reset open on startup
        openOnStartupCheck.setSelected(tempOpenOnStartup);

        // Open last project
        openLastProjectCheck.setSelected(tempOpenLastProject);

        // Reset show metadata preview
        showMetadataImagePreviewCheck.setSelected(tempShowImagePreviewInMetadata);

        // Reset clear search on project select
        clearSearchOnProjectSelectCheck.setSelected(tempClearSearchOnSelect);

        // Reset status on clear search
        resetStatusOnClearSearchCheck.setSelected(tempResetStatusOnClearSearch);

        // Reset search debounce
        searchDebounceSpinner.getValueFactory().setValue(tempSearchDebounce);

        // Immediately preview the defaults
        ThemeManager.applyFontSizeToScene(fontSizeSpinner.getScene(), tempFontSize);
        // Also apply default theme to this window (but not save)
        ThemeManager.applyTheme(tempTheme);
        ThemeManager.applyFontSizeToAllWindows(tempFontSize);
    }

    private void browseFolder(TextField target, String title) 
    {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        String current = target.getText();
        if (current != null && !current.isEmpty()) 
        {
            File dir = new File(current);
            if (dir.exists()) chooser.setInitialDirectory(dir);
        }
        File selected = chooser.showDialog(target.getScene().getWindow());
        if (selected != null) 
        {
            target.setText(selected.getAbsolutePath());
            // Optionally keep the temporary variable in sync with what’s 
            // displayed in the text field
            // Update the corresponding temp variable
            if (target == projectRootField) tempProjectRoot = selected.getAbsolutePath();
            else if (target == logDirField) tempLogDir = selected.getAbsolutePath();
        }
    }

    @FXML
    private void closeWindow() 
    {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        stage.close();
    }

    // ----------------------------- Callback for MainController ----------------
    public void setOnSettingsApplied(Runnable callback) 
    {
        this.onSettingsApplied = callback;
    }
}