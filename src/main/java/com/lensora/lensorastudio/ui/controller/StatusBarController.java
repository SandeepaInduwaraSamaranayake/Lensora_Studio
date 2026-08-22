package com.lensora.lensorastudio.ui.controller;

import java.util.LinkedList;
import java.util.Queue;

import com.lensora.lensorastudio.feature.project.viewmodel.ProjectsViewModel;
import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;
import com.lensora.lensorastudio.ui.viewmodel.StatusBarViewModel;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
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
    private final Queue<Runnable> pendingTrackRequests = new LinkedList<>();

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
                (ListChangeListener<Object>) c -> updateCount.run());
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

            // Attach state listener instead of using setOnSucceeded/Failed/Cancelled.
            // These setter methods overwrite any previously assigned handler.
            // The listener pattern avoids this collision.
            ChangeListener<Worker.State> stateListener = new ChangeListener<>() {
                @Override
                public void changed(ObservableValue<? extends Worker.State> obs, Worker.State oldState, Worker.State newState) 
                {
                    if (newState == Worker.State.SUCCEEDED || newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) 
                    {
                        task.stateProperty().removeListener(this);
                        cleanup.run();
                    }
                }
            };

            // If the task finished before tracking started, cleanup immediately
            Worker.State state = task.getState();
            if (state == Worker.State.SUCCEEDED || state == Worker.State.FAILED || state == Worker.State.CANCELLED)
            {
                cleanup.run();
            }
            else
            {
                task.stateProperty().addListener(stateListener);
            }
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