package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.model.Project;
import com.lensora.lensorastudio.model.ProjectNote;
import com.lensora.lensorastudio.repository.ProjectLastFolderRepository;
import com.lensora.lensorastudio.repository.ProjectNoteRepository;
import com.lensora.lensorastudio.util.DialogBuilder;
import com.lensora.lensorastudio.util.ErrorHandler;
import com.lensora.lensorastudio.util.Resources;
import com.lensora.lensorastudio.viewmodel.ProjectsViewModel;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProjectDetailsController
{
    private static final Logger logger = LoggerFactory.getLogger(ProjectDetailsController.class);

    // Overview tab
    @FXML private TextField detProjectNumber, detClientName, detClientPhone, detClientEmail,
            detEventType, detEventDate, detDueDate, detStatus, detPackage, detProjectPath,
            detTotalAmount, detAdvanceAmount, detBalanceAmount;
    @FXML private TextArea detRemarks;
    @FXML private HBox progressStepper;
    @FXML private Button btnDetailEdit, btnDetailOpenFolder, btnDetailArchive;

    // Notes tab
    @FXML private VBox notesContainer;
    @FXML private Button btnAddNote;

    // Reminders tab (project-scoped)
    @FXML private TableView<Object> projectReminderTable;
    @FXML private TableColumn<Object, String> colProjRemDate, colProjRemTitle, colProjRemDone;
    @FXML private Button btnAddReminder;

    // Payments tab
    @FXML private TableView<Object> paymentTable;
    @FXML private TableColumn<Object, String> colPayDate, colPayAmount, colPayMethod, colPayRef;
    @FXML private Button btnAddPayment;

    private ProjectsViewModel viewModel;
    private Consumer<String> onProjectPathChanged;
    private Consumer<String> onRestoreLastFolder;

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a");

    public void bind(ProjectsViewModel viewModel)
    {
        this.viewModel = viewModel;

        viewModel.selectedProjectProperty().addListener((obs, oldVal, newVal) -> loadProject(newVal));
        loadProject(viewModel.getSelectedProject());

        btnDetailOpenFolder.setOnAction(e -> openProjectFolder());

        // Not implemented yet - wired so the buttons don't silently do nothing.
        btnDetailEdit.setOnAction(e -> { });
        btnDetailArchive.setOnAction(e -> { /* TODO: archive project */ });
        btnAddNote.setOnAction(e -> {  addNote(); });
        btnAddReminder.setOnAction(e -> { /* TODO: insert into reminder table */ });
        btnAddPayment.setOnAction(e -> { /* TODO: insert into payment table */ });

        projectReminderTable.setPlaceholder(new Label("No reminders for this project."));
        paymentTable.setPlaceholder(new Label("No payments recorded yet."));
    }

    public void setOnProjectPathChanged(Consumer<String> callback) { this.onProjectPathChanged = callback; }

    private void loadProject(Project project)
    {
        if (project == null) { clearFields(); return; }

        detProjectNumber.setText(project.getProjectNumber());
        detClientName.setText(project.getClientName());
        detClientPhone.setText(project.getClientPhone());
        detClientEmail.setText(project.getClientEmail());
        detEventType.setText(project.getEventType());
        detEventDate.setText(project.getEventDate() != null ? project.getEventDate().toString() : "");
        detDueDate.setText(project.getDueDate() != null ? project.getDueDate().toString() : "");
        detStatus.setText(project.getProjectStatus());
        detPackage.setText(project.getPackageName());
        detProjectPath.setText(project.getProjectPath());
        detTotalAmount.setText(project.getTotalAmount() != null ? project.getTotalAmount().toString() : "0.00");
        detAdvanceAmount.setText(project.getAdvanceAmount() != null ? project.getAdvanceAmount().toString() : "0.00");
        detBalanceAmount.setText(project.getBalanceAmount() != null ? project.getBalanceAmount().toString() : "0.00");
        detRemarks.setText(project.getRemarks());

        progressStepper.getChildren().clear();
        Label statusLabel = new Label("Status: " + project.getProjectStatus());
        statusLabel.setStyle("-fx-font-weight: bold;");
        progressStepper.getChildren().add(statusLabel);

        if (onProjectPathChanged != null) onProjectPathChanged.accept(project.getProjectPath());

         // restore whichever folder the user was last browsing in it.
        restoreLastFolderFor(project);

        // load notes when the project is loading
        loadNotes(project.getProjectId());
    }

    private void clearFields()
    {
        for (TextField tf : new TextField[]{detProjectNumber, detClientName, detClientPhone, detClientEmail,
                detEventType, detEventDate, detDueDate, detStatus, detPackage, detProjectPath,
                detTotalAmount, detAdvanceAmount, detBalanceAmount})
        {
            tf.setText("");
        }
        detRemarks.setText("");
        progressStepper.getChildren().clear();
        notesContainer.getChildren().clear();
    }

    private void openProjectFolder()
    {
        Project current = viewModel.getSelectedProject();
        if (current == null || current.getProjectPath() == null) return;
        // Run in the background
        CompletableFuture.runAsync(() -> {
            try
            {
                if (Desktop.isDesktopSupported())
                {
                    Desktop.getDesktop().open(new File(current.getProjectPath()));
                }
            }
            catch (IOException ex)
            {
                Platform.runLater(() ->
                    ErrorHandler.show(null, "Could not open folder", ex)
                );
            }
        });
    }


    // ─── Notes ──────────────────────────────────────────────────────────────

    private void loadNotes(int projectId)
    {
        notesContainer.getChildren().clear();

        try
        {
            List<ProjectNote> notes = ProjectNoteRepository.findByProject(projectId);
            logger.info("Loading notes for project ID: {}", projectId);
            if (notes.isEmpty())
            {
                Label empty = new Label("No notes yet. Click \"+ Add Note\" to create one.");
                notesContainer.getChildren().add(empty);
                return;
            }

            for (ProjectNote note : notes)
            {
                notesContainer.getChildren().add(buildNoteCard(note));
            }
        }
        catch (SQLException e)
        {
            logger.error("Failed to load notes for project {}", projectId, e);
            ErrorHandler.show(null, "Failed to load notes", e);
        }
    }

    private VBox buildNoteCard(ProjectNote note)
    {
        Label titleLabel = new Label(
                note.getNoteTitle() != null && !note.getNoteTitle().isBlank()
                        ? note.getNoteTitle()
                        : "(Untitled Note)");
        titleLabel.setStyle("-fx-font-weight: bold;");

        Label dateLabel = new Label(note.getCreatedAt() != null ? note.getCreatedAt().format(DATE_TIME_FORMAT) : "");
        dateLabel.setStyle("-fx-opacity: 0.6; -fx-font-size: 11px;");

        Label contentLabel = new Label(note.getNoteContent());
        contentLabel.setWrapText(true);

        Button editBtn = new Button("Edit");
        editBtn.setOnAction(e -> editNote(note));

        Button deleteBtn = new Button("Delete");
        deleteBtn.setOnAction(e -> deleteNote(note));

        HBox header = new HBox(8, titleLabel, spacer(), dateLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox actions = new HBox(6, editBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(6, header, contentLabel, actions);
        card.setPadding(new Insets(10));
        card.setStyle("""
                -fx-background-color: -color-bg-subtle;
                -fx-background-radius: 6;
                -fx-border-color: -color-border-default;
                -fx-border-radius: 6;
                """);

        return card;
    }

    private Region spacer()
    {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private void addNote()
    {
        Project current = viewModel.getSelectedProject();
        if (current == null) return;

        Stage owner = (Stage) notesContainer.getScene().getWindow();
        DialogBuilder.of(Resources.NOTE_EDIT_VIEW.url(), "Add Note", owner)
                .icon("📝")
                .resizable(true)
                .withControllerConsumer(controller -> {
                    if (controller instanceof NoteEditController nec)
                    {
                        nec.setContext(current.getProjectId(), null, note -> {
                            try
                            {
                                ProjectNoteRepository.insert(note);
                                loadNotes(current.getProjectId());
                            }
                            catch (SQLException e)
                            {
                                logger.error("Failed to save note", e);
                                ErrorHandler.show(owner, "Failed to save note", e);
                            }
                        });
                    }
                })
                .build();
    }

    private void editNote(ProjectNote note)
    {
        Project current = viewModel.getSelectedProject();
        if (current == null) return;

        Stage owner = (Stage) notesContainer.getScene().getWindow();
        DialogBuilder.of(Resources.NOTE_EDIT_VIEW.url(), "Edit Note", owner)
                .icon("📝")
                .resizable(true)
                .withControllerConsumer(controller -> {
                    if (controller instanceof NoteEditController nec)
                    {
                        nec.setContext(current.getProjectId(), note, updated -> {
                            try
                            {
                                ProjectNoteRepository.update(updated);
                                loadNotes(current.getProjectId());
                            }
                            catch (SQLException e)
                            {
                                logger.error("Failed to update note", e);
                                ErrorHandler.show(owner, "Failed to update note", e);
                            }
                        });
                    }
                })
                .build();
    }

    private void deleteNote(ProjectNote note)
    {
        Project current = viewModel.getSelectedProject();
        if (current == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Note");
        confirm.setHeaderText(null);
        confirm.setContentText("Delete this note? This cannot be undone.");
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;
            try
            {
                ProjectNoteRepository.delete(note.getNoteId());
                loadNotes(current.getProjectId());
            }
            catch (SQLException e)
            {
                logger.error("Failed to delete note", e);
                ErrorHandler.show(null, "Failed to delete note", e);
            }
        });
    }

    // ─── Restore last visited folder ──────────────────────────────────────────────
    private void restoreLastFolderFor(Project project)
    {
        if (onRestoreLastFolder == null) return;
        try
        {
            String relativePath = ProjectLastFolderRepository.findByProject(project.getProjectId());
            if (relativePath != null)
            {
                onRestoreLastFolder.accept(relativePath);
            }
        }
        catch (SQLException e)
        {
            logger.warn("Failed to load last-visited folder for project {}", project.getProjectId(), e);
        }
    }

    public void setOnRestoreLastFolder(Consumer<String> callback)
    {
        this.onRestoreLastFolder = callback;
    }
}