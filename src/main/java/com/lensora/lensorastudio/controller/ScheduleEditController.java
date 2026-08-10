package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.backup.model.BackupSchedule;
import com.lensora.lensorastudio.model.Project;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.time.LocalDateTime;
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
        projectListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        projectListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Project p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getProjectNumber() + " — " + p.getClientName());
            }
        });

        populateFieldsFromExisting();
    }

    @FXML
    public void initialize()
    {
        scopeCombo.getItems().addAll(BackupSchedule.Scope.values());
        scopeCombo.valueProperty().addListener((obs, old, val) -> updateScopeVisibility());

        frequencyCombo.getItems().addAll(BackupSchedule.Frequency.values());
        frequencyCombo.valueProperty().addListener((obs, old, val) -> updateFrequencyVisibility());

        dayOfWeekCombo.getItems().addAll(1, 2, 3, 4, 5, 6, 7);
        dayOfWeekCombo.setConverter(new javafx.util.StringConverter<>() {
            private final String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
            @Override public String toString(Integer d) { return d == null ? "" : names[d - 1]; }
            @Override public Integer fromString(String s) { return null; }
        });

        intervalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30, 1));

        btnBrowseDestination.setOnAction(e -> browseDestination());
        btnCancel.setOnAction(e -> closeDialog());
        btnSave.setOnAction(e -> saveAndClose());

        // Sensible defaults for a brand-new schedule.
        scopeCombo.setValue(BackupSchedule.Scope.ALL);
        frequencyCombo.setValue(BackupSchedule.Frequency.DAILY);
        timeField.setText("02:00");
        dayOfWeekCombo.setValue(1);
        enabledCheck.setSelected(true);

        updateScopeVisibility();
        updateFrequencyVisibility();
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

        if (existing.getProjectIds() != null)
        {
            for (Project p : projectListView.getItems())
            {
                if (existing.getProjectIds().contains(p.getProjectId()))
                {
                    projectListView.getSelectionModel().select(p);
                }
            }
        }

        updateScopeVisibility();
        updateFrequencyVisibility();
    }

    /**
     * Toggles the project list's visible/managed state on a plain VBox
     * inside a real Stage/Scene — unlike the old Dialog<T>-based version,
     * this correctly triggers a fresh layout pass, so the list actually
     * appears/disappears and the dialog resizes around it as expected.
     */
    private void updateScopeVisibility()
    {
        boolean showList = scopeCombo.getValue() != BackupSchedule.Scope.ALL;
        projectListContainer.setVisible(showList);
        projectListContainer.setManaged(showList);
    }

    private void updateFrequencyVisibility()
    {
        BackupSchedule.Frequency freq = frequencyCombo.getValue();
        boolean showTime = freq != BackupSchedule.Frequency.HOURLY;
        timeLabel.setVisible(showTime);
        timeLabel.setManaged(showTime);
        timeField.setVisible(showTime);
        timeField.setManaged(showTime);

        boolean showDow = freq == BackupSchedule.Frequency.WEEKLY;
        dayOfWeekLabel.setVisible(showDow);
        dayOfWeekLabel.setManaged(showDow);
        dayOfWeekCombo.setVisible(showDow);
        dayOfWeekCombo.setManaged(showDow);
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
        if (nameField.getText() == null || nameField.getText().isBlank())
        {
            com.lensora.lensorastudio.util.Dialogs.showInfo(
                    destinationField.getScene().getWindow(), "Schedule", null, "Please enter a name.");
            return;
        }
        if (destinationField.getText() == null || destinationField.getText().isBlank())
        {
            com.lensora.lensorastudio.util.Dialogs.showInfo(
                    destinationField.getScene().getWindow(), "Schedule", null, "Please choose a destination folder.");
            return;
        }

        BackupSchedule schedule = existing != null ? existing : new BackupSchedule();
        schedule.setName(nameField.getText().trim());
        schedule.setScope(scopeCombo.getValue());
        schedule.setProjectIds(scopeCombo.getValue() == BackupSchedule.Scope.ALL ? null
                : projectListView.getSelectionModel().getSelectedItems().stream()
                        .map(Project::getProjectId).collect(Collectors.toList()));
        schedule.setDestinationPath(destinationField.getText());
        schedule.setFrequency(frequencyCombo.getValue());
        schedule.setIntervalValue(intervalSpinner.getValue());
        schedule.setTimeOfDay(timeField.getText());
        schedule.setDayOfWeek(dayOfWeekCombo.getValue());
        schedule.setEnabled(enabledCheck.isSelected());
        schedule.setNextRun(com.lensora.lensorastudio.backup.engine.BackupScheduler.computeNextRun(schedule, LocalDateTime.now()));

        if (onSaved != null) onSaved.accept(schedule);
        closeDialog();
    }

    private void closeDialog()
    {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        if (stage != null) stage.close();
    }
}