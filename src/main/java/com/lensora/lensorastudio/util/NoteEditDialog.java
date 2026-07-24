package com.lensora.lensorastudio.util;

import com.lensora.lensorastudio.model.ProjectNote;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Optional;

public final class NoteEditDialog
{
    private NoteEditDialog() {}

    /**
     * Shows an add/edit dialog for a note. Pass an existing ProjectNote to
     * edit it, or null to create a new one.
     *
     * @return the note with title/content populated, or empty if cancelled.
     */
    public static Optional<ProjectNote> show(Window owner, int projectId, ProjectNote existing)
    {
        boolean isEdit = existing != null;

        Dialog<ProjectNote> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Note" : "Add Note");
        dialog.setHeaderText(null);
        if (owner != null) dialog.initOwner(owner);

        ButtonType saveButtonType = new ButtonType(isEdit ? "Save" : "Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField titleField = new TextField();
        titleField.setPromptText("Title (optional)");
        if (isEdit) titleField.setText(existing.getNoteTitle());

        TextArea contentArea = new TextArea();
        contentArea.setPromptText("Note content...");
        contentArea.setWrapText(true);
        contentArea.setPrefRowCount(8);
        if (isEdit) contentArea.setText(existing.getNoteContent());

        VBox content = new VBox(10, new Label("Title"), titleField, new Label("Content"), contentArea);
        content.setPadding(new Insets(10));
        content.setPrefWidth(420);
        dialog.getDialogPane().setContent(content);

        // Disable Save until there's actual content
        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(contentArea.getText().isBlank());
        contentArea.textProperty().addListener((obs, old, val) -> saveButton.setDisable(val.isBlank()));

        dialog.setResultConverter(buttonType -> {
            if (buttonType != saveButtonType) return null;

            ProjectNote note = isEdit ? existing : new ProjectNote(projectId, null, null);
            note.setNoteTitle(titleField.getText() == null || titleField.getText().isBlank()
                    ? null : titleField.getText().trim());
            note.setNoteContent(contentArea.getText().trim());
            return note;
        });

        return dialog.showAndWait();
    }
}