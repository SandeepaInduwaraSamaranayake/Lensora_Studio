package com.lensora.lensorastudio.feature.explorer.control;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lensora.lensorastudio.core.io.InstrumentedFileIO;
import com.lensora.lensorastudio.core.threading.BackgroundExecutor;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;
import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.List;

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
    private final InstrumentedFileIO fileIO;

    /** Paste is delegated out - FileOperationsManager owns the actual copy-with-progress logic. */
    private Runnable pasteRequested = () -> {};

    public FolderContextMenuManager(TreeView<File> folderTree, FolderTreeViewManager treeViewManager,
                                    FolderNavigationManager navigationManager, InstrumentedFileIO fileIO)
    {
        this.folderTree = folderTree;
        this.treeViewManager = treeViewManager;
        this.navigationManager = navigationManager;
        this.fileIO = fileIO;

        setupContextMenu();
        setupKeyboardShortcuts();
    }

    public void setOnPasteRequested(Runnable callback) { this.pasteRequested = callback; }

    // ─── Context menu (copy/paste/open/copy-path) ──────────────────────────

    private void setupContextMenu()
    {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem newFolderItem = new MenuItem("New Folder");
        MenuItem copyItem = new MenuItem("Copy");
        MenuItem pasteItem = new MenuItem("Paste");
        MenuItem deleteFolderItem = new MenuItem("Delete Folder\tDelete");
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

    private void setupKeyboardShortcuts()
    {
        folderTree.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            // Only act if the tree is focused
            if (!folderTree.isFocused()) return;

            if (event.getCode() == KeyCode.DELETE)
            {
                deleteSelectedFolder();
                event.consume();
            }
            // Additional shortcuts can be added here.
        });
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
        BackgroundExecutor.getInstance().executeIO(() -> {
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

            String sanitized = fileIO.sanitizeFolderName(name.trim());
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
                newFolder = fileIO.createDirectory(finalParent, sanitized);
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
            NotificationUtil.showToast(folderTree, "Please select a folder to move to trash", "fas-exclamation-circle");
            return;
        }
        if (projectRoot != null && folder.equals(projectRoot)) 
        {
            NotificationUtil.showToast(folderTree, "Cannot move the project root folder to trash", "fas-exclamation-circle");
            return;
        }

        // Count files in the background
        BackgroundExecutor.getInstance().executeIO(() -> {
            long fileCount = fileIO.countFilesRecursive(folder);
            Platform.runLater(() -> showDeleteConfirmation(folder, projectRoot, fileCount));
        });
    }

    /**
     * Shows the confirmation dialog for moving to trash.
     * Called on the JavaFX Application Thread.
     */
    private void showDeleteConfirmation(File folder, File projectRoot, long fileCount) 
    {
        if (!folder.exists()) 
        {
            NotificationUtil.showToast(folderTree, "Folder no longer exists", "fas-exclamation-circle");
            return;
        }

        String message = fileCount > 0
                ? "Move \"" + folder.getName() + "\" and all " + fileCount + " file(s) inside it to the Trash?"
                : "Move \"" + folder.getName() + "\" to the Trash?";

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Move to Trash");
        confirm.setHeaderText(null);
        confirm.setContentText(message);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) 
            {
                // Attempt trash in the background
                attemptTrashInBackground(folder, projectRoot);
            }
        });
    }

    /**
     * Attempts to move the folder to the OS trash in the background.
     * On success, updates UI. On failure, shows the fallback dialog.
     */
    private void attemptTrashInBackground(File folder, File projectRoot) 
    {
        BackgroundExecutor.getInstance().executeIO(() -> {
            if (!folder.exists()) 
            {
                Platform.runLater(() ->
                    NotificationUtil.showToast(folderTree, "Folder no longer exists", "fas-exclamation-circle")
                );
                return;
            }
            boolean moved = fileIO.moveToTrash(folder);
            Platform.runLater(() -> {
                if (moved) 
                {
                    navigateAwayIfCurrentFolderDeleted(folder, projectRoot);
                    NotificationUtil.showToast(folderTree, "Folder moved to Trash");
                } 
                else 
                {
                    // Show fallback dialog (trash not supported or failed)
                    showTrashFailedFallback(folder, projectRoot);
                }
            });
        });
    }

    /**
     * Shows a dialog informing the user that trash is not supported and offers
     * a permanent deletion. Called on the JavaFX Application Thread.
     */
    private void showTrashFailedFallback(File folder, File projectRoot) 
    {
        if (!folder.exists()) 
        {
            NotificationUtil.showToast(folderTree, "Folder no longer exists", "fas-exclamation-circle");
            return;
        }

        Alert fallbackConfirm = new Alert(Alert.AlertType.CONFIRMATION);
        fallbackConfirm.setTitle("Move to Trash Failed. Asking permission for permenent deletion");
        fallbackConfirm.setHeaderText(null);
        fallbackConfirm.setContentText(
            "Moving to Trash is not supported on this system.\n" +
            "The folder will be permanently deleted and cannot be recovered.\n" +
            "Do you want to continue?"
        );
        fallbackConfirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) 
            {
                // Permanent delete in the background
                performPermanentDeleteInBackground(folder, projectRoot);
            }
        });
    }

    /**
     * Permanently deletes the folder in the background.
     * Updates UI on success or failure.
     */
    private void performPermanentDeleteInBackground(File folder, File projectRoot) 
    {
        BackgroundExecutor.getInstance().executeIO(() -> {
            try 
            {
                fileIO.deleteRecursive(folder);
                Platform.runLater(() -> {
                    navigateAwayIfCurrentFolderDeleted(folder, projectRoot);
                    NotificationUtil.showToast(folderTree, "Folder permanently deleted");
                });
            } 
            catch (IOException ex) 
            {
                Platform.runLater(() ->
                    ErrorHandler.show(null, "Failed to delete folder", ex)
                );
            }
        });
    }

/**
 * Navigates away from the deleted folder if it was the current one.
 * Called on the JavaFX Application Thread.
 */
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