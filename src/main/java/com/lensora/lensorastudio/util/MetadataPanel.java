package com.lensora.lensorastudio.util;

import com.lensora.lensorastudio.model.MediaMetadata;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.snapfx.SnapFX;
import org.snapfx.model.DockNode;

import java.io.File;
import java.util.Map;

public final class MetadataPanel
{
    private MetadataPanel() {}

    // ─────────────────────────────────────────────────────────────────────
    // Modal version  (kept for fallback)
    // ─────────────────────────────────────────────────────────────────────

    public static void show(Window owner, MediaMetadata metadata)
    {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        if (owner != null) stage.initOwner(owner);
        stage.setTitle("Metadata - " + new File(metadata.getFilePath()).getName());

        Scene scene = new Scene(buildContent(metadata), 650, 550);
        stage.setScene(scene);
        stage.showAndWait();
    }

    // ─────────────────────────────────────────────────────────────────────
    // SnapFX floating version
    // ─────────────────────────────────────────────────────────────────────

    public static void showFloating(MediaMetadata metadata, SnapFX snapFX)
    {
        if (snapFX == null)
        {
            show(null, metadata);
            return;
        }

        DockNode dockNode = new DockNode(
                "metadata-" + System.currentTimeMillis(),
                buildContent(metadata),
                "Metadata - " + new File(metadata.getFilePath()).getName());

        dockNode.setCloseable(true);
        snapFX.floatNode(dockNode);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Shared UI
    // ─────────────────────────────────────────────────────────────────────

    public static Parent buildContent(MediaMetadata metadata)
    {
        VBox container = new VBox(8);
        container.setPadding(new Insets(10));

        for (Map.Entry<String, Map<String, String>> group : metadata.getGroups().entrySet())
        {
            TitledPane pane = new TitledPane( group.getKey(), createPropertyGrid(group.getValue()));
            pane.setExpanded(true);
            container.getChildren().add(pane);
        }

        ScrollPane scrollPane = new ScrollPane(container);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);

        return scrollPane;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Property Grid
    // ─────────────────────────────────────────────────────────────────────

    private static GridPane createPropertyGrid(Map<String, String> values)
    {
        GridPane grid = new GridPane();

        grid.setHgap(12);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));

        ColumnConstraints propertyColumn = new ColumnConstraints();
        propertyColumn.setPrefWidth(220);
        propertyColumn.setMinWidth(180);

        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(propertyColumn, valueColumn);

        int row = 0;
        int index = 0;

        for (Map.Entry<String, String> entry : values.entrySet())
        {
                Label property = new Label(entry.getKey());
                property.setStyle("-fx-font-weight: bold;");

                Label value =  new Label(entry.getValue());
                
                grid.add(property, 0, row);
                grid.add(value, 1, row);

                if (index != values.size() - 1)
                {
                        row++;
                        Separator separator = new Separator();
                        GridPane.setColumnSpan(separator, 2);
                        grid.add(separator, 0, row);
                }

                row++;
                index++;
        }

        return grid;
    }
}