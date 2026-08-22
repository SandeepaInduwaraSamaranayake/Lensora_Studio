package com.lensora.lensorastudio.feature.project.controller;

import com.lensora.lensorastudio.core.config.Resources;
import com.lensora.lensorastudio.feature.project.model.Project;
import com.lensora.lensorastudio.feature.project.model.ProjectNote;
import com.lensora.lensorastudio.feature.project.repository.ProjectLastFolderRepository;
import com.lensora.lensorastudio.feature.project.repository.ProjectNoteRepository;
import com.lensora.lensorastudio.feature.project.repository.ProjectRepository;
import com.lensora.lensorastudio.feature.project.viewmodel.ProjectsViewModel;
import com.lensora.lensorastudio.ui.dialogs.DialogBuilder;
import com.lensora.lensorastudio.ui.dialogs.Dialogs;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;
import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;

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
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.time.format.DateTimeFormatter;

import org.kordamp.ikonli.javafx.FontIcon;
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
    @FXML private ComboBox<String> detStatusCombo;
    @FXML private DatePicker detEventDatePicker, detDueDatePicker;

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
    private boolean editMode = false;

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a");

    public void bind(ProjectsViewModel viewModel)
    {
        this.viewModel = viewModel;

        viewModel.selectedProjectProperty().addListener((obs, oldVal, newVal) -> {
            if (Objects.equals(oldVal, newVal))
            {
                return;
            }
            exitEditMode(false); // discard any in-progress edit when switching projects
            loadProject(newVal);
        });
        loadProject(viewModel.getSelectedProject());

        setEditControlsVisible(false);
        detStatusCombo.getItems().setAll(Project.ALL_STATUSES);

        btnDetailOpenFolder.setOnAction(e -> openProjectFolder());
        btnDetailEdit.setOnAction(e -> onEditButtonClicked());
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
        System.out.println("DEBUG: loadProject called for: " + (project != null ? project.getProjectNumber() : "null"));
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

        detStatusCombo.setValue(project.getProjectStatus());
        detEventDatePicker.setValue(project.getEventDate());
        detDueDatePicker.setValue(project.getDueDate());

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

        // ─── Edit mode ──────────────────────────────────────────────────────────

    private void onEditButtonClicked()
    {
        if (editMode)
        {
            saveEdits();
        }
        else
        {
            enterEditMode();
        }
    }

    private void enterEditMode()
    {
        System.out.println("DEBUG: enterEditMode() called");
        if (viewModel.getSelectedProject() == null) return;

        editMode = true;
        setEditControlsVisible(true);
        setFieldsEditable(true);

        FontIcon icon = new FontIcon("fas-save");
        icon.getStyleClass().add("icon-size-10");
        btnDetailEdit.setGraphic(icon);
        btnDetailEdit.setTooltip(new Tooltip("Save Changes"));
    }

    private void exitEditMode(boolean reloadFromModel)
    {
        System.out.println("DEBUG: exitEditMode() called");
        editMode = false;
        setEditControlsVisible(false);
        setFieldsEditable(false);

        FontIcon icon = new FontIcon("fas-edit");
        icon.getStyleClass().add("icon-size-10");
        btnDetailEdit.setGraphic(icon);
        btnDetailEdit.setTooltip(new Tooltip("Edit Project"));

        if (reloadFromModel)
        {
            loadProject(viewModel.getSelectedProject());
        }
    }

    /** Swaps the read-only display fields and their edit-mode counterparts (ComboBox/DatePicker) in/out of view. */
    private void setEditControlsVisible(boolean editing)
    {
        detStatus.setVisible(!editing);
        detStatus.setManaged(!editing);
        detStatusCombo.setVisible(editing);
        detStatusCombo.setManaged(editing);

        detEventDate.setVisible(!editing);
        detEventDate.setManaged(!editing);
        detEventDatePicker.setVisible(editing);
        detEventDatePicker.setManaged(editing);

        detDueDate.setVisible(!editing);
        detDueDate.setManaged(!editing);
        detDueDatePicker.setVisible(editing);
        detDueDatePicker.setManaged(editing);
    }

    private void setFieldsEditable(boolean editable)
    {
        detClientName.setEditable(editable);
        detClientPhone.setEditable(editable);
        detClientEmail.setEditable(editable);
        detEventType.setEditable(editable);
        detPackage.setEditable(editable);
        detTotalAmount.setEditable(editable);
        detAdvanceAmount.setEditable(editable);
        detRemarks.setEditable(editable);
        // detProjectNumber, detProjectPath, detBalanceAmount stay read-only always.
    }

    private void saveEdits()
    {
        Project current = viewModel.getSelectedProject();
        if (current == null) return;

        if (detClientName.getText() == null || detClientName.getText().isBlank())
        {
            Dialogs.showInfo(getWindow(), "Save Project", null, "Client name is required.");
            return;
        }

        BigDecimal total, advance;
        try
        {
            total = parseCurrencyOrZero(detTotalAmount.getText());
            advance = parseCurrencyOrZero(detAdvanceAmount.getText());
        }
        catch (NumberFormatException e)
        {
            Dialogs.showInfo(getWindow(), "Save Project", null, "Total and advance amounts must be valid numbers.");
            return;
        }

        current.setClientName(detClientName.getText().trim());
        current.setClientPhone(detClientPhone.getText());
        current.setClientEmail(detClientEmail.getText());
        current.setEventType(detEventType.getText());
        current.setEventDate(detEventDatePicker.getValue());
        current.setDueDate(detDueDatePicker.getValue());
        current.setProjectStatus(detStatusCombo.getValue() != null ? detStatusCombo.getValue() : current.getProjectStatus());
        current.setPackageName(detPackage.getText());
        current.setTotalAmount(total);
        current.setAdvanceAmount(advance);
        current.setBalanceAmount(total.subtract(advance));
        current.setRemarks(detRemarks.getText());

        try
        {
            ProjectRepository.update(current);
            viewModel.refreshSelectedFromDb();
            exitEditMode(true);
            NotificationUtil.showToast(getWindow(), "Project details saved.");
        }
        catch (SQLException e)
        {
            logger.error("Failed to save project {}", current.getProjectId(), e);
            ErrorHandler.show(getWindow(), "Failed to save project", e);
        }
    }

    private BigDecimal parseCurrencyOrZero(String text)
    {
        if (text == null || text.trim().isEmpty()) return BigDecimal.ZERO;
        return new BigDecimal(text.replaceAll("[^\\d.]", ""));
    }

    private Window getWindow()
    {
        return notesContainer.getScene() != null ? notesContainer.getScene().getWindow() : null;
    }

    // ─── Folder ──────────────────────────────────────────────────────────────

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
        logger.info("Loading notes for project ID: {}", projectId);
        notesContainer.getChildren().clear();

        CompletableFuture.supplyAsync(() -> {
            try 
            {
                return ProjectNoteRepository.findByProject(projectId);
            } 
            catch (SQLException e) 
            {
                throw new RuntimeException(e);
            }
        }).thenAcceptAsync(notes -> {
            // Double check: confirm current selected project still matches before populating
            Project current = viewModel.getSelectedProject();
            if (current == null || current.getProjectId() != projectId) 
            {
                return; // Discard stale background query result
            }

            notesContainer.getChildren().clear();
            if (notes.isEmpty()) 
            {
                notesContainer.getChildren().add(new Label("No notes yet. Click \"+ Add Note\" to create one."));
                return;
            }

            for (ProjectNote note : notes) 
            {
                notesContainer.getChildren().add(buildNoteCard(note));
            }
        }, Platform::runLater)
        .exceptionally(ex -> {
            Platform.runLater(() -> {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                logger.error("Failed to load notes for project {}", projectId, cause);
                ErrorHandler.show(null, "Failed to load notes", cause);
            });
            return null;
        });
    }

    private VBox buildNoteCard(ProjectNote note)
    {
        Label titleLabel = new Label(
                note.getNoteTitle() != null && !note.getNoteTitle().isBlank()
                        ? note.getNoteTitle()
                        : "(Untitled Note)");
        titleLabel.setStyle("-fx-font-weight: bold;");

        Label dateLabel = new Label(note.getCreatedAt() != null ? note.getCreatedAt().format(DATE_TIME_FORMAT) : "");
        dateLabel.setStyle("-fx-opacity: 0.6;");

        Label contentLabel = new Label(note.getNoteContent());
        contentLabel.setWrapText(true);
        contentLabel.setMinWidth(0);
        contentLabel.setMaxWidth(Double.MAX_VALUE);

        Button editBtn = new Button("Edit");
        editBtn.setOnAction(e -> editNote(note));

        Button deleteBtn = new Button("Delete");
        deleteBtn.setOnAction(e -> deleteNote(note));

        HBox header = new HBox(8, titleLabel, spacer(), dateLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox actions = new HBox(6, editBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox card = new VBox(6, header, contentLabel, actions);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
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

        Stage owner = (Stage) getWindow();
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

        Stage owner = (Stage) getWindow();
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
        System.out.println("DEBUG: restoreLastFolderFor called for: " + (project != null ? project.getProjectNumber() : "null"));
        if (onRestoreLastFolder == null || project == null) return;

        final int targetProjectId = project.getProjectId();

        CompletableFuture.supplyAsync(() -> {
            try 
            {
                return ProjectLastFolderRepository.findByProject(targetProjectId);
            } 
            catch (SQLException e)
            {
                logger.warn("Failed to load last-visited folder for project {}", targetProjectId, e);
                return null;
            }
        }).thenAcceptAsync(relativePath -> {
            // Guard against race conditions: verify the selected project hasn't changed
            Project current = viewModel.getSelectedProject();
            if (current == null || current.getProjectId() != targetProjectId) 
            {
                return; // Discard stale result if user switched projects
            }

            if (relativePath != null && !relativePath.isBlank()) 
            {
                onRestoreLastFolder.accept(relativePath);
            }
        }, Platform::runLater);
    }

    public void setOnRestoreLastFolder(Consumer<String> callback)
    {
        this.onRestoreLastFolder = callback;
    }
}