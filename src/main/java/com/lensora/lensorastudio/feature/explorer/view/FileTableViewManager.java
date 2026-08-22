package com.lensora.lensorastudio.feature.explorer.view;

import com.lensora.lensorastudio.util.ClipboardFormats;
import com.lensora.lensorastudio.util.FileSizeFormatter;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/** Owns the details TableView: columns, selection, and shared context menu attachment. */
public class FileTableViewManager
{
    private final TableView<File> fileTable;
    private Consumer<File> doubleClickHandler;

    public FileTableViewManager(TableView<File> fileTable,
                                TableColumn<File, String> colName, TableColumn<File, String> colType,
                                TableColumn<File, String> colSize, TableColumn<File, String> colDimensions,
                                TableColumn<File, String> colModified,
                                FileDimensionService dimensionService)
    {
        this.fileTable = fileTable;
        this.fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        setupColumns(colName, colType, colSize, colDimensions, colModified, dimensionService);
        setupKeyboardAndMouse();
        setupDragSource();
    }

    private void setupColumns(TableColumn<File, String> colName, TableColumn<File, String> colType,
                                TableColumn<File, String> colSize, TableColumn<File, String> colDimensions,
                                TableColumn<File, String> colModified, FileDimensionService dimensionService)
    {
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colType.setCellValueFactory(c -> {
            String name = c.getValue().getName();
            int idx = name.lastIndexOf('.');
            return new SimpleStringProperty(idx > 0 ? name.substring(idx + 1) : "");
        });
        colSize.setCellValueFactory(c -> {
            long size = c.getValue().length();
            return new SimpleStringProperty(size > 0 ? FileSizeFormatter.formatFileSize(size) : "");
        });
        colDimensions.setCellValueFactory(c -> dimensionService.propertyFor(c.getValue()));
        colModified.setCellValueFactory(c -> new SimpleStringProperty(
                LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getValue().lastModified()), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
    }

    private void setupKeyboardAndMouse()
    {
        fileTable.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && doubleClickHandler != null)
            {
                doubleClickHandler.accept(fileTable.getSelectionModel().getSelectedItem());
            }
        });

        fileTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER)
            {
                File selected = fileTable.getSelectionModel().getSelectedItem();
                if (selected != null && doubleClickHandler != null)
                {
                    doubleClickHandler.accept(selected);
                    event.consume();
                }
            }
        });
    }

    private void setupDragSource()
    {
        fileTable.setRowFactory(tv -> {
            TableRow<File> row = new TableRow<>();
            row.setOnDragDetected(e -> {
                if (row.isEmpty()) return;
                List<File> selected = fileTable.getSelectionModel().getSelectedItems();
                if (selected.isEmpty()) return;
                Dragboard db = row.startDragAndDrop(TransferMode.COPY_OR_MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putFiles(selected);
                content.put(ClipboardFormats.INTERNAL_DRAG, true);
                db.setContent(content);
                e.consume();
            });
            return row;
        });
    }

    public void setItems(ObservableList<File> files) { fileTable.setItems(files); }
    public File getSelectedFile() { return fileTable.getSelectionModel().getSelectedItem(); }
    public List<File> getSelectedFiles() { return fileTable.getSelectionModel().getSelectedItems(); }
    public boolean isFocused() { return fileTable.isFocused(); }
    public TableView<File> getNode() { return fileTable; }

    public void attachSharedContextMenu(ContextMenu menu)
    {
        fileTable.setContextMenu(menu);
    }

    public void setOnDoubleClick(Consumer<File> handler)
    {
        this.doubleClickHandler = handler;
    }
}