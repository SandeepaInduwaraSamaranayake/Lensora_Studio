package com.lensora.lensorastudio.controller;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.repository.FolderTemplateRepository;
import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.services.ThemeManager;
import com.lensora.lensorastudio.util.Dialogs;
import com.lensora.lensorastudio.util.ImageCache;
import com.lensora.lensorastudio.util.NotificationUtil;
import com.lensora.lensorastudio.util.StartupManager;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;

public class SettingsController implements DialogController
{
    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);
    
    // Constant for the empty template state with a Sentinel Object
    private static final FolderTemplateRepository.FolderTemplate NONE_TEMPLATE = 
            new FolderTemplateRepository.FolderTemplate(-1, "(None - empty folder)", null);

    // ----------------------------- FXML fields --------------------------------

    @FXML 
    private ComboBox<AppSettings.Theme>                     themeCombo;

    @FXML private ComboBox<AppSettings.ImageQuality>        imageViewerPreviewQualityCombo;

    @FXML private ComboBox<String>                          defaultStatusCombo;

    @FXML private ComboBox<FolderTemplateRepository.FolderTemplate> defaultTemplateCombo;

    @FXML 
    private ComboBox<Integer>                               metadataPreviewQualityCombo;

    @FXML 
    private Spinner<Double>                                 fontSizeSpinner,
                                                            zoomSensitivitySpinner;

    @FXML private Spinner<Integer>                          searchDebounceSpinner, 
                                                            folderSaveDebounceSpinner,
                                                            cacheSizeSpinner;

    @FXML
    private Button                                          btnCancel, 
                                                            btnSave, 
                                                            btnApply, 
                                                            btnRestoreDefaults, 
                                                            btnBrowseDefaultRoot, 
                                                            btnBrowseLogDir;

    @FXML 
    private HBox                                            prefHeaderBar;

    @FXML 
    private TextField                                       projectRootField, 
                                                            logDirField;

    @FXML
    private CheckBox                                        openOnStartupCheck, 
                                                            openLastProjectCheck, 
                                                            clearSearchOnProjectSelectCheck,
                                                            resetStatusOnClearSearchCheck,
                                                            showMetadataImagePreviewCheck;

    private final AppSettings settings = AppSettings.getInstance();

    private Runnable            onSettingsApplied;

    // ---------------- Temporary copies to revert on Cancel --------------------
    private AppSettings.Theme               tempTheme;
    private double                          tempFontSize;
    private String                          tempProjectRoot;
    private String                          tempLogDir;
    private boolean                         tempOpenOnStartup;
    private boolean                         tempClearSearchOnSelect;
    private boolean                         tempOpenLastProject;
    private boolean                         tempResetStatusOnClearSearch;
    private int                             tempSearchDebounce;
    private int                             tempFolderSaveDebounce;
    private boolean                         tempShowImagePreviewInMetadata;
    private int                             tempMetadataPreviewSize;
    private int                             tempCacheSize;
    private double                          tempZoomSensitivity;
    private AppSettings.ImageQuality        tempImageViewerQuality;
    private int                             tempDefaultTemplateId;
    private String                          tempDefaultStatus;

    // ----------------------------- Initialization ----------------------------
    @FXML
    public void initialize()
    {
        logger.info("[SettingsController] Initializing SettingsController...");

        loadCurrentSettingsIntoTemp();
        setupThemeCombo();
        setupFontSizeSpinner();
        setupSearchDebounceSpinner();
        setupFolderSaveDebounceSpinner();
        setupMetadataPreviewQuality();
        setupCacheSizeSpinner();
        setupZoomSensitivitySpinner();
        updateUIFromTemp();
        setupButtonActions();
        setupImageViewerQualityCombo();
        setupProjectDefaultsCombos();
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
        tempFolderSaveDebounce          = settings.getFolderSaveDelayMs();
        tempShowImagePreviewInMetadata  = settings.getShowMetadataImagePreview();
        tempMetadataPreviewSize         = settings.getMetadataPreviewSize();
        tempCacheSize                   = settings.getImageCacheSize();
        tempZoomSensitivity             = settings.getZoomSensitivity();
        tempImageViewerQuality          = settings.getImageViewerQuality();
        tempDefaultTemplateId           = settings.getDefaultFolderTemplateId();
        tempDefaultStatus               = settings.getDefaultProjectStatus();
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

        themeCombo.valueProperty().addListener((obs, old, val) -> {
            if (val != null) tempTheme = val;
        });
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

    private void setupFolderSaveDebounceSpinner() 
    {
        SpinnerValueFactory<Integer> debounceFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 1000, tempFolderSaveDebounce, 50);
        folderSaveDebounceSpinner.setValueFactory(debounceFactory);

        folderSaveDebounceSpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            tempFolderSaveDebounce = newVal;
        });
    }

    private void setupZoomSensitivitySpinner()
    {
        SpinnerValueFactory<Double> factory =
                new SpinnerValueFactory.DoubleSpinnerValueFactory(1.05, 2.0, tempZoomSensitivity, 0.05);
        zoomSensitivitySpinner.setValueFactory(factory);
        zoomSensitivitySpinner.valueProperty().addListener((obs, old, val) -> tempZoomSensitivity = val);
    }

    private void setupMetadataPreviewQuality()
    {
        List<Integer> sizes = List.of(100, 200, 400, 600, 800, 1200);
        metadataPreviewQualityCombo.getItems().addAll(sizes);
        metadataPreviewQualityCombo.setValue(tempMetadataPreviewSize);
        metadataPreviewQualityCombo.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Integer item, boolean empty) 
            {
                super.updateItem(item, empty);
                setText(item == null ? "" : item + "px");
            }
        });
        metadataPreviewQualityCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) tempMetadataPreviewSize = newVal;
        });
    }

    private void setupCacheSizeSpinner() 
    {
        SpinnerValueFactory<Integer> valueFactory = new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 2000, tempCacheSize, 50);
        cacheSizeSpinner.setValueFactory(valueFactory);
        cacheSizeSpinner.valueProperty().addListener((obs, old, val) -> {
            tempCacheSize = val;
        });
    }

    private void updateUIFromTemp() 
    {
        // set theme
        themeCombo.setValue(tempTheme);

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

        // Reset preview quality
        metadataPreviewQualityCombo.setValue(tempMetadataPreviewSize);

        // Reset cache size
        cacheSizeSpinner.getValueFactory().setValue(tempCacheSize);

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

    private void setupImageViewerQualityCombo()
    {
        imageViewerPreviewQualityCombo.getItems().addAll(AppSettings.ImageQuality.values());
        imageViewerPreviewQualityCombo.setValue(tempImageViewerQuality);
        imageViewerPreviewQualityCombo.valueProperty().addListener((obs, old, val) -> tempImageViewerQuality = val);
    }

    private void setupProjectDefaultsCombos()
    {
        // Status combo - static list, same source as NewProjectController
        defaultStatusCombo.getItems().setAll(Project.ALL_STATUSES);
        defaultStatusCombo.setValue(tempDefaultStatus);
        defaultStatusCombo.valueProperty().addListener((obs, old, val) -> tempDefaultStatus = val);

        // Template combo - loaded from DB, same source as NewProjectController
        try
        {
            List<FolderTemplateRepository.FolderTemplate> templates = FolderTemplateRepository.findAll();
            
            defaultTemplateCombo.getItems().clear();
            defaultTemplateCombo.getItems().add(NONE_TEMPLATE);
            defaultTemplateCombo.getItems().addAll(templates);

            // Tell JavaFX how to render the object in the dropdown
            defaultTemplateCombo.setConverter(new StringConverter<>() {
                @Override
                public String toString(FolderTemplateRepository.FolderTemplate template) 
                {
                    return template == null ? "" : template.name();
                }

                @Override
                public FolderTemplateRepository.FolderTemplate fromString(String string) 
                {
                    return null;
                }
            });

            // Resolve current selection by ID
            FolderTemplateRepository.FolderTemplate currentSelection = templates.stream()
                    .filter(t -> t.id() == tempDefaultTemplateId)
                    .findFirst()
                    .orElse(NONE_TEMPLATE);

            defaultTemplateCombo.setValue(currentSelection);

            // Clean listener using type-safe IDs
            defaultTemplateCombo.valueProperty().addListener((obs, old, val) -> {
                tempDefaultTemplateId = (val != null) ? val.id() : -1;
            });
        }
        catch (SQLException e)
        {
            logger.error("Failed to load folder templates for settings", e);
        }
    }


    // -------- Save changes to preferences and apply to all windows ----------------
    private void applyChanges()
    {
        applyThemeAndFont();
        applySearchDebounce();
        applyFolderSaveDebounce();
        applyMetadataPreviewSize();
        applyCacheSize();
        applyZoomSensitivity();
        applyProjectRoot();
        applyLogDirectory();
        applyStartupBehaviour();
        applyUiBehaviour();
        applyImageViewerQuality();
        applyProjectDefaults();
        if (onSettingsApplied != null)  onSettingsApplied.run();
        NotificationUtil.showToast(getOwnerWindow(), "All Settings Applied");
        logger.info("[SettingsController] Settings applied.");
    }

    private void applyThemeAndFont()
    {
        // Save theme if changed
        AppSettings.Theme selectedTheme = tempTheme;
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

    private void applyFolderSaveDebounce() 
    {
        // Save search debounce (already stored in tempSearchDebounce)
        if (tempFolderSaveDebounce != settings.getFolderSaveDelayMs()) 
        {
            settings.setFolderSaveDelayMs(tempFolderSaveDebounce);
        }
    }

    private void applyMetadataPreviewSize()
    {
        // Save preview quality (already stored in tempMetadataPreviewSize)
        if (tempMetadataPreviewSize != settings.getMetadataPreviewSize())
        {
            settings.setMetadataPreviewSize(tempMetadataPreviewSize);
            ImageCache.clear();
        }
    }

    private void applyCacheSize() 
    {
        if (tempCacheSize != settings.getImageCacheSize()) 
        {
            settings.setImageCacheSize(tempCacheSize);
            ImageCache.setMaxEntries(tempCacheSize);
        }
    }

    private void applyZoomSensitivity()
    {
        if (tempZoomSensitivity != settings.getZoomSensitivity())
        {
            settings.setZoomSensitivity(tempZoomSensitivity);
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

    private void applyImageViewerQuality()
    {
        if (tempImageViewerQuality != settings.getImageViewerQuality())
        {
            settings.setImageViewerQuality(tempImageViewerQuality);
        }
    }

    private void applyProjectDefaults()
    {
        settings.setDefaultFolderTemplateId(tempDefaultTemplateId);
        settings.setDefaultProjectStatus(tempDefaultStatus);
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
        tempFolderSaveDebounce           = AppSettings.DEFAULT_FOLDER_SAVE_DELAY_MS;
        tempShowImagePreviewInMetadata   = AppSettings.DEFAULT_SHOW_METADATA_PREVIEW;
        tempMetadataPreviewSize          = AppSettings.DEFAULT_METADATA_PREVIEW_SIZE;        
        tempCacheSize                    = AppSettings.DEFAULT_IMAGE_CACHE_SIZE;
        tempZoomSensitivity              = AppSettings.DEFAULT_ZOOM_SENSITIVITY;
        tempImageViewerQuality           = AppSettings.ImageQuality.valueOf(AppSettings.DEFAULT_IMAGE_VIEWER_QUALITY);
        tempDefaultTemplateId            = AppSettings.DEFAULT_FOLDER_TEMPLATE_ID;
        tempDefaultStatus                = AppSettings.DEFAULT_PROJECT_STATUS;

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

        // Reset metadata preview quality 
        metadataPreviewQualityCombo.setValue(tempMetadataPreviewSize);

        // Reset cache size
        cacheSizeSpinner.getValueFactory().setValue(tempCacheSize);

        // Reset clear search on project select
        clearSearchOnProjectSelectCheck.setSelected(tempClearSearchOnSelect);

        // Reset status on clear search
        resetStatusOnClearSearchCheck.setSelected(tempResetStatusOnClearSearch);

        // Reset search debounce
        searchDebounceSpinner.getValueFactory().setValue(tempSearchDebounce);

        // Reset Zoom Sensitivity
        zoomSensitivitySpinner.getValueFactory().setValue(tempZoomSensitivity);

        // Reset folder save debounce
        folderSaveDebounceSpinner.getValueFactory().setValue(tempFolderSaveDebounce);

        // Reset image viewer quality
        imageViewerPreviewQualityCombo.setValue(tempImageViewerQuality);

        // Reset Template
        defaultTemplateCombo.setValue(NONE_TEMPLATE);

        // Reset Status
        defaultStatusCombo.setValue(tempDefaultStatus);

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
        Stage stage = (Stage) getOwnerWindow();
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    private Window getOwnerWindow()
    {
        return btnCancel.getScene() != null
                ? btnCancel.getScene().getWindow()
                : null;
    }

    // ----------------------------- Callback for MainController ----------------
    public void setOnSettingsApplied(Runnable callback) 
    {
        this.onSettingsApplied = callback;
    }
}