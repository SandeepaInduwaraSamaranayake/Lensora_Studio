package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.backup.engine.*;
import com.lensora.lensorastudio.backup.verify.BackupVerifier;
import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.repository.ProjectRepository;
import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.util.Dialogs;
import com.lensora.lensorastudio.util.ErrorHandler;
import com.lensora.lensorastudio.util.NotificationUtil;
import com.lensora.lensorastudio.viewmodel.ProjectsViewModel;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BackupRestoreCenterController implements DialogController
{
    private static final Logger logger = LoggerFactory.getLogger(BackupRestoreCenterController.class);

    @FXML private TabPane tabPane;

    // Backup tab
    @FXML private ListView<Project> backupProjectListView;
    @FXML private TextField backupDestinationField;
    @FXML private Button btnBrowseBackupDestination, btnStartBackup;
    @FXML private VBox backupProgressBox;
    @FXML private Label backupStatusLabel;
    @FXML private ProgressBar backupProgressBar;

    // Restore tab
    @FXML private TextField restoreSourceField, restoreDestinationField;
    @FXML private Button btnBrowseRestoreSource, btnBrowseRestoreDestination, btnVerifyBackup, btnStartRestore;
    @FXML private Label restoreVerificationLabel;
    @FXML private VBox restoreProgressBox;
    @FXML private Label restoreStatusLabel;
    @FXML private ProgressBar restoreProgressBar;

    // History tab
    @FXML private ListView<String> historyListView;
    @FXML private Button btnRefreshHistory, btnOpenHistoryFolder, btnVerifyHistoryItem, btnRestoreHistoryItem;

    private final ObservableList<String> historyItems = FXCollections.observableArrayList();
    private File selectedBackupDestinationFolder;
    private File selectedRestoreSource;
    private File selectedRestoreDestination;

    private ProjectsViewModel projectsViewModel;
    private Runnable onProjectsChanged;

    /** Called by MainController right after DialogBuilder loads this controller. */
    public void initContext(ProjectsViewModel projectsViewModel, Runnable onProjectsChanged)
    {
        this.projectsViewModel = projectsViewModel;
        this.onProjectsChanged = onProjectsChanged;
        loadProjectsIntoList();
    }

    /** Preselects a project and jumps to the Backup tab — used by the Projects context menu. */
    public void preselectProjectsForBackup(List<Project> projects)
    {
        if (projects != null && !projects.isEmpty())
        {
            loadProjectsIntoList();
            backupProjectListView.getSelectionModel().clearSelection();
            for (Project p : projects)
            {
                backupProjectListView.getSelectionModel().select(p);
            }
        }
        tabPane.getSelectionModel().select(0);
        selectedBackupDestinationFolder = new File(System.getProperty("user.home"));
        backupDestinationField.setText(selectedBackupDestinationFolder.getAbsolutePath());
    }

    @FXML
    public void initialize()
    {
        setupBackupTab();
        setupRestoreTab();
        setupHistoryTab();
    }

    // ─── Backup tab ─────────────────────────────────────────────────────────

    private void setupBackupTab()
{
    backupProjectListView.setCellFactory(lv -> new ListCell<>() {
        @Override
        protected void updateItem(Project p, boolean empty)
        {
            super.updateItem(p, empty);
            setText(empty || p == null ? null : p.getProjectNumber() + " — " + p.getClientName());
        }
    });
    backupProjectListView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

    btnBrowseBackupDestination.setOnAction(e -> browseBackupDestinationFolder());
    btnStartBackup.setOnAction(e -> startBackup());
}

    private void loadProjectsIntoList()
    {
        try
        {
            List<Project> projects = ProjectRepository.findAll();
            backupProjectListView.setItems(FXCollections.observableArrayList(projects));
        }
        catch (SQLException e)
        {
            logger.error("Failed to load projects for backup", e);
        }
    }

    private void browseBackupDestinationFolder()
    {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Backup Destination Folder");
        File result = chooser.showDialog(getStage());
        if (result != null)
        {
            selectedBackupDestinationFolder = result;
            backupDestinationField.setText(result.getAbsolutePath());
        }
    }

    private void startBackup()
    {
        List<Project> selected = new ArrayList<>(backupProjectListView.getSelectionModel().getSelectedItems());
        if (selected.isEmpty())
        {
            Dialogs.showInfo(getStage(), "Backup", null, "Please select at least one project.");
            return;
        }
        if (selectedBackupDestinationFolder == null)
        {
            Dialogs.showInfo(getStage(), "Backup", null, "Please choose a destination folder.");
            return;
        }

        List<BatchBackupJob.BatchItem> items = new ArrayList<>();
        for (Project project : selected)
        {
            String fileName = BackupService.suggestFileName(project);
            items.add(new BatchBackupJob.BatchItem(project, new File(selectedBackupDestinationFolder, fileName)));
        }

        BatchBackupJob job = BackupService.createBatchBackupJob(items);
        bindProgress(job, backupProgressBox, backupStatusLabel, backupProgressBar);
        setBackupControlsDisabled(true);

        job.setOnSucceeded(e -> {
            setBackupControlsDisabled(false);
            List<File> completed = job.getValue();

            int verifiedCount = 0;
            for (File file : completed)
            {
                var verification = BackupVerifier.verify(file);
                AppSettings.getInstance().addBackupHistoryPath(file.getAbsolutePath());
                if (verification.success()) verifiedCount++;
            }
            refreshHistoryList();

            long failedCount = job.getResults().stream().filter(r -> !r.succeeded()).count();
            String summary = String.format("%d/%d backup(s) succeeded and verified.",
                    verifiedCount, items.size());
            if (failedCount > 0)
            {
                summary += " " + failedCount + " failed - see log for details.";
                for (var result : job.getResults())
                {
                    if (!result.succeeded())
                    {
                        logger.error("Backup failed for {}: {}", result.project().getProjectNumber(), result.errorMessage());
                    }
                }
            }

            NotificationUtil.showToast(getStage(), summary);
        });
        job.setOnFailed(e -> {
            setBackupControlsDisabled(false);
            ErrorHandler.show(getStage(), "Batch backup failed", job.getException());
        });
        job.setOnCancelled(e -> setBackupControlsDisabled(false));

        runTask(job);
    }

    private void setBackupControlsDisabled(boolean disabled)
    {
        backupProjectListView.setDisable(disabled);
        btnBrowseBackupDestination.setDisable(disabled);
        btnStartBackup.setDisable(disabled);
    }
    // ─── Restore tab ────────────────────────────────────────────────────────

    private void setupRestoreTab()
    {
        btnBrowseRestoreSource.setOnAction(e -> browseRestoreSource());
        btnBrowseRestoreDestination.setOnAction(e -> browseRestoreDestination());
        btnVerifyBackup.setOnAction(e -> verifySelectedBackup());
        btnStartRestore.setOnAction(e -> startRestore());
    }

    private void browseRestoreSource()
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup File");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Lensora Backup", "*.lsbak"));
        File result = chooser.showOpenDialog(getStage());
        if (result != null)
        {
            selectedRestoreSource = result;
            restoreSourceField.setText(result.getAbsolutePath());
            restoreVerificationLabel.setText("");
        }
    }

    private void browseRestoreDestination()
    {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Restore Location");
        File result = chooser.showDialog(getStage());
        if (result != null)
        {
            selectedRestoreDestination = result;
            restoreDestinationField.setText(result.getAbsolutePath());
        }
    }

    private void verifySelectedBackup()
    {
        loadBackupForVerification(selectedRestoreSource);
    }

    private void loadBackupForVerification(File file)
    {
        if (file == null)
        {
            Dialogs.showInfo(getStage(), "Verify", null, "Please select a .lsbak file first.");
            return;
        }
        var result = BackupVerifier.verify(file);
        restoreVerificationLabel.setText((result.success() ? "✓ " : "✗ ") + result.message());
        restoreVerificationLabel.setStyle(result.success()
                ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    }

    private void startRestore()
    {
        if (selectedRestoreSource == null)
        {
            Dialogs.showInfo(getStage(), "Restore", null, "Please select a .lsbak file.");
            return;
        }
        if (selectedRestoreDestination == null)
        {
            Dialogs.showInfo(getStage(), "Restore", null, "Please select a destination folder.");
            return;
        }

        RestoreJob job = RestoreService.createRestoreJob(selectedRestoreSource, selectedRestoreDestination);
        bindProgress(job, restoreProgressBox, restoreStatusLabel, restoreProgressBar);
        setRestoreControlsDisabled(true);

        job.setOnSucceeded(e -> {
            setRestoreControlsDisabled(false);
            int newProjectId = job.getValue();
            if (projectsViewModel != null)
            {
                projectsViewModel.refresh();
                projectsViewModel.selectById(newProjectId);
            }
            if (onProjectsChanged != null) onProjectsChanged.run();
            NotificationUtil.showToast(getStage(), "Project restored successfully.");
        });
        job.setOnFailed(e -> {
            setRestoreControlsDisabled(false);
            ErrorHandler.show(getStage(), "Restore failed", job.getException());
        });
        job.setOnCancelled(e -> setRestoreControlsDisabled(false));

        runTask(job);
    }

    private void setRestoreControlsDisabled(boolean disabled)
    {
        btnBrowseRestoreSource.setDisable(disabled);
        btnBrowseRestoreDestination.setDisable(disabled);
        btnVerifyBackup.setDisable(disabled);
        btnStartRestore.setDisable(disabled);
    }

    // ─── History tab ────────────────────────────────────────────────────────

    private void setupHistoryTab()
    {
        historyListView.setItems(historyItems);
        btnRefreshHistory.setOnAction(e -> refreshHistoryList());
        btnOpenHistoryFolder.setOnAction(e -> openSelectedHistoryFolder());
        btnVerifyHistoryItem.setOnAction(e -> verifySelectedHistoryItem());
        btnRestoreHistoryItem.setOnAction(e -> restoreSelectedHistoryItem());

        refreshHistoryList();
    }

    private void refreshHistoryList()
    {
        List<String> paths = AppSettings.getInstance().getBackupHistoryPaths();
        // Drop entries whose file no longer exists (moved/deleted).
        paths.removeIf(p -> !new File(p).exists());
        historyItems.setAll(paths);
    }

    private File getSelectedHistoryFile()
    {
        String path = historyListView.getSelectionModel().getSelectedItem();
        return path != null ? new File(path) : null;
    }

    private void openSelectedHistoryFolder()
    {
        File file = getSelectedHistoryFile();
        if (file == null || !file.exists()) return;

        // Offload native OS calls to a background thread to prevent UI freezing on Linux
        CompletableFuture.runAsync(() -> {
            if (!Desktop.isDesktopSupported())
            {
                Platform.runLater(() -> 
                    NotificationUtil.showToast(getStage(), "Desktop API is not supported", "fas-exclamation-circle")
                );
                return;
            }

            try
            {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR))
                {
                    desktop.browseFileDirectory(file);
                    return;
                }
            }
            catch (Exception e)
            {
                ErrorHandler.show(getStage(), "Could not open folder", e);
            }
        });
    }

    private void verifySelectedHistoryItem()
    {
        File file = getSelectedHistoryFile();
        if (file == null) return;
        var result = BackupVerifier.verify(file);
        Dialogs.showInfo(getStage(), "Verify Backup", null, result.message());
    }

    private void restoreSelectedHistoryItem()
    {
        File file = getSelectedHistoryFile();
        if (file == null) return;

        selectedRestoreSource = file;
        restoreSourceField.setText(file.getAbsolutePath());
        loadBackupForVerification(file);
        tabPane.getSelectionModel().select(1); // jump to Restore tab
    }

    // ─── Shared task-running helper ─────────────────────────────────────────

    private <T> void bindProgress(Task<T> task, VBox box, Label statusLabel, ProgressBar bar)
    {
        box.setVisible(true);
        box.setManaged(true);
        statusLabel.textProperty().bind(task.messageProperty());
        bar.progressProperty().bind(task.progressProperty());
    }

    private <T> void runTask(Task<T> task)
    {
        Thread thread = new Thread(task, "backup-restore-task");
        thread.setDaemon(true);
        thread.start();
    }

    private Stage getStage()
    {
        return (Stage) tabPane.getScene().getWindow();
    }
}