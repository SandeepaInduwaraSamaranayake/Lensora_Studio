package com.lensora.lensorastudio.feature.explorer.view;

import com.lensora.lensorastudio.ui.util.FileIconUtil;
import com.lensora.lensorastudio.util.ClipboardFormats;

import javafx.collections.ObservableList;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.*;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns the simple ListView representation of the file listing. */
public class FileListViewManager
{
    private final ListView<File> fileListView;
    private ContextMenu sharedMenu;
    private Supplier<List<File>> selectionSupplier;
    private Consumer<File> doubleClickHandler;

    public FileListViewManager(ListView<File> fileListView)
    {
        this.fileListView = fileListView;
        this.fileListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    public void setItems(ObservableList<File> files)
    {
        fileListView.setItems(files);
        fileListView.setCellFactory(lv -> buildCell());
    }

    private ListCell<File> buildCell()
    {
        ListCell<File> cell = new ListCell<>() {
            @Override
            protected void updateItem(File file, boolean empty)
            {
                super.updateItem(file, empty);
                if (empty || file == null) { setText(null); setGraphic(null); }
                else { setText(file.getName()); setGraphic(FileIconUtil.getFileIcon(file, ".icon-size-10")); }
            }
        };

        // Right-click selects the row under the cursor before the shared
        // context menu opens - ListView doesn't do this automatically the
        // way TableView does.
        cell.setOnContextMenuRequested(event -> {
            if (cell.getItem() != null && !fileListView.getSelectionModel().getSelectedItems().contains(cell.getItem()))
            {
                fileListView.getSelectionModel().select(cell.getItem());
            }
        });
        if (sharedMenu != null) cell.setContextMenu(sharedMenu);

        cell.setOnDragDetected(event -> {
            if (cell.getItem() == null) return;
            List<File> filesToDrag = fileListView.getSelectionModel().getSelectedItems().isEmpty()
                    ? List.of(cell.getItem()) 
                    : List.copyOf(fileListView.getSelectionModel().getSelectedItems());

            Dragboard db = cell.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putFiles(filesToDrag);
            content.put(ClipboardFormats.INTERNAL_DRAG, true);
            db.setContent(content);
            event.consume();
        });

        cell.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && doubleClickHandler != null)
            {
                doubleClickHandler.accept(cell.getItem());
            }
        });

        return cell;
    }

    
    public void setOnDoubleClick(Consumer<File> handler) { this.doubleClickHandler = handler; }

    public void attachSharedContextMenu(ContextMenu menu)
    {
        this.sharedMenu = menu;
        // Applied per-cell in buildCell() since ListView doesn't have a
        // single-node context menu the way TableView does for full-width rows.
    }

    public List<File> getSelectedFiles() { return fileListView.getSelectionModel().getSelectedItems(); }
    public boolean isFocused() { return fileListView.isFocused(); }
    public ListView<File> getNode() { return fileListView; }
}