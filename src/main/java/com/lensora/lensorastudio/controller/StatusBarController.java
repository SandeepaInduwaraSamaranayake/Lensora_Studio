package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.viewmodel.ProjectsViewModel;
import com.lensora.lensorastudio.viewmodel.StatusBarViewModel;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;

public class StatusBarController
{
    @FXML private Label lblStatusText, lblStatusProjects, lblStatusReminders, lblStatusPath;
    @FXML private HBox progressContainer;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel, progressSpeedLabel, progressEtaLabel;

    public void bind(StatusBarViewModel vm, ProjectsViewModel projectsViewModel)
    {
        lblStatusText.textProperty().bind(vm.statusTextProperty());
        lblStatusProjects.textProperty().bind(vm.projectsCountProperty());
        lblStatusReminders.textProperty().bind(vm.remindersCountProperty());
        lblStatusPath.textProperty().bind(vm.currentPathProperty());

        // Keep "Projects: N" in sync with the filtered list without
        // StatusBarController needing to know about TableView internals.
        Runnable updateCount = () -> vm.projectsCountProperty()
                .set("Projects: " + projectsViewModel.getFilteredCount());
        projectsViewModel.getFilteredProjects().addListener(
                (javafx.collections.ListChangeListener<Object>) c -> updateCount.run());
        updateCount.run();
    }

    // Exposed so FileExplorerController can attach the FileCopyTask progress bindings.
    public HBox getProgressContainer()   { return progressContainer; }
    public ProgressBar getProgressBar()  { return progressBar; }
    public Label getProgressLabel()      { return progressLabel; }
    public Label getProgressSpeedLabel() { return progressSpeedLabel; }
    public Label getProgressEtaLabel()   { return progressEtaLabel; }
}