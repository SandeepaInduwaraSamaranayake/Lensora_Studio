package com.lensora.lensorastudio.feature.settings.ui;

import com.lensora.lensorastudio.core.config.AppSettings;
import com.lensora.lensorastudio.feature.settings.model.ExternalApp;
import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;
import com.lensora.lensorastudio.ui.util.AppIconUtil;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;

public final class ExternalAppsDialog 
{
    private ExternalAppsDialog() {}

    public static void show(Window owner) 
    {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Manage External Apps");
        AppIconUtil.setAppIcon(stage);

        ObservableList<ExternalApp> apps = FXCollections.observableArrayList(
                AppSettings.getInstance().getExternalApps()
        );

        // ---- ListView with custom cell ----
        ListView<ExternalApp> listView = new ListView<>(apps);
        listView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(listView, Priority.ALWAYS);

        listView.setCellFactory(lv -> new ListCell<>() {
            private final VBox cellBox = new VBox(2);
            private final Label nameLabel = new Label();
            private final Label pathLabel = new Label();

            {
                cellBox.getChildren().addAll(nameLabel, pathLabel);
                nameLabel.setStyle("-fx-font-weight: bold;");
                pathLabel.setStyle("-fx-opacity: 0.7;");
            }

            @Override
            protected void updateItem(ExternalApp item, boolean empty) 
            {
                super.updateItem(item, empty);
                if (empty || item == null) 
                {
                    setText(null);
                    setGraphic(null);
                } 
                else 
                {
                    nameLabel.setText(item.getName());
                    pathLabel.setText(item.getExecutablePath());
                    setGraphic(cellBox);
                }
            }
        });

        TextField nameField = new TextField();
        nameField.setPromptText("Display name (e.g. Adobe Photoshop)");

        TextField pathField = new TextField();
        pathField.setPromptText("Path to executable or app bundle");

        Button browseButton = new Button("Browse…");
        browseButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Application Executable");
            if (System.getProperty("os.name").toLowerCase().contains("win")) 
            {
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter("Executable", "*.exe"));
            }
            File selected = chooser.showOpenDialog(stage);
            if (selected != null) 
            {
                pathField.setText(selected.getAbsolutePath());
                if (nameField.getText().isBlank()) 
                {
                    String fileName = selected.getName();
                    int dot = fileName.lastIndexOf('.');
                    nameField.setText(dot > 0 ? fileName.substring(0, dot) : fileName);
                }
            }
        });

        // ---- Controls & Form Action ----
        Button actionButton = new Button("Add");
        Button clearButton = new Button("Clear");
        Button removeButton = new Button("Remove Selected");

        // Disable remove button when nothing is selected
        removeButton.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());

        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) 
            {
                nameField.setText(selected.getName());
                pathField.setText(selected.getExecutablePath());
                actionButton.setText("Update");
            } 
            else 
            {
                nameField.clear();
                pathField.clear();
                actionButton.setText("Add");
            }
        });

        actionButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            String path = pathField.getText().trim();

            if (name.isBlank() || path.isBlank()) 
            {
                NotificationUtil.showToast(stage, "Name and path are required", "fas-exclamation-circle");
                return;
            }

            // Check exists() instead of isFile() to support macOS .app packages
            File exeFile = new File(path);
            if (!exeFile.exists()) 
            {
                NotificationUtil.showToast(stage, "Executable not found at that path", "fas-exclamation-circle");
                return;
            }

            ExternalApp selected = listView.getSelectionModel().getSelectedItem();

            if (selected != null) 
            {
                // Update existing
                boolean duplicate = apps.stream()
                        .filter(a -> a != selected)
                        .anyMatch(a -> a.getName().equalsIgnoreCase(name));
                if (duplicate)
                {
                    NotificationUtil.showToast(stage, "Duplicate Name : Another app already has that name", "fas-exclamation-circle");
                    return;
                }
                selected.setName(name);
                selected.setExecutablePath(path);
                listView.refresh();
                AppSettings.getInstance().setExternalApps(apps);
                NotificationUtil.showToast(stage, "Application updated successfully", "fas-check-circle");
            } 
            else 
            {
                // Add new
                boolean duplicate = apps.stream()
                        .anyMatch(a -> a.getName().equalsIgnoreCase(name));
                if (duplicate) 
                {
                    NotificationUtil.showToast(stage, "Duplicate Name : An app with that name already exists", "fas-exclamation-circle");
                    return;
                }
                ExternalApp newApp = new ExternalApp(name, path);
                apps.add(newApp);
                AppSettings.getInstance().setExternalApps(apps);
                nameField.clear();
                pathField.clear();
                NotificationUtil.showToast(stage, "Application added successfully.", "fas-check-circle");
            }
        });

        clearButton.setOnAction(e -> listView.getSelectionModel().clearSelection());

        removeButton.setOnAction(e -> {
            ExternalApp selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) 
            {
                apps.remove(selected);
                AppSettings.getInstance().setExternalApps(apps);
                listView.getSelectionModel().clearSelection();
            }
        });

        // ---- Layout ----
        HBox pathRow = new HBox(6, pathField, browseButton);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        pathField.setMaxWidth(Double.MAX_VALUE);

        VBox formBox = new VBox(6,
                new Label("Name"), nameField,
                new Label("Executable Path"), pathRow,
                new HBox(8, actionButton, clearButton, removeButton)
        );
        formBox.setPadding(new Insets(0, 0, 10, 0));

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> stage.close());

        HBox footer = new HBox(10, spacerRegion(), closeButton);

        VBox root = new VBox(10,
                new Label("Configured Applications"),
                listView,
                new Separator(),
                formBox,
                footer
        );
        root.setPadding(new Insets(15));
        root.setFillWidth(true);

        Scene scene = new Scene(root, 500, 600);
        stage.setScene(scene);
        stage.setMinWidth(400);
        stage.setMinHeight(360);
        stage.showAndWait();
    }

    private static Region spacerRegion() 
    {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}