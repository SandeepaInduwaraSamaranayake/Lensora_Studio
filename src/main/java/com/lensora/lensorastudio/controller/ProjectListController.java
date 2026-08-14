package com.lensora.lensorastudio.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.viewmodel.ProjectsViewModel;

import javafx.collections.ListChangeListener;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ProjectListController
{
    @FXML private TableView<Project> projectTable;
    @FXML private TableColumn<Project, String> colProjectNumber, colClientName, colStatus;
    @FXML private Label lblProjectCount;
    @FXML private ComboBox<String> cmbStatusFilter;
    @FXML private MenuItem ctxProjectBackup, ctxProjectArchive;

    // Reminders tab - wired to a real RemindersViewModel later; placeholders for now
    // so the FXML loads and the tab isn't blank/broken.
    @FXML private Label lblReminderCount;
    @FXML private CheckBox chkShowCompleted;
    @FXML private TableView<Object> reminderTable;
    @FXML private TableColumn<Object, String> colReminderDate, colReminderProject, colReminderTitle;

    private ProjectsViewModel viewModel;
    private boolean syncingSelection = false;
    private Runnable onRowClicked;
    private Consumer<List<Project>> onBackupRequested;
    private Consumer<Project> onArchiveRequested;

    public void bind(ProjectsViewModel viewModel)
    {
        // set selection mode to multiple so that the user can select multiple projects for backup or archive
        projectTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        this.viewModel = viewModel;

        colProjectNumber.setCellValueFactory(c -> c.getValue().projectNumberProperty());
        colClientName.setCellValueFactory(c -> c.getValue().clientNameProperty());
        colStatus.setCellValueFactory(c -> c.getValue().projectStatusProperty());

        // FilteredList → SortedList → TableView
        SortedList<Project> sorted = new SortedList<>(viewModel.getFilteredProjects());
        sorted.comparatorProperty().bind(projectTable.comparatorProperty());
        projectTable.setItems(sorted);

        cmbStatusFilter.getItems().clear();
        cmbStatusFilter.getItems().add("All Statuses");
        cmbStatusFilter.getItems().addAll(Project.ALL_STATUSES);
        
        // Apply the user's configured default status filter on first load,
        // instead of always defaulting to "All Statuses".
        String defaultStatus = AppSettings.getInstance().getDefaultProjectStatus();
        String initialFilter = Arrays.asList(Project.ALL_STATUSES).contains(defaultStatus)
                ? defaultStatus : "All Statuses";
        cmbStatusFilter.setValue(initialFilter);
        viewModel.filterByStatus(initialFilter);

        cmbStatusFilter.valueProperty().addListener((obs, old, val) -> {
            if (val != null) 
            {
                viewModel.filterByStatus(val);
                updateCountLabel();
            }
        });

        projectTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (syncingSelection) return;
            // Prevent wiping detail view if table selection temporarily drops to null during filtering/refresh
            if (newVal == null && !projectTable.getItems().isEmpty() && oldVal != null && projectTable.getItems().contains(oldVal)) 
            {
                return;
            }
            viewModel.setSelectedProject(newVal);
        });


        viewModel.selectedProjectProperty().addListener((obs, oldVal, newVal) -> {
            if (projectTable.getSelectionModel().getSelectedItem() == newVal) return;

            syncingSelection = true;
            if (newVal == null) 
            {
                projectTable.getSelectionModel().clearSelection();
            } 
            else 
            {
                // Preserves focus while selecting without resetting whole selection if already selected
                projectTable.getSelectionModel().select(newVal);
            }
            syncingSelection = false;
        });

        // Future updates: whenever the filtered list changes later
        viewModel.getFilteredProjects().addListener(
                (ListChangeListener<Project>) c -> updateCountLabel());

        //Initial value: set the label right now, on bind
        updateCountLabel();

        // Handle Row Click cleanly without deferring layout mutations via Platform.runLater
        projectTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1
                        && AppSettings.getInstance().getClearSearchOnProjectSelect()
                        && projectTable.getSelectionModel().getSelectedItem() != null 
                        && onRowClicked != null)
            {
                onRowClicked.run();
            }
        });

        ctxProjectBackup.setOnAction(e -> {
            List<Project> selected = new ArrayList<>(projectTable.getSelectionModel().getSelectedItems());
            if (!selected.isEmpty() && onBackupRequested != null) onBackupRequested.accept(selected);
        });

        ctxProjectArchive.setOnAction(e -> {
            Project selected = projectTable.getSelectionModel().getSelectedItem();
            if (selected != null && onArchiveRequested != null) onArchiveRequested.accept(selected);
        });

        // Reminders tab: no data source yet, just keep the UI honest.
        lblReminderCount.setText("0 reminders");
        reminderTable.setPlaceholder(new Label("No upcoming reminders."));
    }

    public void setOnRowClicked(Runnable callback) { this.onRowClicked = callback; }

    private void updateCountLabel()
    {
        lblProjectCount.setText(viewModel.getFilteredCount() + " projects");
    }

    public void setOnBackupRequested(Consumer<List<Project>> callback)
    {
        this.onBackupRequested = callback;
    }

    public void setOnArchiveRequested(Consumer<Project> callback)
    {
        this.onArchiveRequested = callback;
    }
}