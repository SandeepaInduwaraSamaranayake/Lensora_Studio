package com.lensora.lensorastudio.feature.backup.ui;

import com.lensora.lensorastudio.feature.backup.engine.BackupScheduler;
import com.lensora.lensorastudio.feature.backup.model.BackupSchedule;
import com.lensora.lensorastudio.feature.project.model.Project;
import com.lensora.lensorastudio.ui.controller.DialogController;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ScheduleEditController implements DialogController
{
    @FXML private TextField nameField;
    @FXML private ComboBox<BackupSchedule.Scope> scopeCombo;
    @FXML private VBox projectListContainer;
    @FXML private ListView<Project> projectListView;
    @FXML private TextField destinationField;
    @FXML private Button btnBrowseDestination;
    @FXML private ComboBox<BackupSchedule.Frequency> frequencyCombo;
    @FXML private Spinner<Integer> intervalSpinner;
    @FXML private Label timeLabel, dayOfWeekLabel;
    @FXML private TextField timeField;
    @FXML private ComboBox<Integer> dayOfWeekCombo;
    @FXML private CheckBox enabledCheck;
    @FXML private Button btnCancel, btnSave;
    @FXML private CheckBox selectAllSchedProjectsCheckBox;

    // Checked-projects source of truth, same pattern as the Backup tab.
    private final ObservableSet<Project> checkedProjects = FXCollections.observableSet(new LinkedHashSet<>());
    private boolean suppressSelectAllEvents = false;

    private BackupSchedule existing;
    private Consumer<BackupSchedule> onSaved;

    @Override
    public boolean canClose() { return true; }

    /** Called by MainController after DialogBuilder loads this controller, before showing. */
    public void setContext(List<Project> allProjects, BackupSchedule existingSchedule, Consumer<BackupSchedule> onSaved)
    {
        this.existing = existingSchedule;
        this.onSaved = onSaved;

        projectListView.setItems(FXCollections.observableArrayList(allProjects));
        projectListView.setCellFactory(lv -> new ProjectCheckBoxListCell(checkedProjects));
        
        populateFieldsFromExisting();
        updateSelectAllCheckboxState();
    }

    @FXML
    public void initialize()
    {
        setupCombosAndSpinners();
        setupButtonActions();
        setupBindings();

        // Sensible defaults for a brand-new schedule.
        scopeCombo.setValue(BackupSchedule.Scope.ALL);
        frequencyCombo.setValue(BackupSchedule.Frequency.DAILY);
        timeField.setText("02:00");
        dayOfWeekCombo.setValue(1);
        enabledCheck.setSelected(true);
    }

    private void setupCombosAndSpinners()
    {
        scopeCombo.getItems().addAll(BackupSchedule.Scope.values());
        frequencyCombo.getItems().addAll(BackupSchedule.Frequency.values());

        dayOfWeekCombo.getItems().addAll(1, 2, 3, 4, 5, 6, 7);
        dayOfWeekCombo.setConverter(new javafx.util.StringConverter<>() {
            private final String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            @Override public String toString(Integer d) { return d == null ? "" : names[d - 1]; }
            @Override public Integer fromString(String s) { return null; }
        });

        intervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30, 1));

        // Keep "Select All" in sync when individual rows are checked/unchecked.
        checkedProjects.addListener((SetChangeListener<Project>) change -> updateSelectAllCheckboxState());

        selectAllSchedProjectsCheckBox.setOnAction(e -> {
            if (suppressSelectAllEvents) return;
            if (selectAllSchedProjectsCheckBox.isSelected())
            {
                checkedProjects.addAll(projectListView.getItems());
            }
            else
            {
                checkedProjects.clear();
            }
            projectListView.refresh();
        });
    }

    private void setupButtonActions()
    {
        btnBrowseDestination.setOnAction(e -> browseDestination());
        btnCancel.setOnAction(e -> closeDialog());
        btnSave.setOnAction(e -> saveAndClose());
    }

// ─── Declarative Bindings ───────────────────────────────────────────────

    private void setupBindings()
    {
        // Scope visibility binding
        BooleanBinding showProjectList = Bindings.createBooleanBinding(
            () -> scopeCombo.getValue() != BackupSchedule.Scope.ALL,
            scopeCombo.valueProperty()
        );

        projectListContainer.visibleProperty().bind(showProjectList);
        projectListContainer.managedProperty().bind(showProjectList);

        // Frequency visibility bindings
        BooleanBinding showTime = Bindings.createBooleanBinding(
            () -> frequencyCombo.getValue() != BackupSchedule.Frequency.HOURLY,
            frequencyCombo.valueProperty()
        );

        timeLabel.visibleProperty().bind(showTime);
        timeLabel.managedProperty().bind(showTime);
        timeField.visibleProperty().bind(showTime);
        timeField.managedProperty().bind(showTime);

        BooleanBinding showDow = Bindings.createBooleanBinding(
            () -> frequencyCombo.getValue() == BackupSchedule.Frequency.WEEKLY,
            frequencyCombo.valueProperty()
        );

        dayOfWeekLabel.visibleProperty().bind(showDow);
        dayOfWeekLabel.managedProperty().bind(showDow);
        dayOfWeekCombo.visibleProperty().bind(showDow);
        dayOfWeekCombo.managedProperty().bind(showDow);

        // Form validation & Save button disable binding
        BooleanBinding isFormInvalid = Bindings.createBooleanBinding(
            () -> {
                boolean isNameEmpty = nameField.getText() == null || nameField.getText().isBlank();
                boolean isDestEmpty = destinationField.getText() == null || destinationField.getText().isBlank();
                boolean isScopeSpecific = scopeCombo.getValue() != BackupSchedule.Scope.ALL;
                boolean noProjectsSelected = checkedProjects.isEmpty();

                return isNameEmpty || isDestEmpty || (isScopeSpecific && noProjectsSelected);
            },
            nameField.textProperty(),
            destinationField.textProperty(),
            scopeCombo.valueProperty(),
            checkedProjects
        );

        btnSave.disableProperty().bind(isFormInvalid);
    }

    private void populateFieldsFromExisting()
    {
        if (existing == null) return;

        nameField.setText(existing.getName());
        scopeCombo.setValue(existing.getScope());
        destinationField.setText(existing.getDestinationPath());
        frequencyCombo.setValue(existing.getFrequency());
        intervalSpinner.getValueFactory().setValue(existing.getIntervalValue());
        timeField.setText(existing.getTimeOfDay() != null ? existing.getTimeOfDay() : "02:00");
        dayOfWeekCombo.setValue(existing.getDayOfWeek() != null ? existing.getDayOfWeek() : 1);
        enabledCheck.setSelected(existing.isEnabled());

        checkedProjects.clear();
        if (existing.getProjectIds() != null)
        {
            for (Project p : projectListView.getItems())
            {
                if (existing.getProjectIds().contains(p.getProjectId()))
                {
                    checkedProjects.add(p);
                }
            }
        }
        projectListView.refresh();
    }

    private void updateSelectAllCheckboxState()
    {
        suppressSelectAllEvents = true;
        List<Project> allItems = projectListView.getItems();
        boolean allChecked = !allItems.isEmpty() && checkedProjects.containsAll(allItems);
        boolean noneChecked = checkedProjects.isEmpty();

        selectAllSchedProjectsCheckBox.setSelected(allChecked);
        selectAllSchedProjectsCheckBox.setIndeterminate(!allChecked && !noneChecked);
        suppressSelectAllEvents = false;
    }

    private void browseDestination()
    {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Destination Folder");
        var result = chooser.showDialog(destinationField.getScene().getWindow());
        if (result != null) destinationField.setText(result.getAbsolutePath());
    }

    private void saveAndClose()
    {
        // Single-line guard using the binding's actual output
        if (btnSave.isDisabled()) return;

        BackupSchedule schedule = existing != null ? existing : new BackupSchedule();
        schedule.setName(nameField.getText().trim());
        schedule.setScope(scopeCombo.getValue());
        schedule.setProjectIds(scopeCombo.getValue() == BackupSchedule.Scope.ALL ? null
                : checkedProjects.stream().map(Project::getProjectId).collect(Collectors.toList()));
        schedule.setDestinationPath(destinationField.getText());
        schedule.setFrequency(frequencyCombo.getValue());
        schedule.setIntervalValue(intervalSpinner.getValue());
        schedule.setTimeOfDay(timeField.getText());
        schedule.setDayOfWeek(dayOfWeekCombo.getValue());
        schedule.setEnabled(enabledCheck.isSelected());
        schedule.setNextRun(BackupScheduler.computeNextRun(schedule, LocalDateTime.now()));

        if (onSaved != null) onSaved.accept(schedule);
        closeDialog();
    }

    private void closeDialog()
    {
        Window window = btnCancel.getScene() != null ? btnCancel.getScene().getWindow() : null;
        if (window != null)
        {
            window.fireEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSE_REQUEST));
        }
    }
}