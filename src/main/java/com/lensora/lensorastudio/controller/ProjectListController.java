package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.viewmodel.ProjectsViewModel;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class ProjectListController
{
    @FXML private TableView<Project> projectTable;
    @FXML private TableColumn<Project, String> colProjectNumber, colClientName, colStatus;
    @FXML private Label lblProjectCount;
    @FXML private ComboBox<String> cmbStatusFilter;

    // Reminders tab — wired to a real RemindersViewModel later; placeholders for now
    // so the FXML loads and the tab isn't blank/broken.
    @FXML private Label lblReminderCount;
    @FXML private CheckBox chkShowCompleted;
    @FXML private TableView<Object> reminderTable;
    @FXML private TableColumn<Object, String> colReminderDate, colReminderProject, colReminderTitle;

    private ProjectsViewModel viewModel;
    private boolean syncingSelection = false;
    private Runnable onRowClicked;

    public void bind(ProjectsViewModel viewModel)
    {
        this.viewModel = viewModel;

        colProjectNumber.setCellValueFactory(c -> c.getValue().projectNumberProperty());
        colClientName.setCellValueFactory(c -> c.getValue().clientNameProperty());
        colStatus.setCellValueFactory(c -> c.getValue().projectStatusProperty());
        projectTable.setItems(viewModel.getFilteredProjects());

        cmbStatusFilter.getItems().add("All Statuses");
        cmbStatusFilter.getItems().addAll(Project.ALL_STATUSES);
        cmbStatusFilter.setValue("All Statuses");
        cmbStatusFilter.valueProperty().addListener((obs, old, val) -> {
            viewModel.filterByStatus(val);
            updateCountLabel();
        });

        projectTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (syncingSelection) return;
            viewModel.setSelectedProject(newVal);
        });

        viewModel.selectedProjectProperty().addListener((obs, oldVal, newVal) -> {
            if (projectTable.getSelectionModel().getSelectedItem() == newVal) return;
            syncingSelection = true;
            projectTable.getSelectionModel().select(newVal);
            syncingSelection = false;
        });

        viewModel.getFilteredProjects().addListener(
                (javafx.collections.ListChangeListener<Project>) c -> updateCountLabel());
        updateCountLabel();

        projectTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1 && AppSettings.getInstance().getClearSearchOnProjectSelect()
                    && projectTable.getSelectionModel().getSelectedItem() != null && onRowClicked != null)
            {
                Platform.runLater(() -> onRowClicked.run());
            }
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
}