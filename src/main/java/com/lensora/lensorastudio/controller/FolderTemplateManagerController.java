package com.lensora.lensorastudio.controller;

import com.lensora.lensorastudio.repository.FolderTemplateRepository;
import com.lensora.lensorastudio.repository.FolderTemplateRepository.FolderTemplate;
import com.lensora.lensorastudio.util.Dialogs;
import com.lensora.lensorastudio.util.ErrorHandler;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import javafx.util.StringConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class FolderTemplateManagerController implements DialogController
{
    private static final Logger logger = LoggerFactory.getLogger(FolderTemplateManagerController.class);

    @FXML private ListView<FolderTemplate> templateListView;
    @FXML private Button btnNewTemplate;

    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private ListView<String> folderListView;

    @FXML private TextField newFolderField;
    @FXML private Button btnAddFolder, btnRemoveFolder, btnMoveFolderUp, btnMoveFolderDown;

    @FXML private Button btnDeleteTemplate, btnSaveTemplate, btnClose;

    private final ObservableList<FolderTemplate> templates = FXCollections.observableArrayList();
    private final ObservableList<String> folderNames = FXCollections.observableArrayList();

    private Runnable onTemplatesChanged;


    /** Called by MainController right after DialogBuilder loads this controller. */
    public void setOnTemplatesChanged(Runnable callback)
    {
        this.onTemplatesChanged = callback;
    }

    // ─── Initialisation ─────────────────────────────────────────────────────

    @FXML
    public void initialize()
    {
        logger.info("[FolderTemplateManagerController] Initializing...");

        setupTemplateList();
        setupFolderList();
        setupButtonActions();

        reloadTemplates();
    }

    private void setupTemplateList()
    {
        templateListView.setItems(templates);
        templateListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(FolderTemplate item, boolean empty)
            {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });

        templateListView.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, selected) -> onTemplateSelected(selected));
    }

    private void setupFolderList()
    {
        folderListView.setItems(folderNames);
        folderListView.setCellFactory(lv -> new TextFieldListCell<>(new StringConverter<>() {
            @Override public String toString(String s) { return s; }
            @Override public String fromString(String s) { return s; }
        }));
    }

    private void setupButtonActions()
    {
        btnNewTemplate.setOnAction(e -> onNewTemplate());
        btnSaveTemplate.setOnAction(e -> onSaveTemplate());
        btnDeleteTemplate.setOnAction(e -> onDeleteTemplate());

        btnAddFolder.setOnAction(e -> onAddFolder());
        btnRemoveFolder.setOnAction(e -> onRemoveFolder());
        btnMoveFolderUp.setOnAction(e -> onMoveFolder(-1));
        btnMoveFolderDown.setOnAction(e -> onMoveFolder(1));

        newFolderField.setOnAction(e -> onAddFolder()); // Enter key adds too
        btnClose.setOnAction(e -> closeDialog());
    }

    // ─── Loading ─────────────────────────────────────────────────────────────

    private void reloadTemplates()
    {
        FolderTemplate previouslySelected = templateListView.getSelectionModel().getSelectedItem();

        try
        {
            List<FolderTemplate> loaded = FolderTemplateRepository.findAll();
            templates.setAll(loaded);
        }
        catch (SQLException e)
        {
            logger.error("Failed to load folder templates", e);
            ErrorHandler.show(getOwnerWindow(), "Failed to load templates", e);
            return;
        }

        if (previouslySelected != null)
        {
            templates.stream()
                    .filter(t -> t.id() == previouslySelected.id())
                    .findFirst()
                    .ifPresentOrElse(
                            t -> templateListView.getSelectionModel().select(t),
                            () -> selectFirstOrClear());
        }
        else
        {
            selectFirstOrClear();
        }
    }

    private void selectFirstOrClear()
    {
        if (!templates.isEmpty())
        {
            templateListView.getSelectionModel().selectFirst();
        }
        else
        {
            onTemplateSelected(null);
        }
    }

    private void onTemplateSelected(FolderTemplate selected)
    {
        if (selected == null)
        {
            nameField.clear();
            descriptionField.clear();
            folderNames.clear();
            return;
        }

        nameField.setText(selected.name());
        descriptionField.setText(selected.description() != null ? selected.description() : "");

        try
        {
            folderNames.setAll(FolderTemplateRepository.getFolderNames(selected.id()));
        }
        catch (SQLException e)
        {
            logger.error("Failed to load folders for template {}", selected.id(), e);
            ErrorHandler.show(getOwnerWindow(), "Failed to load template folders", e);
        }
    }

    // ─── Folder list editing ────────────────────────────────────────────────

    private void onAddFolder()
    {
        String name = newFolderField.getText();
        if (name == null || name.isBlank()) return;

        String sanitized = sanitizeFolderName(name.trim());
        if (sanitized.isEmpty())
        {
            Dialogs.showInfo(getOwnerWindow(), "New Folder", null, "Invalid folder name.");
            return;
        }
        if (folderNames.contains(sanitized))
        {
            Dialogs.showInfo(getOwnerWindow(), "New Folder", null, "That folder is already in the list.");
            return;
        }

        folderNames.add(sanitized);
        newFolderField.clear();
    }

    private void onRemoveFolder()
    {
        String selected = folderListView.getSelectionModel().getSelectedItem();
        if (selected != null) folderNames.remove(selected);
    }

    private void onMoveFolder(int direction)
    {
        int idx = folderListView.getSelectionModel().getSelectedIndex();
        int targetIdx = idx + direction;
        if (idx < 0 || targetIdx < 0 || targetIdx >= folderNames.size()) return;

        Collections.swap(folderNames, idx, targetIdx);
        folderListView.getSelectionModel().select(targetIdx);
    }

    private String sanitizeFolderName(String name)
    {
        return name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    // ─── Template CRUD ──────────────────────────────────────────────────────

    private void onNewTemplate()
    {
        TextInputDialog dialog = new TextInputDialog("New Template");
        dialog.setTitle("New Template");
        dialog.setHeaderText(null);
        dialog.setContentText("Template name:");

        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.isBlank()) return;

            try
            {
                if (FolderTemplateRepository.nameExists(name.trim(), null))
                {
                    Dialogs.showInfo(getOwnerWindow(), "New Template", null, "A template with that name already exists.");
                    return;
                }

                int newId = FolderTemplateRepository.insert(name.trim(), "", List.of());
                reloadTemplates();

                templates.stream()
                        .filter(t -> t.id() == newId)
                        .findFirst()
                        .ifPresent(t -> templateListView.getSelectionModel().select(t));

                notifyTemplatesChanged();
            }
            catch (SQLException e)
            {
                logger.error("Failed to create template", e);
                ErrorHandler.show(getOwnerWindow(), "Failed to create template", e);
            }
        });
    }

    private void onSaveTemplate()
    {
        FolderTemplate selected = templateListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        String name = nameField.getText();
        if (name == null || name.isBlank())
        {
            Dialogs.showInfo(getOwnerWindow(), "Save Template", null, "Template name is required.");
            return;
        }

        try
        {
            if (FolderTemplateRepository.nameExists(name.trim(), selected.id()))
            {
                Dialogs.showInfo(getOwnerWindow(), "Save Template", null, "Another template already uses that name.");
                return;
            }

            FolderTemplateRepository.update(
                    selected.id(), name.trim(), descriptionField.getText(), List.copyOf(folderNames));

            reloadTemplates();
            notifyTemplatesChanged();
            Dialogs.showInfo(getOwnerWindow(), "Save Template", null, "Template saved.");
        }
        catch (SQLException e)
        {
            logger.error("Failed to save template {}", selected.id(), e);
            ErrorHandler.show(getOwnerWindow(), "Failed to save template", e);
        }
    }

    private void onDeleteTemplate()
    {
        FolderTemplate selected = templateListView.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Template");
        confirm.setHeaderText(null);
        confirm.setContentText("Delete template \"" + selected.name()
                + "\"? Existing projects created from it are unaffected.");

        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;

            try
            {
                FolderTemplateRepository.delete(selected.id());
                reloadTemplates();
                notifyTemplatesChanged();
            }
            catch (SQLException e)
            {
                logger.error("Failed to delete template {}", selected.id(), e);
                ErrorHandler.show(getOwnerWindow(), "Failed to delete template", e);
            }
        });
    }

    private void notifyTemplatesChanged()
    {
        if (onTemplatesChanged != null) onTemplatesChanged.run();
    }

    // ─── Window helpers ─────────────────────────────────────────────────────

    private Window getOwnerWindow()
    {
        return btnClose.getScene() != null
                ? btnClose.getScene().getWindow()
                : null;
    }

    private void closeDialog() 
    {
        Stage stage = (Stage) btnClose.getScene().getWindow();
        // Fire a window close request – this will trigger the handler set in DialogBuilder
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }
}