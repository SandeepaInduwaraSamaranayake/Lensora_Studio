package com.lensora.lensorastudio.feature.explorer.control;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import org.kordamp.ikonli.javafx.FontIcon;

import com.lensora.lensorastudio.util.ClipboardFormats;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/**
 * Owns the folder TreeView itself: cell rendering, drag-and-drop onto
 * tree cells, building/refreshing the tree structure from disk, and
 * locating/selecting/expanding TreeItems by File.
 */
public class FolderTreeViewManager
{
    private final TreeView<File> folderTree;
    private File projectRoot;

    private Consumer<File> onTreeSelectionChanged;
    /** (files, targetFolder, isMove) - isMove is true for internal drags, false for external OS drag-ins. */
    private FolderTreeManager.TriConsumer<List<File>, File, Boolean> onFilesDropped = (files, folder, move) -> {};

    public FolderTreeViewManager(TreeView<File> folderTree)
    {
        this.folderTree = folderTree;

        setupTreeCellFactory();
        setupTreeSelectionListener();
    }

    public void setOnTreeSelectionChanged(Consumer<File> callback) { this.onTreeSelectionChanged = callback; }
    public void setOnFilesDropped(FolderTreeManager.TriConsumer<List<File>, File, Boolean> callback) { this.onFilesDropped = callback; }

    public TreeView<File> getFolderTree() { return folderTree; }
    public File getProjectRoot() { return projectRoot; }

    // ─── Loading ────────────────────────────────────────────────────────────

    /** Builds the tree from the given project path. Returns the loaded root File, or null if the path is invalid. */
    public File loadProjectPath(String path)
    {
        if (path == null || path.isEmpty())
        {
            folderTree.setRoot(null);
            projectRoot = null;
            return null;
        }
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory())
        {
            folderTree.setRoot(null);
            projectRoot = null;
            return null;
        }

        this.projectRoot = folder;

        TreeItem<File> rootItem = new TreeItem<>(folder);
        rootItem.setExpanded(true);
        addChildren(rootItem);
        folderTree.setRoot(rootItem);
        folderTree.setShowRoot(false);

        return folder;
    }

    private void addChildren(TreeItem<File> parent)
    {
        File dir = parent.getValue();
        if (!dir.isDirectory()) return;
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) return;
        for (File child : children)
        {
            TreeItem<File> item = new TreeItem<>(child);
            parent.getChildren().add(item);
            File[] grandchildren = child.listFiles(File::isDirectory);
            if (grandchildren != null && grandchildren.length > 0)
            {
                addChildren(item);
            }
        }
    }

    public void refreshFolder(File folder) 
    {
        System.out.println("refreshFolder(File folder)  called on folder : " + folder.getAbsolutePath() );
        TreeItem<File> root = folderTree.getRoot();
        if (root == null || folder == null) return;

        TreeItem<File> item = findTreeItem(root, folder);
        if (item != null) 
        {
            File previouslySelected = getSelectedFolder(); // capture before rebuild

            item.getChildren().clear();
            addChildren(item);
            item.setExpanded(true);

            if (previouslySelected != null)
            {
                selectFolderInTree(previouslySelected); // re-locate & reselect if it still exists
            }
        }
    }

    // update both folder tree and files
    public void refreshSelected(Runnable onRefreshRequested)
    {
        TreeItem<File> selected = folderTree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue().isDirectory())
        {
            selected.getChildren().clear();
            addChildren(selected);
            selected.setExpanded(true);
        }
        if (onRefreshRequested != null) onRefreshRequested.run();
    }

    // ─── Selection / lookup ─────────────────────────────────────────────────

    public File getSelectedFolder()
    {
        TreeItem<File> selected = folderTree.getSelectionModel().getSelectedItem();
        return selected != null ? selected.getValue() : null;
    }

    public void selectFolderInTree(File folder)
    {
        TreeItem<File> root = folderTree.getRoot();
        if (root == null) return;
        TreeItem<File> target = findTreeItem(root, folder);
        if (target != null)
        {
            folderTree.getSelectionModel().select(target);
            target.setExpanded(true);
        }
    }

    private TreeItem<File> findTreeItem(TreeItem<File> node, File target)
    {
        if (node.getValue().equals(target)) return node;
        for (TreeItem<File> child : node.getChildren())
        {
            TreeItem<File> result = findTreeItem(child, target);
            if (result != null) return result;
        }
        return null;
    }

    /** Expands every TreeItem from the root down to (and including) the folder's ancestors. */
    public void expandAncestorsInTree(File folder)
    {
        TreeItem<File> root = folderTree.getRoot();
        if (root == null) return;

        TreeItem<File> item = findTreeItem(root, folder);
        TreeItem<File> current = item != null ? item : root;

        // Expand from the found node up to the root so the whole chain is visible.
        TreeItem<File> walker = current;
        while (walker != null)
        {
            walker.setExpanded(true);
            walker = walker.getParent();
        }
    }

    // ─── Rebuild after create/delete ────────────────────────────────────────

    /** Rebuilds the affected TreeItem's children and selects the new folder (does not navigate). */
    public void refreshTreeAfterFolderCreation(File parentFolder, File newFolder)
    {
        TreeItem<File> root = folderTree.getRoot();
        if (root == null) return;

        TreeItem<File> parentItem = findTreeItem(root, parentFolder);
        if (parentItem != null)
        {
            parentItem.getChildren().clear();
            addChildren(parentItem);
            parentItem.setExpanded(true);

            TreeItem<File> newItem = findTreeItem(parentItem, newFolder);
            if (newItem != null)
            {
                folderTree.getSelectionModel().select(newItem);
            }
        }
    }

    /** Rebuilds the parent's children in the tree after a folder beneath it was deleted. */
    public void refreshTreeAfterFolderDeletion(File parentFolder)
    {
        if (parentFolder == null) return;

        TreeItem<File> root = folderTree.getRoot();
        if (root == null) return;

        TreeItem<File> parentItem = findTreeItem(root, parentFolder);
        if (parentItem != null)
        {
            parentItem.getChildren().clear();
            addChildren(parentItem);
            parentItem.setExpanded(true);
            folderTree.getSelectionModel().select(parentItem);
        }
        else if (parentFolder.equals(projectRoot))
        {
            // Deleted a top-level folder directly under the project root -
            // rebuild the whole tree root since there's no parent TreeItem.
            loadProjectPath(projectRoot.getAbsolutePath());
        }
    }

    // ─── Tree cell factory + selection ─────────────────────────────────────

    private void setupTreeCellFactory()
    {
        folderTree.setCellFactory(tv -> {
            TreeCell<File> cell = new TreeCell<>() {
                @Override
                protected void updateItem(File item, boolean empty)
                {
                    super.updateItem(item, empty);
                    if (empty || item == null)
                    {
                        setText(null);
                        setGraphic(null);
                    }
                    else
                    {
                        setText(item.getName());
                        FontIcon folderIcon = new FontIcon("fas-folder");
                        folderIcon.getStyleClass().add("icon-size-14");
                        setGraphic(folderIcon);
                    }
                }
            };

            setupCellDragAndDrop(cell);
            return cell;
        });
    }

    private void setupCellDragAndDrop(TreeCell<File> cell)
    {
        cell.setOnDragOver(event -> {
            File folder = cell.getItem();
            if (folder != null && folder.isDirectory() && event.getDragboard().hasFiles())
            {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                cell.setStyle("-fx-background-color: -color-accent-muted;");
            }
            event.consume();
        });

        cell.setOnDragExited(event -> cell.setStyle(""));

        cell.setOnDragDropped(event -> {
            File targetFolder = cell.getItem();
            Dragboard db = event.getDragboard();
            boolean success = false;

            if (targetFolder != null && targetFolder.isDirectory() && db.hasFiles() && onFilesDropped != null)
            {
                boolean isInternal = Boolean.TRUE.equals(
                        db.getContent(ClipboardFormats.INTERNAL_DRAG));
                onFilesDropped.accept(db.getFiles(), targetFolder, isInternal);
                success = true;
            }

            cell.setStyle("");
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void setupTreeSelectionListener()
    {
        folderTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.getValue() == null || !newVal.getValue().isDirectory())
            {
                return;
            }

            File folder = newVal.getValue();

            if (onTreeSelectionChanged != null)
            {
                onTreeSelectionChanged.accept(folder);
            }
        });
    }

    public boolean isFocused()
    {
        return folderTree.isFocused();
    }
}