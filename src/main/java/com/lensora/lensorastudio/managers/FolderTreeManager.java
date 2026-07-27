package com.lensora.lensorastudio.managers;

import com.lensora.lensorastudio.util.Dialogs;
import com.lensora.lensorastudio.util.ErrorHandler;

import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Consumer;

/**
 * Owns the folder TreeView: tree population, navigation history
 * (back/forward), the breadcrumb bar, and the folder-tree context menu
 * (copy/paste/open/copy-path).
 */
public class FolderTreeManager
{
    private static final Logger logger = LoggerFactory.getLogger(FolderTreeManager.class);

    private final TreeView<File> folderTree;
    private final HBox breadcrumbContainer;
    private final Button btnBack, btnForward;
    private final Label lblFolderHeader;

    private final Stack<File> backStack = new Stack<>();
    private final Stack<File> forwardStack = new Stack<>();
    private File currentFolder;
    private File projectRoot;
    private boolean isNavigatingHistory = false;

    private Consumer<File> onFolderSelected;
    private Runnable onRefreshRequested;
    private Consumer<String> onPathChanged;
    /** Paste is delegated out — FileOperationsManager owns the actual copy-with-progress logic. */
    private Runnable pasteRequested = () -> {};
    /** (files, targetFolder, isMove) — isMove is true for internal drags, false for external OS drag-ins. */
    private TriConsumer<java.util.List<File>, File, Boolean> onFilesDropped = (files, folder, move) -> {};

    public FolderTreeManager(TreeView<File> folderTree, HBox breadcrumbContainer,
                            Button btnBack, Button btnForward, Label lblFolderHeader)
    {
        this.folderTree = folderTree;
        this.breadcrumbContainer = breadcrumbContainer;
        this.btnBack = btnBack;
        this.btnForward = btnForward;
        this.lblFolderHeader = lblFolderHeader;

        setupTreeCellFactory();
        setupTreeSelectionListener();
        setupContextMenu();
        setupNavigationButtons();
    }

    /** Small local functional interface — java.util has no 3-arg Consumer. */
    @FunctionalInterface
    public interface TriConsumer<A, B, C>
    {
        void accept(A a, B b, C c);
    }

    public void setOnFolderSelected(Consumer<File> callback) { this.onFolderSelected = callback; }
    public void setOnPathChanged(Consumer<String> callback) { this.onPathChanged = callback; }
    public void setOnRefreshRequested(Runnable callback) { this.onRefreshRequested = callback; }
    public void setOnFilesDropped(TriConsumer<java.util.List<File>, File, Boolean> callback) { this.onFilesDropped = callback; }
    public void setOnPasteRequested(Runnable callback) { this.pasteRequested = callback; }

    public File getCurrentFolder() { return currentFolder; }
    public File getProjectRoot() { return projectRoot; }

    // ─── Loading ────────────────────────────────────────────────────────────

    public void loadProjectPath(String path)
    {
        if (path == null || path.isEmpty())
        {
            folderTree.setRoot(null);
            projectRoot = null;
            return;
        }
        File folder = new File(path);
        if (!folder.exists() || !folder.isDirectory())
        {
            folderTree.setRoot(null);
            projectRoot = null;
            return;
        }

        this.projectRoot = folder;
        backStack.clear();
        forwardStack.clear();

        TreeItem<File> rootItem = new TreeItem<>(folder);
        rootItem.setExpanded(true);
        addChildren(rootItem);
        folderTree.setRoot(rootItem);
        folderTree.setShowRoot(false);

        navigateTo(folder);
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

    // update both folder tree and files
    public void refreshSelected()
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

    // ─── Navigation ─────────────────────────────────────────────────────────

    public void navigateTo(File folder)
    {
        if (folder == null) return;
        if (!isNavigatingHistory && currentFolder != null && !currentFolder.equals(folder))
        {
            backStack.push(currentFolder);
            forwardStack.clear();
        }
        currentFolder = folder;
        updateBreadcrumb(folder);
        updateButtonStates();
        //lblFolderHeader.setText("Folders  [" + folder.getAbsolutePath() + "]");

        if (!isNavigatingHistory)
        {
            selectFolderInTree(folder);
        }

        if (onFolderSelected != null)
        {
            onFolderSelected.accept(folder);
        }

        if (onPathChanged != null) 
        {
            onPathChanged.accept(folder.getAbsolutePath());
        }
    }

    public void goBack()
    {
        if (!backStack.isEmpty())
        {
            isNavigatingHistory = true;
            forwardStack.push(currentFolder);
            navigateTo(backStack.pop());
            isNavigatingHistory = false;
            selectFolderInTree(currentFolder);
        }
    }

    public void goForward()
    {
        if (!forwardStack.isEmpty())
        {
            isNavigatingHistory = true;
            backStack.push(currentFolder);
            navigateTo(forwardStack.pop());
            isNavigatingHistory = false;
            selectFolderInTree(currentFolder);
        }
    }

    private void setupNavigationButtons()
    {
        btnBack.setOnAction(e -> goBack());
        btnForward.setOnAction(e -> goForward());
        updateButtonStates();
    }

    private void updateButtonStates()
    {
        btnBack.setDisable(backStack.isEmpty());
        btnForward.setDisable(forwardStack.isEmpty());
    }

    private void selectFolderInTree(File folder)
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

    // ─── Breadcrumb ─────────────────────────────────────────────────────────

    private void updateBreadcrumb(File folder)
    {
        breadcrumbContainer.getChildren().clear();
        if (folder == null) return;

        if (projectRoot == null)
        {
            Button btn = new Button(folder.getAbsolutePath());
            btn.setStyle("-fx-background-color: transparent;");
            breadcrumbContainer.getChildren().add(btn);
            return;
        }

        List<File> segmentFiles = new ArrayList<>();
        List<String> segmentNames = new ArrayList<>();
        segmentFiles.add(projectRoot);
        segmentNames.add(projectRoot.getName());

        if (!folder.equals(projectRoot))
        {
            Path relative = projectRoot.toPath().relativize(folder.toPath());
            for (int i = 0; i < relative.getNameCount(); i++)
            {
                Path fullPath = projectRoot.toPath().resolve(relative.subpath(0, i + 1));
                segmentFiles.add(fullPath.toFile());
                segmentNames.add(relative.getName(i).toString());
            }
        }

        for (int i = 0; i < segmentFiles.size(); i++)
        {
            if (i > 0)
            {
                Label sep = new Label(">");
                breadcrumbContainer.getChildren().add(sep);
            }
            Button btn = new Button(segmentNames.get(i));
            btn.setStyle("-fx-background-color: transparent;");
            final File target = segmentFiles.get(i);
            btn.setOnAction(e -> navigateTo(target));
            breadcrumbContainer.getChildren().add(btn);
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
                        folderIcon.setIconSize(16);
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
                        db.getContent(com.lensora.lensorastudio.util.ClipboardFormats.INTERNAL_DRAG));
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
            if (newVal != null && newVal.getValue() != null && newVal.getValue().isDirectory())
            {
                navigateTo(newVal.getValue());
            }
        });
    }

    // ─── Context menu (copy/paste/open/copy-path) ──────────────────────────

    private void setupContextMenu()
    {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem newFolderItem = new MenuItem("New Folder");
        MenuItem copyItem = new MenuItem("Copy");
        MenuItem pasteItem = new MenuItem("Paste");
        MenuItem openItem = new MenuItem("Open in Explorer");
        MenuItem copyPathItem = new MenuItem("Copy Directory Path");

        newFolderItem.setOnAction(e -> createNewFolder());
        copyItem.setOnAction(e -> copySelectedFolder());
        pasteItem.setOnAction(e -> pasteRequested.run());
        openItem.setOnAction(e -> openSelectedFolderInExplorer());
        copyPathItem.setOnAction(e -> copySelectedFolderPath());

        contextMenu.getItems().addAll(newFolderItem, copyItem, new SeparatorMenuItem(), pasteItem, openItem, copyPathItem);
        folderTree.setContextMenu(contextMenu);
    }


    public File getSelectedFolder()
    {
        TreeItem<File> selected = folderTree.getSelectionModel().getSelectedItem();
        return selected != null ? selected.getValue() : null;
    }

    public void copySelectedFolder()
    {
        File folder = getSelectedFolder();
        if (folder == null || !folder.isDirectory())
        {
            Dialogs.showInfo(null, "Copy", null, "Please select a folder.");
            return;
        }
        List<File> folderList = List.of(folder);
        ClipboardContent content = new ClipboardContent();
        content.put(DataFormat.FILES, folderList);
        Clipboard.getSystemClipboard().setContent(content);
        Dialogs.showInfo(null, "Copy", null, "Folder '" + folder.getName() + "' copied.");
    }

    private void openSelectedFolderInExplorer()
    {
        File folder = getSelectedFolder();
        if (folder == null || !folder.isDirectory()) return;
        try
        {
            if (Desktop.isDesktopSupported())
            {
                Desktop.getDesktop().open(folder);
            }
            else 
            {
                Dialogs.showInfo(null, "Not Supported", null, "Cannot open folder on this system.");
            }
        }
        catch (IOException e)
        {
            ErrorHandler.show(null, "Could not open folder", e);
        }
    }

    private void copySelectedFolderPath()
    {
        File folder = getSelectedFolder();
        if (folder == null) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(folder.getAbsolutePath());
        Clipboard.getSystemClipboard().setContent(content);
        Dialogs.showInfo(null, "Copy Path", null, "Path copied to clipboard.");
    }

    private void createNewFolder()
    {
        File parentFolder = getSelectedFolder();
        if (parentFolder == null)
        {
            parentFolder = projectRoot; // right-click on empty area / nothing selected -> create at root
        }
        if (parentFolder == null || !parentFolder.isDirectory())
        {
            Dialogs.showInfo(null, "New Folder", null, "Please select a valid location.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("New Folder");
        dialog.setTitle("New Folder");
        dialog.setHeaderText(null);
        dialog.setContentText("Folder name:");

        final File finalParent = parentFolder;
        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.isBlank()) return;

            String sanitized = sanitizeFolderName(name.trim());
            if (sanitized.isEmpty())
            {
                Dialogs.showInfo(null, "New Folder", null, "Invalid folder name.");
                return;
            }

            File newFolder = new File(finalParent, sanitized);
            if (newFolder.exists())
            {
                Dialogs.showInfo(null, "New Folder", null, "A folder with that name already exists.");
                return;
            }

            try
            {
                Files.createDirectory(newFolder.toPath());
                refreshTreeAfterFolderCreation(finalParent, newFolder);
            }
            catch (java.io.IOException ex)
            {
                ErrorHandler.show(null, "Failed to create folder", ex);
            }
        });
    }

    /** Strips characters that are invalid in folder names on Windows/macOS/Linux. */
    private String sanitizeFolderName(String name)
    {
        return name.replaceAll("[\\\\/:*?\"<>|]", "").trim();
    }

    /** Rebuilds the affected TreeItem's children and selects/expands to the new folder. */
    private void refreshTreeAfterFolderCreation(File parentFolder, File newFolder)
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

        navigateTo(newFolder);
    }



    public boolean isFocused()
    {
        return folderTree.isFocused();
    }
}