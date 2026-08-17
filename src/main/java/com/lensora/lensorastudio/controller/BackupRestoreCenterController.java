package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.backup.engine.*;
import com.lensora.lensorastudio.backup.model.BackupHistoryItem;
import com.lensora.lensorastudio.backup.model.BackupSchedule;
import com.lensora.lensorastudio.backup.model.RestoreQueueItem;
import com.lensora.lensorastudio.backup.ui.ProjectCheckBoxListCell;
import com.lensora.lensorastudio.backup.verify.BackupVerifier;
import com.lensora.lensorastudio.backup.verify.BatchVerifyTask;
import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.repository.BackupHistoryRepository;
import com.lensora.lensorastudio.repository.BackupScheduleRepository;
import com.lensora.lensorastudio.repository.ProjectRepository;
import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.util.DialogBuilder;
import com.lensora.lensorastudio.util.Dialogs;
import com.lensora.lensorastudio.util.ErrorHandler;
import com.lensora.lensorastudio.util.FileSizeFormatter;
import com.lensora.lensorastudio.util.NotificationUtil;
import com.lensora.lensorastudio.util.Resources;
import com.lensora.lensorastudio.viewmodel.ProjectsViewModel;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    @FXML private CheckBox selectAllProjectsCheckBox;
    @FXML private TextField backupDestinationField;
    @FXML private Button btnBrowseBackupDestination, btnStartBackup;
    @FXML private VBox backupProgressBox;
    @FXML private Label backupStatusLabel;
    @FXML private ProgressBar backupProgressBar;

    // Restore tab
    @FXML private ListView<RestoreQueueItem> restoreFileListView;
    @FXML private TextField restoreSourceField, restoreDestinationField;
    @FXML private Button btnBrowseRestoreDestination, btnStartRestore, btnAddRestoreFiles, btnRemoveRestoreFile, btnClearRestoreFiles, btnVerifySelected, btnVerifyAll;
    @FXML private VBox restoreProgressBox, verifyProgressBox;
    @FXML private Label restoreStatusLabel, verifyStatusLabel;
    @FXML private ProgressBar restoreProgressBar, verifyProgressBar;

    // Schedule tab
    @FXML private TableView<BackupSchedule> scheduleTableView;
    @FXML private TableColumn<BackupSchedule, String> colScheduleName, colScheduleScope, colScheduleFrequency, colScheduleDestination, colScheduleNextRun, colScheduleStatus;
    @FXML private TableColumn<BackupSchedule, Boolean> colScheduleEnabled;
    @FXML private Button btnNewSchedule, btnRefresh, btnRunScheduleNow, btnEditSchedule, btnDeleteSchedule;

    // History tab
    @FXML private Button btnRefreshHistory, btnOpenHistoryFolder, btnVerifyHistoryItem, btnRestoreHistoryItem, btnDeleteHistoryItem;
    @FXML private TableView<BackupHistoryItem> historyTableView;
    @FXML private TableColumn<BackupHistoryItem, String> colHistoryDate, colHistoryProject, colHistorySource, colHistorySize, colHistoryStatus;
    @FXML private TableColumn<BackupHistoryItem, String> colHistoryVerified;


    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ObservableSet<Project> checkedProjects = FXCollections.observableSet(new LinkedHashSet<>());
    private final ObservableList<RestoreQueueItem> restoreQueue = FXCollections.observableArrayList();
    private File selectedBackupDestinationFolder;
    private File selectedRestoreDestination;
    private boolean suppressSelectAllEvents = false;

    // --- Reactive binding properties (bindings to enable/disable buttons for each tab) ---

    // --- Backup Tab State ---
    private final BooleanProperty isBackupRunning = new SimpleBooleanProperty(false);

    // --- Restore Tab State ---
    private final BooleanProperty isRestoreRunning = new SimpleBooleanProperty(false);
    private final BooleanProperty isRestoreVerifying = new SimpleBooleanProperty(false);

    // --- History Tab State ---
    private final BooleanProperty isHistoryVerifying = new SimpleBooleanProperty(false);


    private ProjectsViewModel projectsViewModel;
    private Runnable onProjectsChanged;
    private final Runnable scheduleStatusListener = this::refreshScheduleTableInPlace;

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

    /** Preselects a project and jumps to the Backup tab - used by the Projects context menu. */
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
        setupScheduleTab();
        setupHistoryTab();
    }

    // ─── Backup tab ─────────────────────────────────────────────────────────

    private void setupBackupTab()
    {
        backupProjectListView.setCellFactory(lv -> new ProjectCheckBoxListCell(checkedProjects));

        // Keep "Select All" in sync when individual rows are checked/unchecked.
        checkedProjects.addListener((SetChangeListener<Project>) change -> {
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

        // --- REACTIVE BINDINGS ---

        // Disable "Create Backup" if: Backing Up OR No projects checked OR No destination set
        btnStartBackup.disableProperty().bind(
            isBackupRunning
            .or(Bindings.isEmpty(checkedProjects))
            .or(backupDestinationField.textProperty().isEmpty())
        );

        // Disable selection controls while backup is actively running
        backupProjectListView.disableProperty().bind(isBackupRunning);
        selectAllProjectsCheckBox.disableProperty().bind(isBackupRunning);
        btnBrowseBackupDestination.disableProperty().bind(isBackupRunning);
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
        if (selected.isEmpty() || selectedBackupDestinationFolder == null) return;

        List<BatchBackupJob.BatchItem> items = new ArrayList<>();
        for (Project project : selected)
        {
            String fileName = BackupService.suggestFileName(project);
            items.add(new BatchBackupJob.BatchItem(project, new File(selectedBackupDestinationFolder, fileName)));
        }

        BatchBackupJob job = BackupService.createBatchBackupJob(items);
        bindProgress(job, backupProgressBox, backupStatusLabel, backupProgressBar);

        // Lock UI controls reactively
        isBackupRunning.set(true);

        job.setOnSucceeded(e -> {
            List<BatchBackupJob.BatchItemResult> results = job.getResults();

            CompletableFuture.runAsync(() -> {
                int verifiedCount = 0;
                for (var result : results)
                {
                    boolean isVerified = false;
                    if (result.succeeded() && result.file() != null) 
                    {
                        var verification = BackupVerifier.verify(result.file());
                        isVerified = verification != null && verification.success();
                    }
                    if (isVerified) verifiedCount++;

                    try
                    {
                        BackupHistoryItem item = new BackupHistoryItem();
                        item.setScheduleId(null); // manual
                        item.setScheduleName(null);
                        item.setProjectId(result.project().getProjectId());
                        item.setProjectNumber(result.project().getProjectNumber());
                        item.setClientName(result.project().getClientName());
                        item.setFilePath(result.file() != null ? result.file().getAbsolutePath() : "");
                        item.setFileSize(result.file() != null && result.file().exists() ? result.file().length() : 0);
                        item.setStatus(result.succeeded() ? "SUCCEEDED" : "FAILED");
                        item.setErrorMessage(result.errorMessage());
                        item.setVerified(isVerified);
                        item.setStartedAt(LocalDateTime.now());
                        item.setCompletedAt(LocalDateTime.now());
                        BackupHistoryRepository.insert(item);
                    }
                    catch (SQLException ex)
                    {
                        logger.error("Failed to record backup history", ex);
                    }

                    if (result.succeeded() && result.file() != null)
                    {
                        AppSettings.getInstance().addBackupHistoryPath(result.file().getAbsolutePath());
                    }
                }

                int finalVerifiedCount = verifiedCount;
                long failedCount = results.stream().filter(r -> !r.succeeded()).count();

                Platform.runLater(() -> {
                    // Unlock UI controls reactively when all async tasks finish
                    isBackupRunning.set(false);
                    refreshHistoryList();
                    String summary = String.format("%d/%d backup(s) succeeded and verified.", finalVerifiedCount, results.size());
                    if (failedCount > 0) summary += " " + failedCount + " failed.";
                    NotificationUtil.showToast(getStage(), summary);
                });
            });
        });

        job.setOnFailed(e -> {
            isBackupRunning.set(false); // Unlock UI controls
            ErrorHandler.show(getStage(), "Batch backup failed", job.getException());
        });

        job.setOnCancelled(e -> {
            isBackupRunning.set(false); // Unlock UI controls
            logger.info("Backup cancelled");
        });

        runTask(job);
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
        restoreFileListView.setItems(restoreQueue);
        restoreFileListView.setCellFactory(lv -> new RestoreQueueCell());

        btnAddRestoreFiles.setOnAction(e -> addRestoreFiles());
        btnRemoveRestoreFile.setOnAction(e -> removeSelectedRestoreItem());
        btnClearRestoreFiles.setOnAction(e -> restoreQueue.clear());
        btnVerifySelected.setOnAction(e -> verifySelectedItem());
        btnVerifyAll.setOnAction(e -> verifyAllItems());
        btnBrowseRestoreDestination.setOnAction(e -> browseRestoreDestination());
        btnStartRestore.setOnAction(e -> startRestore());

        // --- REACTIVE BINDINGS ---
    
        var noSelection = restoreFileListView.getSelectionModel().selectedItemProperty().isNull();
        var isQueueEmpty = Bindings.isEmpty(restoreQueue);
        var noDestination = restoreDestinationField.textProperty().isEmpty();

        // Item-specific actions (require a selected row & idle state)
        btnRemoveRestoreFile.disableProperty().bind(noSelection.or(isRestoreRunning).or(isRestoreVerifying));
        btnVerifySelected.disableProperty().bind(noSelection.or(isRestoreRunning).or(isRestoreVerifying));

        // Queue-level actions (require items in queue & idle state)
        btnClearRestoreFiles.disableProperty().bind(isQueueEmpty.or(isRestoreRunning).or(isRestoreVerifying));
        btnVerifyAll.disableProperty().bind(isQueueEmpty.or(isRestoreRunning).or(isRestoreVerifying));

        // Primary action (requires items in queue + destination path + idle state)
        btnStartRestore.disableProperty().bind(
            isRestoreRunning
            .or(isQueueEmpty)
            .or(noDestination)
        );

        // Inputs/controls disabled while actively restoring/verifying
        btnAddRestoreFiles.disableProperty().bind(isRestoreRunning.or(isRestoreVerifying));
        btnBrowseRestoreDestination.disableProperty().bind(isRestoreRunning.or(isRestoreVerifying));
        restoreFileListView.disableProperty().bind(isRestoreRunning.or(isRestoreVerifying));
    }

    /** Renders each queued .lsbak with a leading status icon/spinner and a right-aligned filename. */
    private class RestoreQueueCell extends ListCell<RestoreQueueItem>
    {
        private final Label statusIcon = new Label();
        private final Label nameLabel = new Label();
        private final HBox container = new HBox(8, statusIcon, nameLabel);

        RestoreQueueCell()
        {
            container.setAlignment(Pos.CENTER_LEFT);
            statusIcon.setMinWidth(18);
        }

        @Override
        protected void updateItem(RestoreQueueItem item, boolean empty)
        {
            super.updateItem(item, empty);
            if (empty || item == null)
            {
                setGraphic(null);
                setTooltip(null);
                return;
            }

            nameLabel.setText(item.getFile().getName());

            switch (item.getState())
            {
                case NOT_VERIFIED -> { statusIcon.setText("○"); statusIcon.setStyle("-fx-text-fill: #888888;"); }
                case VERIFYING    -> { statusIcon.setText("…"); statusIcon.setStyle("-fx-text-fill: #3574f0;"); }
                case VERIFIED_OK  -> { statusIcon.setText("✓"); statusIcon.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;"); }
                case VERIFIED_FAILED -> { statusIcon.setText("✗"); statusIcon.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;"); }
            }

            setTooltip(item.getMessage() == null || item.getMessage().isBlank()
                    ? null : new Tooltip(item.getMessage()));

            setGraphic(container);
        }
    }

    private void addRestoreFiles()
    {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Backup File(s)");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Lensora Backup", "*.lsbak"));

        List<File> selected = chooser.showOpenMultipleDialog(getStage());
        if (selected == null) return;

        int skippedDuplicates = 0;
        for (File f : selected)
        {
            RestoreQueueItem candidate = new RestoreQueueItem(f);
            // RestoreQueueItem.equals() compares canonical paths, so this
            // correctly catches the same physical file added twice even via
            // different path strings (symlinks, case differences on Windows).
            if (restoreQueue.contains(candidate))
            {
                skippedDuplicates++;
                continue;
            }
            restoreQueue.add(candidate);
        }

        if (skippedDuplicates > 0)
        {
            NotificationUtil.showToast(getStage(),
                    skippedDuplicates + " file(s) already in the list were skipped.");
        }
    }

    private void removeSelectedRestoreItem()
    {
        RestoreQueueItem selected = restoreFileListView.getSelectionModel().getSelectedItem();
        if (selected != null) restoreQueue.remove(selected);
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

    // ─── Verification ───────────────────────────────────────────────────────

    private void verifySelectedItem()
    {
        RestoreQueueItem selected = restoreFileListView.getSelectionModel().getSelectedItem();
        if (selected == null)
        {
            Dialogs.showInfo(getStage(), "Verify", null, "Please select a file to verify.");
            return;
        }
        runVerification(List.of(selected));
    }

    private void verifyAllItems()
    {
        if (restoreQueue.isEmpty())
        {
            Dialogs.showInfo(getStage(), "Verify", null, "Please add at least one .lsbak file.");
            return;
        }
        runVerification(new ArrayList<>(restoreQueue));
    }

    private void runVerification(List<RestoreQueueItem> items)
    {
        BatchVerifyTask task = new BatchVerifyTask(items);
        bindProgress(task, verifyProgressBox, verifyStatusLabel, verifyProgressBar);
        isRestoreVerifying.set(true);

        task.setOnSucceeded(e -> {
            isRestoreVerifying.set(false);
            restoreFileListView.refresh();

            long failed = items.stream()
                    .filter(i -> i.getState() == RestoreQueueItem.VerificationState.VERIFIED_FAILED)
                    .count();
            String summary = (items.size() - failed) + "/" + items.size() + " verified successfully.";
            NotificationUtil.showToast(getStage(), summary);
        });
        task.setOnFailed(e -> {
            isRestoreVerifying.set(false);
            ErrorHandler.show(getStage(), "Verification failed", task.getException());
        });
        task.setOnCancelled(e -> {
            isRestoreVerifying.set(false);
        });

        runTask(task);
    }

    // ─── Restore ────────────────────────────────────────────────────────────
    private void startRestore()
    {
        if (restoreQueue.isEmpty() || selectedRestoreDestination == null) return;

        List<BatchRestoreJob.BatchItem> items = new ArrayList<>();
        for (RestoreQueueItem queueItem : restoreQueue)
        {
            String baseName = queueItem.getFile().getName().replaceFirst("\\.lsbak$", "");
            File subFolder = uniqueSubfolder(selectedRestoreDestination, baseName);
            items.add(new BatchRestoreJob.BatchItem(queueItem.getFile(), subFolder));
        }

        BatchRestoreJob job = RestoreService.createBatchRestoreJob(items);
        bindProgress(job, restoreProgressBox, restoreStatusLabel, restoreProgressBar);
        // Lock UI reactively
        isRestoreRunning.set(true);

        job.setOnSucceeded(e -> {
            isRestoreRunning.set(false); // Unlock UI

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
            isRestoreRunning.set(false); // Unlock UI
            ErrorHandler.show(getStage(), "Batch restore failed", job.getException());
        });
        job.setOnCancelled(e -> {
            isRestoreRunning.set(false); // Unlock UI
        });

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

    // ─── Schedule tab ────────────────────────────────────────────────────────
    private void setupScheduleTab()
    {
        colScheduleName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colScheduleScope.setCellValueFactory(c -> new SimpleStringProperty(
                switch (c.getValue().getScope()) {
                    case ALL -> "All Projects";
                    case SINGLE -> "1 project";
                    case MULTIPLE -> (c.getValue().getProjectIds() != null ? c.getValue().getProjectIds().size() : 0) + " projects";
                }));
        colScheduleFrequency.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().describeFrequency()));
        colScheduleDestination.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDestinationPath()));
        colScheduleNextRun.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getNextRun() != null ? c.getValue().getNextRun().format(DATE_FORMATTER) : "-"));
        colScheduleEnabled.setCellValueFactory(c -> new SimpleBooleanProperty(c.getValue().isEnabled()));
        colScheduleEnabled.setCellFactory(CheckBoxTableCell.forTableColumn(colScheduleEnabled));

        colScheduleStatus.setCellValueFactory(c -> new SimpleStringProperty(
            statusLabel(BackupScheduler.getInstance().getLiveStatus(c.getValue().getScheduleId()))));

        colScheduleStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty)
            {
                super.updateItem(status, empty);
                if (empty || status == null)
                {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(status);
                setStyle(switch (status) {
                    case "Running"   -> "-fx-text-fill: #3574f0; -fx-font-weight: bold;";
                    case "Succeeded" -> "-fx-text-fill: #2e7d32;";
                    case "Failed"    -> "-fx-text-fill: #c62828; -fx-font-weight: bold;";
                    case "Scheduled" -> "-fx-text-fill: #888888;";
                    default          -> "";
                });
            }
        });

        // Live-refresh the table whenever the scheduler's status map changes.
        BackupScheduler.getInstance().addStatusChangeListener(scheduleStatusListener);


        btnRefresh.setOnAction(e -> refreshScheduleList());
        btnNewSchedule.setOnAction(e -> createSchedule());
        btnEditSchedule.setOnAction(e -> editSelectedSchedule());
        btnDeleteSchedule.setOnAction(e -> deleteSelectedSchedule());
        btnRunScheduleNow.setOnAction(e -> runSelectedScheduleNow());

        // --- REACTIVE BINDINGS ---

        // binding the buttons' disableProperty directly to the table's selection model (No selection, all buttons are disabled)
        var noSelection = scheduleTableView.getSelectionModel().selectedItemProperty().isNull();
        btnEditSchedule.disableProperty().bind(noSelection);
        btnDeleteSchedule.disableProperty().bind(noSelection);
        btnRunScheduleNow.disableProperty().bind(noSelection);

        refreshScheduleList();
    }

    private String statusLabel(BackupSchedule.RunStatus status)
    {
        return switch (status)
        {
            case IDLE -> "Idle";
            case SCHEDULED -> "Scheduled";
            case RUNNING -> "Running";
            case SUCCEEDED -> "Succeeded";
            case FAILED -> "Failed";
        };
    }

    /** Refreshes the table's cell rendering without reloading data from the DB - just re-evaluates the Status column. */
    private void refreshScheduleTableInPlace()
    {
        scheduleTableView.refresh();
    }

    @Override
    public void onClosing()
    {
        BackupScheduler.getInstance().removeStatusChangeListener(scheduleStatusListener);
    }

    private void refreshScheduleList()
    {
        try
        {
            scheduleTableView.setItems(FXCollections.observableArrayList(BackupScheduleRepository.findAll()));
        }
        catch (SQLException e)
        {
            logger.error("Failed to load schedules", e);
        }
    }

    private void createSchedule()
    {
        Stage owner = getStage();
        DialogBuilder.of(Resources.SCHEDULE_EDIT_VIEW.url(), "New Backup Schedule", owner)
                .icon("🗓")
                .resizable(true)
                .withControllerConsumer(controller -> {
                    if (controller instanceof ScheduleEditController sec)
                    {
                        sec.setContext(allProjectsOrEmpty(), null, schedule -> {
                            try
                            {
                                BackupScheduleRepository.insert(schedule);
                                refreshScheduleList();
                            }
                            catch (SQLException e)
                            {
                                ErrorHandler.show(owner, "Failed to save schedule", e);
                            }
                        });
                    }
                })
                .build();
    }

    private List<Project> allProjectsOrEmpty()
    {
        try { return ProjectRepository.findAll(); }
        catch (SQLException e) { return List.of(); }
    }

    private void editSelectedSchedule()
    {
        BackupSchedule selected = scheduleTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Stage owner = getStage();
        DialogBuilder.of(Resources.SCHEDULE_EDIT_VIEW.url(), "Edit Backup Schedule", owner)
                .icon("🗓")
                .resizable(true)
                .withControllerConsumer(controller -> {
                    if (controller instanceof ScheduleEditController sec)
                    {
                        sec.setContext(allProjectsOrEmpty(), selected, updated -> {
                            try
                            {
                                BackupScheduleRepository.update(updated);
                                refreshScheduleList();
                            }
                            catch (SQLException e)
                            {
                                ErrorHandler.show(owner, "Failed to update schedule", e);
                            }
                        });
                    }
                })
                .build();
    }

    private void deleteSelectedSchedule()
    {
        BackupSchedule selected = scheduleTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setContentText("Delete schedule \"" + selected.getName() + "\"?");
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;
            try
            {
                BackupScheduleRepository.delete(selected.getScheduleId());
                refreshScheduleList();
            }
            catch (SQLException e)
            {
                ErrorHandler.show(getStage(), "Failed to delete schedule", e);
            }
        });
    }

    private void runSelectedScheduleNow()
    {
        BackupSchedule selected = scheduleTableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (!selected.isEnabled())
        {
            NotificationUtil.showToast(getStage(), "Enable the schedule first");
            return;
        }

        selected.setNextRun(LocalDateTime.now().minusMinutes(1)); // force due
        try
        {
            BackupScheduleRepository.update(selected);
            NotificationUtil.showToast(getStage(), "Schedule will run within the next minute.");
        }
        catch (SQLException e)
        {
            ErrorHandler.show(getStage(), "Failed to trigger schedule", e);
        }
    }

    // ─── History tab ────────────────────────────────────────────────────────

    private void setupHistoryTab()
    {
        colHistoryDate.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStartedAt() != null
                        ? c.getValue().getStartedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                        : ""));
        colHistoryProject.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getProjectNumber() + " - " + c.getValue().getClientName()));
        colHistorySource.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().describeSource()));
        colHistorySize.setCellValueFactory(c -> new SimpleStringProperty(
                FileSizeFormatter.formatFileSize(c.getValue().getFileSize())));
        colHistoryStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        colHistoryStatus.setCellFactory(col -> new TableCell<>() {
                @Override protected void updateItem(String status, boolean empty) 
                {
                    super.updateItem(status, empty);
                    if (empty || status == null) { setText(null); setStyle(""); return; }
                    setText(status);
                    setStyle("SUCCEEDED".equals(status)
                            ? "-fx-text-fill: #2e7d32;" : "-fx-text-fill: #c62828; -fx-font-weight: bold;");
                }
        });
        colHistoryVerified.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isVerified() ? "✓" : "—"));

        btnRefreshHistory.setOnAction(e -> refreshHistoryList());
        btnOpenHistoryFolder.setOnAction(e -> openSelectedHistoryFolder());
        btnVerifyHistoryItem.setOnAction(e -> verifySelectedHistoryItem());
        btnRestoreHistoryItem.setOnAction(e -> restoreSelectedHistoryItem());
        btnDeleteHistoryItem.setOnAction(e -> deleteSelectedHistoryItem());

        // --- REACTIVE BINDINGS ---

        // binding the buttons' disableProperty directly to the table's selection model (No selection, all buttons are disabled)
        var noSelection = historyTableView.getSelectionModel().selectedItemProperty().isNull();
        btnOpenHistoryFolder.disableProperty().bind(noSelection);
        btnVerifyHistoryItem.disableProperty().bind(noSelection.or(isHistoryVerifying));
        btnRestoreHistoryItem.disableProperty().bind(noSelection);
        btnDeleteHistoryItem.disableProperty().bind(noSelection);

        refreshHistoryList();
    }

    private void refreshHistoryList()
    {
        try
        {
            historyTableView.setItems(FXCollections.observableArrayList(BackupHistoryRepository.findAll()));
        }
        catch (SQLException e)
        {
            logger.error("Failed to load backup history", e);
        }
    }

    private BackupHistoryItem getSelectedHistoryItem()
    {
        return historyTableView.getSelectionModel().getSelectedItem();
    }

    private void openSelectedHistoryFolder()
    {
        BackupHistoryItem item = getSelectedHistoryItem();
        if (item == null) return;

        File file = new File(item.getFilePath());
        if (!file.exists())
        {
            NotificationUtil.showToast(getStage(), "File no longer exists", "fas-exclamation-circle");
            return;
        }

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
                } 
                else if (desktop.isSupported(Desktop.Action.OPEN)) 
                {
                    desktop.open(file.getParentFile());
                }
                
            } 
            catch (Exception e)
            {
                Platform.runLater(() -> ErrorHandler.show(getStage(), "Could not open folder", e));
            }
        });
    }

    private void verifySelectedHistoryItem() 
    {
        BackupHistoryItem item = getSelectedHistoryItem();
        if (item == null) return;

        File file = new File(item.getFilePath());
        if (!file.exists()) 
        {
            NotificationUtil.showToast(getStage(), "Backup file no longer exists at recorded location.", "fas-exclamation-circle");
            return;
        }

        isHistoryVerifying.set(true); // lock UI

        CompletableFuture.supplyAsync(() -> {
            var result = BackupVerifier.verify(file);
            try 
            {
                BackupHistoryRepository.updateVerified(item.getHistoryId(), result.success());
            } 
            catch (SQLException e)
            {
                logger.error("Failed to update verification status", e);
            }
            return result;
        }).whenCompleteAsync((result, throwable) -> {
            isHistoryVerifying.set(false); // Unlock UI. ALWAYS executes, even on exception
            if (throwable != null) 
            {
                ErrorHandler.show(getStage(), "Verification Error", (Exception) throwable);
            }
            else 
            {
                refreshHistoryList();
                Dialogs.showInfo(getStage(), "Verify Backup", null, result.message());
            }
        }, Platform::runLater);
    }

    private void restoreSelectedHistoryItem()
    {
        BackupHistoryItem item = getSelectedHistoryItem();
        if (item == null) return;
        File file = new File(item.getFilePath());
        if (!file.exists())
        {
            NotificationUtil.showToast(getStage(), "Backup file no longer exists at recorded location", "fas-exclamation-circle");
            return;
        }

        RestoreQueueItem queueItem = new RestoreQueueItem(file);
        if (queueItem != null && !restoreQueue.contains(queueItem))
        {
            restoreQueue.add(queueItem);
        }
        tabPane.getSelectionModel().select(1);
    }

    private void deleteSelectedHistoryItem() 
    {
        BackupHistoryItem item = getSelectedHistoryItem();
        if (item == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(getStage()); // Anchor to parent stage
        confirm.setTitle("Delete History Entry");
        confirm.setHeaderText(null);
        confirm.setContentText("Remove this entry from history? The backup file itself is not deleted.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;
            try 
            {
                BackupHistoryRepository.delete(item.getHistoryId());
                refreshHistoryList();
            } 
            catch (SQLException e)
            {
                ErrorHandler.show(getStage(), "Failed to delete history entry", e);
            }
        });
    }

    // ─── Shared task-running helper ─────────────────────────────────────────

    private <T> void bindProgress(Task<T> task, VBox box, Label statusLabel, ProgressBar bar)
    {
        // Show container and bind properties immediately
        box.setVisible(true);
        box.setManaged(true);
        statusLabel.textProperty().bind(task.messageProperty());
        bar.progressProperty().bind(task.progressProperty());

        Runnable cleanup = () -> {
            statusLabel.textProperty().unbind();
            bar.progressProperty().unbind();
            box.setVisible(false);
            box.setManaged(false);
        };

        // Unbind and hide automatically when the task finishes
        // Add a state listener - this does NOT overwrite existing handlers
        task.stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED ||
                newState == Worker.State.FAILED ||
                newState == Worker.State.CANCELLED) 
            {
                cleanup.run();
            }
        });
    }

    private <T> void runTask(Task<T> task)
    {
        Thread thread = new Thread(task, "Lensora-backup-restore-task");
        thread.setDaemon(true);
        thread.start();
    }

    private Stage getStage()
    {
        return (Stage) tabPane.getScene().getWindow();
    }
}