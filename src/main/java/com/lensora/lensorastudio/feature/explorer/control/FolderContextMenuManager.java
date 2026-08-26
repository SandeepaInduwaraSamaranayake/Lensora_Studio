package com.lensora.lensorastudio.feature.explorer.control;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lensora.lensorastudio.core.io.FileSystemOperations;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;
import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Owns the folder-tree context menu (new folder / copy / paste / delete /
 * open in explorer / copy path) and the file-system operations behind it.
 */
public class FolderContextMenuManager
{
    private static final Logger logger = LoggerFactory.getLogger(FolderContextMenuManager.class);

    private final TreeView<File> folderTree;
    private final FolderTreeViewManager treeViewManager;
    private final FolderNavigationManager navigationManager;
    private final FileSystemOperations fsOps;

    /** Paste is delegated out - FileOperationsManager owns the actual copy-with-progress logic. */
    private Runnable pasteRequested = () -> {};

    public FolderContextMenuManager(TreeView<File> folderTree, FolderTreeViewManager treeViewManager,
                                    FolderNavigationManager navigationManager, FileSystemOperations fsOps)
    {
        this.folderTree = folderTree;
        this.treeViewManager = treeViewManager;
        this.navigationManager = navigationManager;
        this.fsOps = fsOps;

        setupContextMenu();
    }

    public void setOnPasteRequested(Runnable callback) { this.pasteRequested = callback; }

    // ─── Context menu (copy/paste/open/copy-path) ──────────────────────────

    private void setupContextMenu()
    {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem newFolderItem = new MenuItem("New Folder");
        MenuItem copyItem = new MenuItem("Copy");
        MenuItem pasteItem = new MenuItem("Paste");
        MenuItem deleteFolderItem = new MenuItem("Delete Folder");
        MenuItem openItem = new MenuItem("Open in Explorer");
        MenuItem copyPathItem = new MenuItem("Copy Directory Path");

        newFolderItem.setOnAction(e -> createNewFolder());
        copyItem.setOnAction(e -> copySelectedFolder());
        pasteItem.setOnAction(e -> pasteRequested.run());
        deleteFolderItem.setOnAction(e -> deleteSelectedFolder());
        openItem.setOnAction(e -> openSelectedFolderInExplorer());
        copyPathItem.setOnAction(e -> copySelectedFolderPath());

        contextMenu.getItems().addAll(
                                        newFolderItem, copyItem,
                                        new SeparatorMenuItem(),
                                        pasteItem,
                                        deleteFolderItem,
                                        openItem,
                                        copyPathItem
                                    );
        folderTree.setContextMenu(contextMenu);
    }

    public void copySelectedFolder()
    {
        File folder = treeViewManager.getSelectedFolder();
        if (folder == null || !folder.isDirectory())
        {
            NotificationUtil.showToast(folderTree, "Please select a folder", "fas-exclamation-circle");
            return;
        }
        List<File> folderList = List.of(folder);
        ClipboardContent content = new ClipboardContent();
        content.put(DataFormat.FILES, folderList);
        Clipboard.getSystemClipboard().setContent(content);
        NotificationUtil.showToast(folderTree, "Folder '" + folder.getName() + "' copied");
    }

    private void openSelectedFolderInExplorer()
    {
        File folder = treeViewManager.getSelectedFolder();
        if (folder == null || !folder.isDirectory()) return;
        // Runs in the background
        CompletableFuture.runAsync(() -> {
            try
            {
                if (Desktop.isDesktopSupported())
                {
                    Desktop.getDesktop().open(folder);
                }
                else
                {
                    Platform.runLater(() ->
                        NotificationUtil.showToast(folderTree, "Cannot open folder on this system", "fas-exclamation-circle")
                    );
                }
            }
            catch (IOException e)
            {
                Platform.runLater(() ->
                    ErrorHandler.show(null, "Could not open folder", e)
                );
            }
        });
    }

    private void copySelectedFolderPath()
    {
        File folder = treeViewManager.getSelectedFolder();
        if (folder == null) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(folder.getAbsolutePath());
        Clipboard.getSystemClipboard().setContent(content);
        NotificationUtil.showToast(folderTree, "Path copied to clipboard");
    }

    private void createNewFolder()
    {
        File parentFolder = treeViewManager.getSelectedFolder();
        if (parentFolder == null)
        {
            parentFolder = treeViewManager.getProjectRoot(); // right-click on empty area / nothing selected -> create at root
        }
        if (parentFolder == null || !parentFolder.isDirectory())
        {
            NotificationUtil.showToast(folderTree, "Please select a valid location", "fas-exclamation-circle");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("New Folder");
        dialog.setTitle("New Folder");
        dialog.setHeaderText(null);
        dialog.setContentText("Folder name:");

        final File finalParent = parentFolder;
        dialog.showAndWait().ifPresent(name -> {
            if (name == null || name.isBlank()) return;

            String sanitized = fsOps.sanitizeFolderName(name.trim());
            if (sanitized.isEmpty())
            {
                NotificationUtil.showToast(folderTree, "Invalid folder name", "fas-exclamation-circle");
                return;
            }

            File newFolder = new File(finalParent, sanitized);
            if (newFolder.exists())
            {
                NotificationUtil.showToast(folderTree, "A folder with that name already exists", "fas-exclamation-circle");
                return;
            }

            try
            {
                // Marks the change (suppresses the watch-service echo) and
                // triggers the SAME unified refresh path file operations use
                newFolder = fsOps.createDirectory(finalParent, sanitized);
                navigationManager.navigateTo(newFolder);
                NotificationUtil.showToast(folderTree, "Folder created");
            }
            catch (IOException ex)
            {
                ErrorHandler.show(null, "Failed to create folder", ex);
            }
        });
    }

    // ============================== Delete Folder ==============================
    private void deleteSelectedFolder()
    {
        File folder = treeViewManager.getSelectedFolder();
        File projectRoot = treeViewManager.getProjectRoot();

        if (folder == null)
        {
            NotificationUtil.showToast(folderTree, "Please select a folder move to trash", "fas-exclamation-circle");
            return;
        }
        if (projectRoot != null && folder.equals(projectRoot))
        {
            NotificationUtil.showToast(folderTree, "Cannot move the project root folder to trash", "fas-exclamation-circle");
            return;
        }

        long fileCount = fsOps.countFilesRecursive(folder);
        String message = fileCount > 0
                ? "Move \"" + folder.getName() + "\" and all " + fileCount + " file(s) inside it to the Trash?"
                : "Move \"" + folder.getName() + "\" to the Trash?";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Move to Trash");
        confirm.setHeaderText(null);
        confirm.setContentText(message);
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;

            boolean moved = false;
            if (Desktop.isDesktopSupported())
            {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.MOVE_TO_TRASH))
                {
                    try
                    {
                        if (!folder.exists())
                        {
                            NotificationUtil.showToast(folderTree, "Folder no longer exists", "fas-exclamation-circle");
                            return;
                        }
                        moved = folder.exists() && fsOps.moveToTrash(folder);
                    }
                    catch (Exception ex)
                    {
                        logger.warn("Move to Trash failed", ex);
                        moved = false;
                        NotificationUtil.showToast(folderTree, "Cannot move folder to trash", "fas-exclamation-circle");
                    }
                }
            }
            if (!moved)
            {
                // Fallback: ask user if they want to permanently delete instead
                Alert fallbackConfirm = new Alert(Alert.AlertType.CONFIRMATION);
                fallbackConfirm.setTitle("Move to Trash Failed");
                fallbackConfirm.setHeaderText(null);
                fallbackConfirm.setContentText(
                    "Moving to Trash is not supported on this system.\n" +
                    "The folder will be permanently deleted and cannot be recovered."+
                    "Do you want to continue?"
                );
                fallbackConfirm.showAndWait().ifPresent(res -> {
                    if (res == ButtonType.OK)
                    {
                        try
                        {
                            fsOps.deleteRecursive(folder);
                            navigateAwayIfCurrentFolderDeleted(folder, projectRoot);
                            NotificationUtil.showToast(folderTree, "Folder permanently deleted");
                        }
                        catch (IOException ex)
                        {
                            ErrorHandler.show(null, "Failed to delete folder", ex);
                        }
                    }
                });
                return;
            }

            navigateAwayIfCurrentFolderDeleted(folder, projectRoot);
            NotificationUtil.showToast(folderTree, "Folder moved to Trash");
        });
    }

    private void navigateAwayIfCurrentFolderDeleted(File deletedFolder, File projectRoot)
    {
        if (deletedFolder.equals(navigationManager.getCurrentFolder()))
        {
            File parent = deletedFolder.getParentFile();
            File fallback = (parent != null && parent.exists()) ? parent : projectRoot;
            if (fallback != null) navigationManager.navigateTo(fallback);
        }
    }
}