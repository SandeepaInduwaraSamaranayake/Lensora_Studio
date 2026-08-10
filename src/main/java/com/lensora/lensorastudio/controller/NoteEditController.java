package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.model.ProjectNote;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.function.Consumer;

public class NoteEditController implements DialogController
{
    @FXML private TextField titleField;
    @FXML private TextArea contentArea;
    @FXML private Button btnCancel, btnSave;

    private int projectId;
    private ProjectNote existing;
    private Consumer<ProjectNote> onSaved;

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
        updateSaveEnabled();
    }

    @FXML
    public void initialize()
    {
        btnCancel.setOnAction(e -> closeDialog());
        btnSave.setOnAction(e -> saveAndClose());
        contentArea.textProperty().addListener((obs, old, val) -> updateSaveEnabled());
    }

    private void updateSaveEnabled()
    {
        btnSave.setDisable(contentArea.getText() == null || contentArea.getText().isBlank());
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
        if (stage != null) stage.close();
    }
}