package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.util.NotificationUtil;
import com.lensora.lensorastudio.viewmodel.ProjectsViewModel;
import com.lensora.lensorastudio.viewmodel.StatusBarViewModel;

import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;

public class StatusBarController
{
    @FXML private Label lblStatusText, lblStatusProjects, lblStatusReminders, lblStatusPath;
    @FXML private HBox progressContainer;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel, progressSpeedLabel, progressEtaLabel;

    private Task<?> currentTrackedTask;
    private final java.util.Queue<Runnable> pendingTrackRequests = new java.util.LinkedList<>();

    @FXML
    public void initialize()
    {
        // Copy path to clipboard on click
        lblStatusPath.setOnMouseClicked(event -> {
            String path = lblStatusPath.getText();
            if (path != null && !path.trim().isEmpty()) 
            {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();
                content.putString(path);
                clipboard.setContent(content);
                NotificationUtil.showToast(lblStatusPath, "The current path has been copied to the clipboard.");
            }
        });
    }

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

    /**
     * Generic API: binds the shared status-bar progress widgets to ANY
     * JavaFX Task, showing its live progress/message and automatically
     * hiding again when it finishes. Not limited to file operations —
     * FileOperationsManager's copy/paste, BackupJob, RestoreJob, or any
     * future background task can all reuse this same indicator.
     *
     * If another task is already being tracked, this one queues silently
     * behind it (simple mutex) rather than visually fighting over the same
     * progress bar.
     */
    public void trackTask(String title, Task<?> task)
    {
        Runnable startTracking = () -> {
            currentTrackedTask = task;

            progressContainer.setVisible(true);
            progressContainer.setManaged(true);

            progressLabel.textProperty().bind(Bindings.createStringBinding(
                    () -> task.getProgress() < 0 ? "…" : String.format("%.0f%%", task.getProgress() * 100),
                    task.progressProperty()));
            progressBar.progressProperty().bind(task.progressProperty());
            progressSpeedLabel.textProperty().bind(task.messageProperty()); // reuse this slot for status text
            progressEtaLabel.setText(title);

            Runnable cleanup = () -> {
                progressLabel.textProperty().unbind();
                progressBar.progressProperty().unbind();
                progressSpeedLabel.textProperty().unbind();
                progressContainer.setVisible(false);
                progressContainer.setManaged(false);
                currentTrackedTask = null;

                Runnable next = pendingTrackRequests.poll();
                if (next != null) next.run();
            };

            task.setOnSucceeded(e -> cleanup.run());
            task.setOnFailed(e -> cleanup.run());
            task.setOnCancelled(e -> cleanup.run());
        };

        if (currentTrackedTask == null)
        {
            startTracking.run();
        }
        else
        {
            pendingTrackRequests.add(startTracking);
        }
    }

    // Exposed so FileExplorerController can attach the FileCopyTask progress bindings.
    public HBox getProgressContainer()   { return progressContainer; }
    public ProgressBar getProgressBar()  { return progressBar; }
    public Label getProgressLabel()      { return progressLabel; }
    public Label getProgressSpeedLabel() { return progressSpeedLabel; }
    public Label getProgressEtaLabel()   { return progressEtaLabel; }
}