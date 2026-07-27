package com.lensora.lensorastudio.util;

import com.lensora.lensorastudio.model.ExternalApp;
import com.lensora.lensorastudio.services.AppSettings;

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

        ObservableList<ExternalApp> apps = FXCollections.observableArrayList(
                AppSettings.getInstance().getExternalApps());

        ListView<ExternalApp> listView = new ListView<>(apps);
        listView.setPrefSize(400, 250);

        TextField nameField = new TextField();
        nameField.setPromptText("Display name (e.g. Adobe Photoshop)");

        TextField pathField = new TextField();
        pathField.setPromptText("Path to executable");
        pathField.setEditable(false);

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

        Button addButton = new Button("Add");
        addButton.setOnAction(e -> {
            String name = nameField.getText().trim();
            String path = pathField.getText().trim();

            // Check for empty fields
            if (name.isBlank() || path.isBlank()) return;

            // Check if the executable exists and is a file
            File exeFile = new File(path);
            if (!exeFile.exists() || !exeFile.isFile()) return;

            // Check for duplicate display names (case‑insensitive)
            boolean duplicate = apps.stream().anyMatch(a -> a.getName().equalsIgnoreCase(name));
            if (duplicate) return;

            apps.add(new ExternalApp(name, path));
            nameField.clear();
            pathField.clear();
        });

        Button removeButton = new Button("Remove Selected");
        removeButton.setOnAction(e -> {
            ExternalApp selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) apps.remove(selected);
        });

        HBox pathRow = new HBox(6, pathField, browseButton);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        VBox formBox = new VBox(6, new Label("Name"), nameField, new Label("Executable"), pathRow, addButton);
        formBox.setPadding(new Insets(0, 0, 10, 0));

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> {
            AppSettings.getInstance().setExternalApps(apps);
            stage.close();
        });

        HBox footer = new HBox(10, removeButton, spacerRegion(), closeButton);

        VBox root = new VBox(10, new Label("Configured Applications"), listView, new Separator(), formBox, footer);
        root.setPadding(new Insets(15));

        stage.setScene(new Scene(root));
        stage.showAndWait();
    }

    private static Region spacerRegion()
    {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}