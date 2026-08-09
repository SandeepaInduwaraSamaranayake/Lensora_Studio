package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.backup.engine.*;
import com.lensora.lensorastudio.backup.ui.ProjectCheckBoxListCell;
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
import javafx.collections.ObservableSet;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BackupRestoreCenterController implements DialogController
{
    private static final Logger logger = LoggerFactory.getLogger(BackupRestoreCenterController.class);

    @FXML private TabPane tabPane;

    // Backup tab
    @FXML private ListView<Project> backupProjectListView;
    @FXML private ListView<File> restoreFileListView;
    @FXML private CheckBox selectAllProjectsCheckBox;
    @FXML private TextField backupDestinationField;
    @FXML private Button btnBrowseBackupDestination, btnStartBackup;
    @FXML private VBox backupProgressBox;
    @FXML private Label backupStatusLabel;
    @FXML private ProgressBar backupProgressBar;

    // Restore tab
    @FXML private TextField restoreSourceField, restoreDestinationField;
    @FXML private Button btnBrowseRestoreDestination, btnVerifyBackup, btnStartRestore, btnAddRestoreFiles, btnRemoveRestoreFile, btnClearRestoreFiles;
    @FXML private Label restoreVerificationLabel;
    @FXML private VBox restoreProgressBox;
    @FXML private Label restoreStatusLabel;
    @FXML private ProgressBar restoreProgressBar;

    // History tab
    @FXML private ListView<String> historyListView;
    @FXML private Button btnRefreshHistory, btnOpenHistoryFolder, btnVerifyHistoryItem, btnRestoreHistoryItem;

    private final ObservableList<String> historyItems = FXCollections.observableArrayList();
    private final ObservableSet<Project> checkedProjects = FXCollections.observableSet(new java.util.LinkedHashSet<>());
    private final ObservableList<File> restoreFiles = FXCollections.observableArrayList();
    private File selectedBackupDestinationFolder;
    private File selectedRestoreDestination;
    private boolean suppressSelectAllEvents = false;

    private ProjectsViewModel projectsViewModel;
    private Runnable onProjectsChanged;

    /** Called by MainController right after DialogBuilder loads this controller. */
    public void initContext(ProjectsViewModel projectsViewModel, Runnable onProjectsChanged)
    {
        this.projectsViewModel = projectsViewModel;
        this.onProjectsChanged = onProjectsChanged;
        loadProjectsIntoList();
    }

    public void preselectProjectForBackup(Project project)
    {
        preselectProjectsForBackup(List.of(project));
    }

    /** Preselects a project and jumps to the Backup tab — used by the Projects context menu. */
    public void preselectProjectsForBackup(List<Project> projects)
    {
        if (projects != null && !projects.isEmpty())
        {
            loadProjectsIntoList();
            checkedProjects.clear();
            
            // select the projects that match the given list of project IDs
            Set<Integer> targetIds = projects.stream()
            .map(Project::getProjectId)
            .collect(Collectors.toSet());

            for (Project item : backupProjectListView.getItems())
            {
                if (targetIds.contains(item.getProjectId()))
                {
                    checkedProjects.add(item);
                }
            }

            backupProjectListView.refresh();
            updateSelectAllCheckboxState();
        }
        // focus to Backup tab
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
        backupProjectListView.setCellFactory(lv -> new ProjectCheckBoxListCell(checkedProjects));

        // Keep "Select All" in sync when individual rows are checked/unchecked.
        checkedProjects.addListener((javafx.collections.SetChangeListener<Project>) change -> {
            updateSelectAllCheckboxState();
        });

        selectAllProjectsCheckBox.setOnAction(e -> {
            if (suppressSelectAllEvents) return;
            if (selectAllProjectsCheckBox.isSelected())
            {
                checkedProjects.addAll(backupProjectListView.getItems());
            }
            else
            {
                checkedProjects.clear();
            }
            backupProjectListView.refresh();
        });

        btnBrowseBackupDestination.setOnAction(e -> browseBackupDestinationFolder());
        btnStartBackup.setOnAction(e -> startBackup());
    }

    private void loadProjectsIntoList()
    {
        try
        {
            List<Project> projects = ProjectRepository.findAll();
            backupProjectListView.setItems(FXCollections.observableArrayList(projects));
            backupProjectListView.refresh();
            updateSelectAllCheckboxState();
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
        List<Project> selected = new ArrayList<>(checkedProjects);
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
        selectAllProjectsCheckBox.setDisable(disabled);
        btnBrowseBackupDestination.setDisable(disabled);
        btnStartBackup.setDisable(disabled);
    }

    private void updateSelectAllCheckboxState()
    {
        suppressSelectAllEvents = true;
        List<Project> allItems = backupProjectListView.getItems();
        boolean allChecked = !allItems.isEmpty() && checkedProjects.containsAll(allItems);
        boolean noneChecked = checkedProjects.isEmpty();

        selectAllProjectsCheckBox.setSelected(allChecked);
        selectAllProjectsCheckBox.setIndeterminate(!allChecked && !noneChecked);
        suppressSelectAllEvents = false;
    }
    // ─── Restore tab ────────────────────────────────────────────────────────

    private void setupRestoreTab()
    {
        restoreFileListView.setItems(restoreFiles);
        restoreFileListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(File file, boolean empty)
            {
                super.updateItem(file, empty);
                setText(empty || file == null ? null : file.getName());
            }
        });

        btnAddRestoreFiles.setOnAction(e -> addRestoreFiles());
        btnRemoveRestoreFile.setOnAction(e -> removeSelectedRestoreFile());
        btnClearRestoreFiles.setOnAction(e -> { restoreFiles.clear(); restoreVerificationLabel.setText(""); });
        btnBrowseRestoreDestination.setOnAction(e -> browseRestoreDestination());
        btnVerifyBackup.setOnAction(e -> verifySelectedBackups());
        btnStartRestore.setOnAction(e -> startRestore());
    }

    private void addRestoreFiles()
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup File(s)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Lensora Backup", "*.lsbak"));

        List<File> selected = chooser.showOpenMultipleDialog(getStage());
        if (selected != null)
        {
            for (File f : selected)
            {
                if (!restoreFiles.contains(f)) restoreFiles.add(f);
            }
            restoreVerificationLabel.setText("");
        }
    }

    private void removeSelectedRestoreFile()
    {
        File selected = restoreFileListView.getSelectionModel().getSelectedItem();
        if (selected != null) restoreFiles.remove(selected);
    }

    private void browseRestoreDestination()
    {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Restore Destination Folder");
        File result = chooser.showDialog(getStage());
        if (result != null)
        {
            selectedRestoreDestination = result;
            restoreDestinationField.setText(result.getAbsolutePath());
        }
    }

    private void verifySelectedBackups()
    {
        if (restoreFiles.isEmpty())
        {
            Dialogs.showInfo(getStage(), "Verify", null, "Please add at least one .lsbak file.");
            return;
        }

        StringBuilder summary = new StringBuilder();
        int passed = 0;

        for (File file : restoreFiles)
        {
            var result = BackupVerifier.verify(file);
            summary.append(result.success() ? "✓ " : "✗ ")
                .append(file.getName())
                .append(result.success() ? "" : " — " + result.message())
                .append("\n");
            if (result.success()) passed++;
        }

        restoreVerificationLabel.setText(passed + "/" + restoreFiles.size() + " verified.\n" + summary);
        restoreVerificationLabel.setStyle(passed == restoreFiles.size()
                ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828;");
    }

    private void startRestore()
    {
        if (restoreFiles.isEmpty())
        {
            Dialogs.showInfo(getStage(), "Restore", null, "Please add at least one .lsbak file.");
            return;
        }
        if (selectedRestoreDestination == null)
        {
            Dialogs.showInfo(getStage(), "Restore", null, "Please select a destination folder.");
            return;
        }

        // Each archive restores into its own subfolder, named after the
        // .lsbak file (without extension), under the chosen parent
        // destination — avoids collisions when restoring multiple projects
        // at once into the same location.
        List<BatchRestoreJob.BatchItem> items = new ArrayList<>();
        for (File lsbak : restoreFiles)
        {
            String baseName = lsbak.getName().replaceFirst("\\.lsbak$", "");
            File subFolder = uniqueSubfolder(selectedRestoreDestination, baseName);
            items.add(new BatchRestoreJob.BatchItem(lsbak, subFolder));
        }

        BatchRestoreJob job = RestoreService.createBatchRestoreJob(items);
        bindProgress(job, restoreProgressBox, restoreStatusLabel, restoreProgressBar);
        setRestoreControlsDisabled(true);

        job.setOnSucceeded(e -> {
            setRestoreControlsDisabled(false);

            long failedCount = job.getResults().stream().filter(r -> !r.succeeded()).count();
            int succeededCount = job.getResults().size() - (int) failedCount;

            if (projectsViewModel != null)
            {
                projectsViewModel.refresh();
                job.getResults().stream()
                        .filter(BatchRestoreJob.BatchItemResult::succeeded)
                        .findFirst()
                        .ifPresent(r -> projectsViewModel.selectById(r.newProjectId()));
            }
            if (onProjectsChanged != null) onProjectsChanged.run();

            String summary = succeededCount + "/" + items.size() + " project(s) restored successfully.";
            if (failedCount > 0)
            {
                summary += " " + failedCount + " failed.";
                for (var result : job.getResults())
                {
                    if (!result.succeeded())
                    {
                        logger.error("Restore failed for {}: {}", result.lsbakFile().getName(), result.errorMessage());
                    }
                }
            }
            NotificationUtil.showToast(getStage(), summary);
        });
        job.setOnFailed(e -> {
            setRestoreControlsDisabled(false);
            ErrorHandler.show(getStage(), "Batch restore failed", job.getException());
        });
        job.setOnCancelled(e -> setRestoreControlsDisabled(false));

        runTask(job);
    }

    /** Avoids collisions if multiple .lsbak files share a base name, or the folder already exists. */
    private File uniqueSubfolder(File parent, String baseName)
    {
        File candidate = new File(parent, baseName);
        int suffix = 2;
        while (candidate.exists())
        {
            candidate = new File(parent, baseName + " (" + suffix + ")");
            suffix++;
        }
        return candidate;
    }

    private void setRestoreControlsDisabled(boolean disabled)
    {
        restoreFileListView.setDisable(disabled);
        btnAddRestoreFiles.setDisable(disabled);
        btnRemoveRestoreFile.setDisable(disabled);
        btnClearRestoreFiles.setDisable(disabled);
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

        if (!restoreFiles.contains(file))
        {
            restoreFiles.add(file);
        }
        verifySelectedBackups();
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