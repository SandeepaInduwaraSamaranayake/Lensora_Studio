package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.managers.FileManager;
import com.lensora.lensorastudio.managers.ProjectManager;
import com.lensora.lensorastudio.managers.WindowDragManager;
import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.services.LayoutPersistence;
import com.lensora.lensorastudio.util.DialogBuilder;
import com.lensora.lensorastudio.util.Dialogs;
import com.lensora.lensorastudio.util.ErrorHandler;
import com.lensora.lensorastudio.util.Resources;

import java.awt.Desktop;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;


public class MainController 
{

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // ─── FXML injected fields ──────────────────────────────────────────────

    @FXML private HBox headerBar;
    @FXML private MenuItem mnu_btn_exit, mnu_btn_about, mnu_btn_new_project, mnu_btn_preferences, mnu_btn_view_logs;
    @FXML private SplitPane mainSplitPane, projectWorkspace, fileSplitPane;

    // Project list
    @FXML private TableView<Project> projectTable;
    @FXML private TableColumn<Project, String> colProjectNumber, colClientName, colStatus;
    @FXML private Label lblProjectCount, lblStatusProjects, lblStatusText, lblStatusPath, lblFolderHeader;
    @FXML private HBox progressStepper;
    @FXML private VBox emptyStatePane;

    // Project details
    @FXML private TextField detProjectNumber, detClientName, detClientPhone, detClientEmail,
            detEventType, detEventDate, detDueDate, detStatus, detPackage, detProjectPath,
            detTotalAmount, detAdvanceAmount, detBalanceAmount;
    @FXML private TextArea detRemarks;

    // File browser
    @FXML private TreeView<File> folderTree;
    @FXML private TableView<File> fileTable;
    @FXML private TableColumn<File, String> colFileName, colFileType, colFileSize, colFileModified;
    @FXML private Label lblCurrentFolder, lblFileCount;
    @FXML private HBox progressContainer;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel, progressSpeedLabel, progressEtaLabel;

    @FXML private MenuItem ctxFileOpen, ctxFileRename, ctxFileCopy, ctxFileMove,
            ctxFileDelete, ctxFileShowInExplorer;

    @FXML private Button btnNewProject, btnEmptyNewProject, btnDetailOpenFolder;

    // ─── Managers ───────────────────────────────────────────────────────────

    private ProjectManager projectManager;
    private FileManager fileManager;

    // ─── Initialisation ─────────────────────────────────────────────────────

    @FXML
public void initialize() 
{
    logger.info("Initializing MainController...");
    
    // Split panes persistence
    registerSplitPanes();

    // Project manager
    projectManager = new ProjectManager
    (
            projectTable,
            colProjectNumber,
            colClientName, 
            colStatus,
            detProjectNumber,
            detClientName,
            detClientPhone,
            detClientEmail,
            detEventType,
            detEventDate,
            detDueDate,
            detStatus,
            detPackage,
            detProjectPath,
            detTotalAmount,
            detAdvanceAmount, 
            detBalanceAmount,
            detRemarks,
            progressStepper,
            lblStatusPath,
            lblFolderHeader,
            lblStatusProjects, 
            lblProjectCount,
            lblStatusText,
            projectWorkspace,
            emptyStatePane
    );

    // File manager
    fileManager = new FileManager
    (
            folderTree, 
            fileTable,
            colFileName, 
            colFileType, 
            colFileSize, 
            colFileModified,
            lblCurrentFolder, 
            lblFileCount, 
            lblFolderHeader,
            progressContainer, 
            progressBar, 
            progressLabel,
            progressSpeedLabel, 
            progressEtaLabel,
            ctxFileOpen, 
            ctxFileRename, 
            ctxFileCopy, 
            ctxFileMove,
            ctxFileDelete, 
            ctxFileShowInExplorer
    );

    // Wire project selection to file manager
    projectManager.setOnProjectSelected(() -> {
        Project current = projectManager.getCurrentProject();
        if (current != null) 
        {
            fileManager.loadProjectPath(current.getProjectPath());
        }
    });

    // Menu actions
    setupMenuItems();

    setupKeyboardShortcuts();

    // Button actions
    setupButtonActions();

    // Window drag manager
    WindowDragManager.attach(headerBar);

    // Load projects
    projectManager.refreshProjectList();
}

    // ─── Menu Actions ──────────────────────────────────────────────────────

    /**
     * Sets up the main menu item actions.
     * MENU BTN EXIT
     * MENU BTN PREFERENCES
     * MENU BTN ABOUT 
     */
    private void setupMenuItems() 
    {
        // Exit menu item handler
        if (mnu_btn_exit != null) 
        {
            mnu_btn_exit.setOnAction(e -> {
                logger.info("[Lensora] Exit menu item clicked, forwarding close request...");

                Stage stage = (Stage) headerBar.getScene().getWindow();
                if (stage != null)
                {
                    // Fire a formal close request to simulate the user clicking the OS close button
                    stage.fireEvent(new javafx.stage.WindowEvent(stage,
                            javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                }
            });
        }
        // Preferences menu item handler
        if (mnu_btn_preferences != null) 
        {
            mnu_btn_preferences.setOnAction(e -> showPreferencesWindow());
        }

        // About menu item handler
        if (mnu_btn_about != null) 
        {
            mnu_btn_about.setOnAction(e -> showAboutWindow());
        }

        // New Project item handler
        if (mnu_btn_new_project != null)
        {
            mnu_btn_new_project.setOnAction(e -> showNewProjectDialog());
        }

        // View Logs item handler
        if (mnu_btn_view_logs != null) 
        {
            mnu_btn_view_logs.setOnAction(e -> showLogViewer());
        }
    }

    // ─── Button Actions ─────────────────────────────────────────────────────

    /**
     * Sets up button actions (New Project, Open Folder, etc.)
     */
    private void setupButtonActions() 
    {
        if (btnNewProject != null) 
        {
            btnNewProject.setOnAction(e -> showNewProjectDialog());
        }
        if (btnEmptyNewProject != null) 
        {
            btnEmptyNewProject.setOnAction(e -> showNewProjectDialog());
        }
        if (btnDetailOpenFolder != null) 
        {
            btnDetailOpenFolder.setOnAction(e -> {
                Project current = projectManager.getCurrentProject();
                if (current != null && current.getProjectPath() != null) 
                {
                    try 
                    {
                        if (Desktop.isDesktopSupported()) 
                        {
                            Desktop.getDesktop().open(new File(current.getProjectPath()));
                        } 
                        else 
                        {
                            Dialogs.showInfo(null, 
                                            "Not Supported", 
                                            null,
                                            "Cannot open folder on this system.");
                        }
                    } 
                    catch (IOException ex) 
                    {
                        ErrorHandler.show(null, "Could not open folder", ex);
                    }
                }
            });
        }
    }

    /**
     * Sets up keyboard shortcuts (accelerators) for menu items.
     */
    private void setupKeyboardShortcuts() 
    {
        // Preferences: Ctrl + P
        if (mnu_btn_preferences != null) 
        {
            mnu_btn_preferences.setAccelerator(
                new KeyCodeCombination(KeyCode.P, KeyCombination.CONTROL_DOWN)
            );
        }

        // Exit: Ctrl + W
        if (mnu_btn_exit != null) 
        {
            mnu_btn_exit.setAccelerator(
                new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN)
            );
        }

        // About: Ctrl + Shift + A
        if (mnu_btn_about != null) 
        {
            mnu_btn_about.setAccelerator(
                new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)
            );
        }

        // New Project: Ctrl + N
        if (mnu_btn_new_project != null) 
        {
            mnu_btn_new_project.setAccelerator(
                new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN)
            );
        }

        // View Logs: Ctrl + Shift + L
        if (mnu_btn_view_logs != null) 
        {
            mnu_btn_view_logs.setAccelerator(
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)
            );
        }
}

    /**
     * Registers all split panes for layout persistence.
     */
    private void registerSplitPanes() 
    {
        LayoutPersistence.bindSplitPane("main.horizontal", mainSplitPane);
        LayoutPersistence.bindSplitPane("detail.vertical", projectWorkspace);
        LayoutPersistence.bindSplitPane("file.horizontal", fileSplitPane);
    }

    // ─── Dialog Show Methods ───────────────────────────────────────────────

    private void showPreferencesWindow() 
    {
        Stage mainStage = (Stage) headerBar.getScene().getWindow();
        DialogBuilder.of(Resources.SETTINGS_VIEW.url(), "Preferences", mainStage)
                .resizable(false)
                .build();
    }

    private void showAboutWindow() 
    {
        Stage mainStage = (Stage) headerBar.getScene().getWindow();
        DialogBuilder.of(Resources.ABOUT_VIEW.url(), "About Lensora Studio", mainStage)
                .resizable(false)
                .build();
    }

    private void showNewProjectDialog() 
    {
        Stage mainStage = (Stage) headerBar.getScene().getWindow();
        DialogBuilder.of(Resources.NEW_PROJECT_VIEW.url(), "New Project", mainStage)
                .resizable(false)
                .withControllerConsumer(controller -> {
                    if (controller instanceof NewProjectController) 
                    {
                        ((NewProjectController) controller).setOnProjectCreated(projectId -> {
                            // Refresh the project list after creation
                            projectManager.refreshProjectList();
                            // select the new project
                            for (Project p : projectTable.getItems()) 
                            {
                                if (p.getProjectId() == projectId)
                                {
                                    projectTable.getSelectionModel().select(p);
                                    break;
                                }
                            }
                            logger.info("Project list refreshed after creation.");
                        });
                    }
                })
                .build();
    }

    private void showLogViewer() 
    {
        Stage mainStage = (Stage) headerBar.getScene().getWindow();
        DialogBuilder.of(Resources.LOG_VIEWER_VIEW.url(), "Lensora Studio Log", mainStage)
                .resizable(true)  // allow resizing
                .modality(Modality.NONE)     // non‑modal so user can keep it open
                .withControllerConsumer(controller -> {
                    if (controller instanceof LogViewerController)
                    {
                        ((LogViewerController) controller).setStage(mainStage);
                    }
                })
                .build();
    }
}