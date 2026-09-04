package com.lensora.lensorastudio.ui.controller;

import com.drew.lang.annotations.Nullable;
import com.lensora.lensorastudio.app.App;
import com.lensora.lensorastudio.core.config.AppSettings;
import com.lensora.lensorastudio.core.config.Resources;
import com.lensora.lensorastudio.feature.backup.engine.BackupScheduler;
import com.lensora.lensorastudio.feature.backup.ui.BackupRestoreCenterController;
import com.lensora.lensorastudio.feature.explorer.controller.FileExplorerController;
import com.lensora.lensorastudio.feature.explorer.controller.FolderTemplateManagerController;
import com.lensora.lensorastudio.feature.explorer.controller.LogViewerController;
import com.lensora.lensorastudio.feature.explorer.workspace.WindowDragManager;
import com.lensora.lensorastudio.feature.explorer.workspace.WorkspaceDockingService;
import com.lensora.lensorastudio.feature.explorer.control.FileListingManager;
import com.lensora.lensorastudio.feature.explorer.control.FileManager;
import com.lensora.lensorastudio.feature.explorer.control.FileOperationsManager;
import com.lensora.lensorastudio.feature.project.controller.NewProjectController;
import com.lensora.lensorastudio.feature.project.controller.ProjectDetailsController;
import com.lensora.lensorastudio.feature.project.controller.ProjectListController;
import com.lensora.lensorastudio.feature.project.model.Project;
import com.lensora.lensorastudio.feature.project.repository.ProjectLastFolderRepository;
import com.lensora.lensorastudio.feature.project.viewmodel.ProjectsViewModel;
import com.lensora.lensorastudio.feature.settings.controller.SettingsController;
import com.lensora.lensorastudio.media.service.MetadataExtractionService;
import com.lensora.lensorastudio.ui.components.MetadataPanel;
import com.lensora.lensorastudio.ui.dialogs.DialogBuilder;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;
import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;
import com.lensora.lensorastudio.ui.viewmodel.StatusBarViewModel;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainController
{
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private HBox headerBar;
    @FXML private TextField projectSearchField;
    @FXML private StackPane dockHost, statusBarHost;

    @FXML private Menu mnu_view;
    @FXML private MenuItem mnu_btn_exit, mnu_btn_about, mnu_btn_new_project, mnu_btn_backup_center,
                        mnu_btn_preferences, mnu_btn_view_logs, mnu_btn_reset_layout, mnu_btn_new_template;
    @FXML private Button btnNewProject, btnNewTemplate, btnBackupCenter, btnPreferences, btnViewLogs;
    @FXML private ToggleButton btnLockLayout;
    @FXML private FontIcon lockLayoutIcon;
    @FXML private Tooltip lockLayoutTooltip;


    private final ProjectsViewModel projectsViewModel = new ProjectsViewModel();
    private final StatusBarViewModel statusBarViewModel = new StatusBarViewModel();
    private final WorkspaceDockingService dockingService = new WorkspaceDockingService();
    private final Map<String, CheckMenuItem> panelCheckItems = new HashMap<>();

    private final PauseTransition searchDelay = new PauseTransition(Duration.millis(100));
    private final PauseTransition folderSaveDelay = new PauseTransition(Duration.millis(400));

    CheckMenuItem lockLayoutMenuItem;

    private ProjectListController projectListController;
    private FileExplorerController fileExplorerController;
    private StatusBarController statusBarController;
    ProjectDetailsController detailsController;

    /** Set by {@link App#start(Stage)} before any dialogs are shown. */
    @Nullable
    private Stage mainStage;

    @FXML
    public void initialize()
    {
        logger.info("[MainController] Initializing shell...");

        loadStatusBar();
        loadDockablePanels();
        setupPanelsMenu();
        setupLayoutLockToggle();
        setupMenuItems();
        setupKeyboardShortcuts();
        setupSearchField();
        setupBackupSchedular();

        WindowDragManager.attach(headerBar);
        projectsViewModel.refresh();

    }

    // ─── Panel loading ──────────────────────────────────────────────────────

    private void loadStatusBar()
    {
        try
        {
            FXMLLoader loader = new FXMLLoader(Resources.STATUS_BAR_VIEW.url());
            Parent root = loader.load();
            statusBarController = loader.getController();
            statusBarController.bind(statusBarViewModel, projectsViewModel);
            statusBarHost.getChildren().setAll(root);
        }
        catch (IOException e)
        {
            logger.error("Failed to load status bar view", e);
        }
    }

    private void loadDockablePanels()
    {
        try
        {
            // Projects list Panel
            FXMLLoader listLoader = new FXMLLoader(Resources.PROJECT_LIST_VIEW.url());
            Node listRoot = listLoader.load();
            projectListController = listLoader.getController();
            projectListController.bind(projectsViewModel);
            projectListController.setOnRowClicked(projectSearchField::clear);
            dockingService.register("projects", listRoot, "Projects");

            // Project details Panel
            FXMLLoader detailsLoader = new FXMLLoader(Resources.PROJECT_DETAILS_VIEW.url());
            Node detailsRoot = detailsLoader.load();
            detailsController = detailsLoader.getController();
            detailsController.bind(projectsViewModel);
            dockingService.register("projectDetails", detailsRoot, "Project Details");

            // File explorer panel
            FXMLLoader explorerLoader = new FXMLLoader(Resources.FILE_EXPLORER_VIEW.url());
            Node explorerRoot = explorerLoader.load();
            fileExplorerController = explorerLoader.getController();
            fileExplorerController.wireProgressUi(
                    statusBarController.getProgressContainer(),
                    statusBarController.getProgressBar(),
                    statusBarController.getProgressLabel(),
                    statusBarController.getProgressSpeedLabel(),
                    statusBarController.getProgressEtaLabel());
            dockingService.register("files", explorerRoot, "Files");
            detailsController.setOnProjectPathChanged(path -> {fileExplorerController.loadProjectPath(path); statusBarViewModel.currentPathProperty().set(path);});
            // restore last-visited folder once the new project's folder tree is loaded
            detailsController.setOnRestoreLastFolder(fileExplorerController::restoreLastFolder);

            // project list controller callbacks for backup and archive actions
            projectListController.setOnBackupRequested(this::showBackupRestoreCenterForProjects);
            projectListController.setOnArchiveRequested(this::archiveProject);

            // setup last folder save to db
            setupLastFoldersave(fileExplorerController);
            
            FileManager fileManager = fileExplorerController.getFileManager();
            fileManager.setOnPathChanged(statusBarViewModel.currentPathProperty()::set);

            // MetaData Panel
            FileListingManager fileListing = fileExplorerController.getFileManager().getFileListingManager();
            FileOperationsManager fileOps = fileExplorerController.getFileManager().getFileOperationsManager();
            setupMetadataPanel(fileListing, fileOps);

            // Docking service refresh
            dockingService.setOnRebuildRequested(this::rebuildAndMount);
            dockingService.setOnNodeVisibilityChanged(this::refreshPanelsMenuChecks);
            rebuildAndMount();

            // After loading file explorer and docking service
            fileExplorerController.setSnapFX(dockingService.getSnapFX());
        }
        catch (IOException e)
        {
            logger.error("Failed to load dockable panels", e);
        }
    }

    /**
     * Creates and registers the Metadata panel, sets up the file selection listener,
     * and wires the right‑click handler to show the panel.
     */
    private void setupMetadataPanel(FileListingManager fileListing, FileOperationsManager fileOps)
    {
        // Create the panel content (initially shows placeholder)
        StackPane metadataContent = new StackPane();
        Label placeholder = new Label("Select a file to view metadata");
        metadataContent.getChildren().add(placeholder);

        // Register the panel with the docking service
        dockingService.register("metadata", metadataContent, "Metadata");

        // Listen to file selection and load metadata
        fileListing.selectedFileProperty().addListener((obs, oldFile, newFile) -> {
            if (newFile == null) 
            {
                metadataContent.getChildren().setAll(new Label("No file selected"));
                return;
            }

            Label loading = new Label("Loading metadata...");
            metadataContent.getChildren().setAll(loading);

            MetadataExtractionService.extractAsync(newFile,
                metadata -> Platform.runLater(() -> {
                    Node content = MetadataPanel.buildContent(metadata);
                    metadataContent.getChildren().setAll(content);
                }),
                error -> Platform.runLater(() -> {
                    Label errorLabel = new Label("Failed to read metadata: " + error.getMessage());
                    metadataContent.getChildren().setAll(errorLabel);
                })
            );
        });

        // Wire the right‑click "Metadata" action to show the panel
        fileOps.setShowMetadataHandler(file -> {
            // Update the selection – this triggers the listener to load metadata
            fileListing.selectedFileProperty().set(file);
            // Show the metadata panel if it was hidden
            dockingService.showPanel("metadata");
        });
    }

    // ─── Menu / shortcuts / search ─────
    private void setupMenuItems()
    {
        if (mnu_btn_exit != null)
        {
            mnu_btn_exit.setOnAction(e -> {
                if (mainStage != null)
                {
                    mainStage.fireEvent(new WindowEvent(mainStage, WindowEvent.WINDOW_CLOSE_REQUEST));
                }
            });
        }
        if (mnu_btn_preferences != null) mnu_btn_preferences.setOnAction(e -> showPreferencesWindow());
        if (mnu_btn_about != null) mnu_btn_about.setOnAction(e -> showAboutWindow());
        if (mnu_btn_new_project != null) mnu_btn_new_project.setOnAction(e -> showNewProjectDialog());
        if (mnu_btn_new_template != null) mnu_btn_new_template.setOnAction(e -> showTemplateManager());
        if (btnNewProject != null) btnNewProject.setOnAction(e -> showNewProjectDialog());
        if (btnNewTemplate != null) btnNewTemplate.setOnAction(e -> showTemplateManager());
        if (mnu_btn_backup_center != null) mnu_btn_backup_center.setOnAction(e -> showBackupRestoreCenter());
        if (btnBackupCenter != null) btnBackupCenter.setOnAction(e -> showBackupRestoreCenter());
        if (btnPreferences != null) btnPreferences.setOnAction(e -> showPreferencesWindow());
        if (btnViewLogs != null) btnViewLogs.setOnAction(e -> showLogViewer());
        if (mnu_btn_view_logs != null) mnu_btn_view_logs.setOnAction(e -> showLogViewer());
        if (mnu_btn_reset_layout != null)
        {
            mnu_btn_reset_layout.setOnAction(e ->
            {
                dockingService.createDefaultLayout();
                // Rebuild + remount so the reset actually takes effect on screen,
                // and refresh menu checkmarks since a reset also re-shows everything.
                dockHost.getChildren().setAll(dockingService.buildLayout());
                refreshPanelsMenuChecks();
                });
        }

        // lock layout menu handle
        if (mnu_view != null)
        {
            lockLayoutMenuItem = new CheckMenuItem("Lock Layout");
            lockLayoutMenuItem.setSelected(dockingService.isLocked());
            lockLayoutMenuItem.setOnAction(e -> {
                boolean locked = lockLayoutMenuItem.isSelected();
                dockingService.setLocked(locked);
                AppSettings.getInstance().setLayoutLocked(locked);
                if (btnLockLayout != null) btnLockLayout.setSelected(locked);
                updateLockLayoutVisuals(locked);
            });

            // Keep toolbar toggle and menu item in sync in both directions
            if (btnLockLayout != null)
            {
                btnLockLayout.selectedProperty().addListener((obs, old, val) -> lockLayoutMenuItem.setSelected(val));
            }

            mnu_view.getItems().add(2, lockLayoutMenuItem);
            mnu_view.getItems().add(3, new SeparatorMenuItem());
        }
    }

    private void setupKeyboardShortcuts()
    {
        if (mnu_btn_preferences != null)
            mnu_btn_preferences.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN));
        if (mnu_btn_exit != null)
            mnu_btn_exit.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
        if (mnu_btn_about != null)
            mnu_btn_about.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        if (mnu_btn_new_project != null)
            mnu_btn_new_project.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        if (mnu_btn_new_template != null)
            mnu_btn_new_template.setAccelerator(new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN));
        if (mnu_btn_view_logs != null)
            mnu_btn_view_logs.setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        if (lockLayoutMenuItem != null)
            lockLayoutMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.L, KeyCombination.SHIFT_DOWN));

        headerBar.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null && fileExplorerController != null)
            {
                fileExplorerController.setupCopyPasteShortcuts(newScene);
            }
        });

        projectSearchField.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null)
            {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                    if (e.isControlDown() && e.getCode() == KeyCode.F)
                    {
                        projectSearchField.requestFocus();
                        projectSearchField.selectAll();
                        e.consume();
                    }
                });
            }
        });
    }

    private void setupSearchField()
    {
        updateSearchDebounce();

        projectSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.trim().isEmpty()
                    && AppSettings.getInstance().getResetStatusOnClearSearch())
            {
                // handled implicitly: ProjectListController's status combo stays put;
                // if you want auto-reset behaviour, expose a resetStatusFilter() method
                // on ProjectListController and call it here.
            }
            searchDelay.setOnFinished(e -> projectsViewModel.searchProjects(newVal));
            searchDelay.playFromStart();
        });
    }

    private void setupBackupSchedular()
    {
        BackupScheduler.getInstance().setOnJobTriggered((scheduleName, job) -> {
            NotificationUtil.showToast(
                    mainStage,
                    "Scheduled backup \"" + scheduleName + "\" is running…");

            // submit job to track progress
            trackBackgroundTask("Scheduled Backup: " + scheduleName, job);

            job.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, e -> 
                NotificationUtil.showToast(
                    mainStage,
                    "Scheduled backup \"" + scheduleName + "\" completed.")
                );
            });
    }

    private void updateSearchDebounce()
    {
        int ms = AppSettings.getInstance().getSearchDebounceMs();
        searchDelay.setDuration(Duration.millis(ms));
    }

    // ─── Dialogs (unchanged) ────────────────────────────────────────────────

    private void showPreferencesWindow()
    {
        DialogBuilder.of(Resources.SETTINGS_VIEW.url(), "Preferences", mainStage)
                .icon("⚙")
                .resizable(false)
                .withControllerConsumer(controller -> {
                    if (controller instanceof SettingsController sc)
                    {
                        sc.setOnSettingsApplied(() -> {
                            this.updateSearchDebounce();
                            this.updateFolderSaveDebounce();
                            if (fileExplorerController != null) fileExplorerController.updateSearchDebounce();
                        });
                    }
                })
                .build();
    }

    private void showAboutWindow()
    {
        DialogBuilder.of(Resources.ABOUT_VIEW.url(), "About Lensora Studio", mainStage)
                .icon("🛈")
                .resizable(false)
                .build();
    }

    private void showNewProjectDialog()
    {
        DialogBuilder.of(Resources.NEW_PROJECT_VIEW.url(), "New Project", mainStage)
                .icon("📁")
                .resizable(false)
                .withControllerConsumer(controller -> {
                    if (controller instanceof NewProjectController npc)
                    {
                        npc.setOnProjectCreated(projectId -> {
                            projectsViewModel.refresh();
                            projectsViewModel.selectById(projectId);
                            logger.info("Project list refreshed after creation.");
                        });
                    }
                })
                .build();
    }

    private void showLogViewer()
    {
        DialogBuilder.of(Resources.LOG_VIEWER_VIEW.url(), "Lensora Studio Log", mainStage)
                .icon("📄")
                .resizable(true)
                .modality(Modality.NONE)
                .withControllerConsumer(controller -> {
                    if (controller instanceof LogViewerController lvc)
                    {
                        lvc.setStage(mainStage);
                    }
                })
                .build();
    }

    private void showTemplateManager()
    {
        DialogBuilder.of(Resources.FOLDER_TEMPLATE_MANAGER_VIEW.url(), "Manage Folder Templates", mainStage)
                .icon("📐")
                .resizable(true)
                .minSize(640, 460)
                .withControllerConsumer(controller -> {
                    if (controller instanceof FolderTemplateManagerController ftmc)
                    {
                        ftmc.setOnTemplatesChanged(() ->
                                logger.info("[MainController] Folder templates updated."));
                    }
                })
                .build();
    }

    private void showBackupRestoreCenter()
    {
        showBackupRestoreCenterForProjects(null);
    }

    /** Opens the center, optionally preselecting a project on the Backup tab (used by the Projects context menu). */
    private void showBackupRestoreCenterForProjects(List<Project> projects)
    {
        DialogBuilder.of(Resources.BACKUP_RESTORE_CENTER_VIEW.url(), "Lensora Backup & Restore Center", mainStage)
                .icon("🛡")
                .resizable(true)
                .minSize(1000, 750)
                .withControllerConsumer(controller -> {
                    if (controller instanceof BackupRestoreCenterController brcc)
                    {
                        brcc.initContext(projectsViewModel, () -> logger.info("[MainController] Backup/restore completed."));
                        brcc.preselectProjectsForBackup(projects);
                    }
                })
                .build();
    }

    /** Called by App.start() so sub-controllers with file dialogs know their owner. */
    public void setStage(Stage stage)
    {
        this.mainStage = stage;
        if (fileExplorerController != null) fileExplorerController.setStage(stage);
        if (detailsController != null) detailsController.setStage(stage);
        if (projectListController != null) projectListController.setStage(stage);
    }

    public WorkspaceDockingService getDockingService() 
    {
        return dockingService;
    }


    /** Builds View -> Panels with one CheckMenuItem per dockable panel. */
    private void setupPanelsMenu()
    {
        if (mnu_view == null) return;

        Menu panelsMenu = new Menu("Panels");

        for (Map.Entry<String, String> entry : dockingService.getRegisteredPanels().entrySet())
        {
            String id = entry.getKey();
            String title = entry.getValue();

            CheckMenuItem item = new CheckMenuItem(title);
            item.setSelected(dockingService.isVisible(id));

            item.setOnAction(e -> {
                if (item.isSelected()) dockingService.showPanel(id);
                else dockingService.hidePanel(id);
            });

            panelsMenu.getItems().add(item);
            panelCheckItems.put(id, item);
        }

        mnu_view.getItems().add(0, panelsMenu);
        mnu_view.getItems().add(1, new SeparatorMenuItem());
    }


    /** Re-syncs checkmarks after an operation that can change multiple panels' visibility at once (e.g. Reset Layout). */
    private void refreshPanelsMenuChecks()
    {
        for (Map.Entry<String, CheckMenuItem> entry : panelCheckItems.entrySet())
        {
            entry.getValue().setSelected(dockingService.isVisible(entry.getKey()));
        }
    }

    private void rebuildAndMount() 
    {
        dockHost.getChildren().setAll(dockingService.buildLayout());
        refreshPanelsMenuChecks();
    }

    // ─── Layout lock toggle ─────────────────────────────────────────────────

    private void setupLayoutLockToggle()
    {
        if (btnLockLayout == null) return;

        boolean savedLocked = AppSettings.getInstance().getLayoutLocked();
        dockingService.setLocked(savedLocked);
        btnLockLayout.setSelected(savedLocked);
        updateLockLayoutVisuals(savedLocked);

        btnLockLayout.setOnAction(e -> {
            boolean locked = btnLockLayout.isSelected();
            dockingService.setLocked(locked);
            AppSettings.getInstance().setLayoutLocked(locked);
            updateLockLayoutVisuals(locked);
        });
    }

    private void updateLockLayoutVisuals(boolean locked)
    {
        if (lockLayoutIcon != null)
        {
            lockLayoutIcon.setIconLiteral(locked ? "fas-lock" : "fas-lock-open");
        }
        if (lockLayoutTooltip != null)
        {
            lockLayoutTooltip.setText(locked ? "Unlock Layout (SHIFT + L)" : "Lock Layout (SHIFT + L)");
        }
    }

    private void setupLastFoldersave(FileExplorerController fileExplorerController)
    {
        updateFolderSaveDebounce();

        fileExplorerController.setOnNavigationPersisted(folder -> {
                Project current = projectsViewModel.getSelectedProject();
                if (current == null) return;

                String relative = fileExplorerController.getCurrentFolderRelativePath();
                if (relative == null) return;

                folderSaveDelay.setOnFinished(e -> {
                    // Save using the current project's ID
                    try 
                    {
                        ProjectLastFolderRepository.save(current.getProjectId(), relative);
                    } 
                    catch (SQLException ex)
                    {
                        logger.warn("Failed to save last-visited folder", ex);
                    }
                });
                folderSaveDelay.playFromStart();
            });
    }

    /** Archives a project: sets status to Closed (reusing existing Project.STATUS_CLOSED constant). */
    private void archiveProject(Project project)
    {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(mainStage);
        confirm.setTitle("Archive Project");
        confirm.setHeaderText(null);
        confirm.setContentText("Archive \"" + project.getProjectNumber() + "\"? "
                + "This sets its status to Closed. The project and its files are not deleted.");
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;
            try
            {
                com.lensora.lensorastudio.feature.project.repository.ProjectRepository.setStatus(
                        project.getProjectId(), Project.STATUS_CLOSED);
                projectsViewModel.refresh();
                NotificationUtil.showToast(mainStage,
                        "Project archived: " + project.getProjectNumber());
            }
            catch (java.sql.SQLException e)
            {
                ErrorHandler.show(mainStage, "Failed to archive project", e);
            }
        });
    }

    private void updateFolderSaveDebounce()
    {
        int ms = AppSettings.getInstance().getFolderSaveDelayMs();
        folderSaveDelay.setDuration(Duration.millis(ms));
    }

    /**
     * Tracks a background task in the status bar, showing its live progress/message and automatically hiding again when it finishes.
     * @param title The title to display in the status bar.
     * @param task The JavaFX Task to track.
     */
    public void trackBackgroundTask(String title, Task<?> task)
    {
        if(task == null) return;
        if (statusBarController != null)
        {
            statusBarController.trackTask(title, task);
        }
    }
}