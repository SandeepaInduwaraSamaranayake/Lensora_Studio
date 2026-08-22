package com.lensora.lensorastudio.feature.project.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.function.Consumer;

import com.lensora.lensorastudio.feature.project.model.ProjectNote;
import com.lensora.lensorastudio.ui.controller.DialogController;

public class NoteEditController implements DialogController
{
    @FXML private TextField titleField;
    @FXML private TextArea contentArea;
    @FXML private Button btnCancel, btnSave;

    private int projectId;
    private ProjectNote existing;
    private Consumer<ProjectNote> onSaved;


    @FXML
    public void initialize()
    {
        setupButtonActions();
        setupBindings();
    }

    private void setupButtonActions()
    {
        btnCancel.setOnAction(e -> closeDialog());
        btnSave.setOnAction(e -> saveAndClose());
    }

    // ─── Declarative Bindings ───────────────────────────────────────────────

    private void setupBindings()
    {
        // Disables save button automatically whenever content is empty or whitespace-only
        BooleanBinding isContentBlank = Bindings.createBooleanBinding(
            () -> contentArea.getText() == null || contentArea.getText().isBlank(),
            contentArea.textProperty()
        );

        btnSave.disableProperty().bind(isContentBlank);
    }

    public void setContext(int projectId, ProjectNote existingNote, Consumer<ProjectNote> onSaved)
    {
        this.projectId = projectId;
        this.existing = existingNote;
        this.onSaved = onSaved;

        if (existingNote != null)
        {
            titleField.setText(existingNote.getNoteTitle());
            contentArea.setText(existingNote.getNoteContent());
        }
    }

    private void saveAndClose()
    {
        ProjectNote note = existing != null ? existing : new ProjectNote(projectId, null, null);
        note.setNoteTitle(titleField.getText() == null || titleField.getText().isBlank()
                ? null : titleField.getText().trim());
        note.setNoteContent(contentArea.getText().trim());

        if (onSaved != null) onSaved.accept(note);
        closeDialog();
    }

    private void closeDialog()
    {
        Stage stage = (Stage) btnCancel.getScene().getWindow();
        if (stage != null)
        {
            stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
        }
    }
}