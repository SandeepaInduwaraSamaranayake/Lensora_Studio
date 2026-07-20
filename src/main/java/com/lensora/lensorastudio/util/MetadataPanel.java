package com.lensora.lensorastudio.util;

import com.lensora.lensorastudio.model.MediaMetadata;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.snapfx.SnapFX;
import org.snapfx.model.DockNode;

import java.io.File;
import java.util.Map;
import java.util.List;

public final class MetadataPanel 
{

private MetadataPanel() {}

         // ─── Modal version (kept for fallback) ──────────────────────────────
        public static void show(Window owner, MediaMetadata metadata) 
        {
                Stage stage = new Stage();
                stage.initModality(Modality.APPLICATION_MODAL);
                if (owner != null) stage.initOwner(owner);
                stage.setTitle("Metadata - " + new File(metadata.getFilePath()).getName());

                Parent content = buildContent(metadata);
                Scene scene = new Scene(content, 650, 550);
                stage.setScene(scene);
                stage.showAndWait();
        }

        // ─── SnapFX floating version ─────────────────────────────────────────
        public static void showFloating(MediaMetadata metadata, SnapFX snapFX) 
        {
                if (snapFX == null) 
                {
                        show(null, metadata);
                        return;
                }

                Node content = buildContent(metadata);
                String title = "Metadata - " + new File(metadata.getFilePath()).getName();
                DockNode dockNode = new DockNode("metadata-" + System.currentTimeMillis(), content, title);
                dockNode.setCloseable(true);

                // Float the node – SnapFX creates a floating, draggable, non‑modal window
                snapFX.floatNode(dockNode);
        }

        // ─── Shared content builder ──────────────────────────────────────────
        public static Parent buildContent(MediaMetadata metadata) 
        {
                VBox container = new VBox(8);
                container.setPadding(new Insets(10));

                for (Map.Entry<String, Map<String, String>> group : metadata.getGroups().entrySet()) 
                {
                        TableView<Map.Entry<String, String>> table = createTable(group.getValue());
                        TitledPane pane = new TitledPane(group.getKey(), table);
                        pane.setExpanded(true); // expand all groups
                        container.getChildren().add(pane);
                }

                ScrollPane scrollPane = new ScrollPane(container);
                scrollPane.setFitToWidth(true);
                scrollPane.setFitToHeight(false);
                return scrollPane;
        }

        private static TableView<Map.Entry<String, String>> createTable(Map<String, String> values) 
        {
                TableView<Map.Entry<String, String>> table = new TableView<>();

                TableColumn<Map.Entry<String, String>, String> keyCol = new TableColumn<>("Property");
                keyCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getKey()));
                keyCol.setPrefWidth(220);

                TableColumn<Map.Entry<String, String>, String> valueCol = new TableColumn<>("Value");
                valueCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getValue()));
                valueCol.setPrefWidth(380);

                table.getColumns().addAll(List.of(keyCol, valueCol));
                table.getItems().addAll(values.entrySet());
                table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

                int rows = values.size();
                table.setPrefHeight(Math.min(300, 28 + rows * 26));
                table.setMaxHeight(Double.MAX_VALUE);
                return table;
        }
}