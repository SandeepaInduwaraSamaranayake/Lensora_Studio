package com.lensora.lensorastudio.feature.explorer.control;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lensora.lensorastudio.core.io.InstrumentedFileIO;
import com.lensora.lensorastudio.core.threading.BackgroundExecutor;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;
import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;
import com.lensora.lensorastudio.ui.util.FileExplorerShortcuts;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    private Stage ownerStage;
    private final FileClipboardService clipboardService;

    /** Paste is delegated out - FileOperationsManager owns the actual copy-with-progress logic. */
    private Runnable pasteRequested = () -> {};

    public FolderContextMenuManager(    TreeView<File> folderTree, 
                                        FolderTreeViewManager treeViewManager,
                                        FolderNavigationManager navigationManager, 
                                        InstrumentedFileIO fileIO, 
                                        FileClipboardService clipboardService
                                    )
    {
        this.folderTree = folderTree;
        this.treeViewManager = treeViewManager;
        this.navigationManager = navigationManager;
        this.fileIO = fileIO;
        this.clipboardService = clipboardService;

        setupContextMenu();
    }

    public void setOnPasteRequested(Runnable callback) { this.pasteRequested = callback; }
    public void setOwnerStage(Stage stage) { this.ownerStage = stage; }

    // ─── Context menu (copy/paste/open/copy-path) ──────────────────────────

    private void setupContextMenu()
    {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem newFolderItem = new MenuItem("_New Folder");
        MenuItem copyItem = new MenuItem("_Copy");
        MenuItem cutItem = new MenuItem("Cu_t");
        MenuItem pasteItem = new MenuItem("_Paste");
        MenuItem renameItem = new MenuItem("_Rename");
        MenuItem deleteFolderItem = new MenuItem("_Delete Folder");
        MenuItem openItem = new MenuItem("_Open in Explorer");
        MenuItem copyPathItem = new MenuItem("Copy Direc_tory Path");
        MenuItem expandItem = new MenuItem("Expand");
        MenuItem collapseItem = new MenuItem("Collapse");

        newFolderItem.setAccelerator(FileExplorerShortcuts.NEW_FOLDER);
        copyItem.setAccelerator(FileExplorerShortcuts.COPY);
        cutItem.setAccelerator(FileExplorerShortcuts.CUT);
        pasteItem.setAccelerator(FileExplorerShortcuts.PASTE);
        renameItem.setAccelerator(FileExplorerShortcuts.RENAME);
        deleteFolderItem.setAccelerator(FileExplorerShortcuts.DELETE);

        newFolderItem.setOnAction(e -> createNewFolder());
        copyItem.setOnAction(e -> copySelectedFolders());
        cutItem.setOnAction(e -> cutSelectedFolders());
        pasteItem.setOnAction(e -> pasteRequested.run());
        renameItem.setOnAction(e -> renameFolder());
        deleteFolderItem.setOnAction(e -> deleteSelectedFolders());
        openItem.setOnAction(e -> openSelectedFolderInExplorer());
        copyPathItem.setOnAction(e -> copySelectedFolderPath());
        expandItem.setOnAction(e -> expandAllFromSelection());
        collapseItem.setOnAction(e -> collapseAllFromSelection());

        contextMenu.getItems().addAll(
                                        newFolderItem, copyItem, cutItem,
                                        new SeparatorMenuItem(),
                                        pasteItem,
                                        renameItem,
                                        deleteFolderItem,
                                        openItem,
                                        copyPathItem,
                                        expandItem,
                                        collapseItem
                                    );
        folderTree.setContextMenu(contextMenu);
    }

    public void copySelectedFolders()
    {
        List<File> folders = treeViewManager.getSelectedFolders();
        if (folders == null || folders.isEmpty())
        {
            NotificationUtil.showToast(folderTree, "Please select at least one folder", "fas-exclamation-circle");
            return;
        }
        clipboardService.copySelectedFiles(folders); // reuse the same clipboard mechanism file listing uses
        NotificationUtil.showToast(folderTree, folders.size() == 1
                ? "Folder '" + folders.get(0).getName() + "' copied"
                : folders.size() + " folders copied");
    }

    public void cutSelectedFolders()
    {
        List<File> folders = treeViewManager.getSelectedFolders();
        if (folders.isEmpty())
        {
            NotificationUtil.showToast(folderTree, "Please select at least one folder", "fas-exclamation-circle");
            return;
        }
        clipboardService.cutSelectedFiles(folders);
        NotificationUtil.showToast(folderTree, folders.size() == 1
                ? "Folder '" + folders.get(0).getName() + "' cut"
                : folders.size() + " folders cut");
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
                    ErrorHandler.show(ownerStage, "Could not open folder", e)
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

    public void createNewFolder()
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
                ErrorHandler.show(ownerStage, "Failed to create folder", ex);
            }
        });
    }

    /**
     * Renames a folder
     */
    public void renameFolder()
    {
        File folder = treeViewManager.getSelectedFolder();
        if (folder == null) 
        {
            NotificationUtil.showToast(folderTree, "Please select a folder to rename", "fas-exclamation-circle");
            return;
        }
        if (!folder.exists())
        {
            NotificationUtil.showToast(folderTree, "Folder no longer exists", "fas-exclamation-circle");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(folder.getName());
        dialog.setTitle("Rename Folder");
        dialog.setHeaderText(null);
        dialog.setContentText("New Folder Name:");
        dialog.showAndWait().ifPresent(newName -> {
            if (newName == null || newName.trim().isEmpty()) return;

            try
            {
                File newFolder = fileIO.rename(folder, newName);
                navigationManager.navigateTo(newFolder);
            }
            catch (FileAlreadyExistsException ex)
            {
                NotificationUtil.showToast(folderTree, ex.getMessage(), "fas-exclamation-circle");
            }
            catch (IllegalArgumentException ex)
            {
                NotificationUtil.showToast(folderTree, ex.getMessage(), "fas-exclamation-circle");
            }
            catch (IOException ex)
            {
                ErrorHandler.show(ownerStage, "Failed to rename file", ex);
            }
        });
    }

    // ============================== Delete Folder ==============================

    public void deleteSelectedFolders()
    {
        List<File> folders = treeViewManager.getSelectedFolders();
        File projectRoot = treeViewManager.getProjectRoot();

        if (folders == null || folders.isEmpty())
        {
            NotificationUtil.showToast(folderTree, "Please select a folder to move to trash", "fas-exclamation-circle");
            return;
        }
        if (projectRoot != null && folders.contains(projectRoot))
        {
            NotificationUtil.showToast(folderTree, "Cannot move the project root folder to trash", "fas-exclamation-circle");
            return;
        }

        // Count files in the background
        BackgroundExecutor.getInstance().executeIO(() -> {
            long totalFiles = folders.stream().mapToLong(fileIO::countFilesRecursive).sum();
            Platform.runLater(() -> showDeleteConfirmation(folders, projectRoot, totalFiles));
        });
    }

    /**
     * Shows the confirmation dialog for moving to trash.
     * Called on the JavaFX Application Thread.
     */
    private void showDeleteConfirmation(List<File> folders, File projectRoot, long fileCount)
    {
        List<File> existingFolders = folders.stream().filter(File::exists).collect(Collectors.toList());
        if (existingFolders.isEmpty())
        {
            NotificationUtil.showToast(folderTree, "Folder(s) no longer exist", "fas-exclamation-circle");
            return;
        }

        String message = buildDeleteMessage(existingFolders, fileCount);

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Move to Trash");
        confirm.setHeaderText(null);
        confirm.setContentText(message);
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK)
            {
                // Attempt trash in the background
                attemptTrashInBackground(existingFolders, projectRoot);
            }
        });
    }

    private String buildDeleteMessage(List<File> folders, long fileCount)
    {
        if (folders.size() == 1)
        {
            File folder = folders.get(0);
            return fileCount > 0
                    ? "Move \"" + folder.getName() + "\" and all " + fileCount + " file(s) inside it to the Trash?"
                    : "Move \"" + folder.getName() + "\" to the Trash?";
        }
        return "Move " + folders.size() + " folders (" + fileCount + " file(s) total) to the Trash?";
    }

    /**
     * Attempts to move each folder to the OS trash in the background.
     * Splits into succeeded/failed - only the failed ones go through the
     * fallback flow, so a folder that trashed successfully never gets asked
     * about a second time.
     */
    private void attemptTrashInBackground(List<File> folders, File projectRoot)
    {
        BackgroundExecutor.getInstance().executeIO(() -> {
            List<File> succeeded = new ArrayList<>();
            List<File> failed = new ArrayList<>();

            for (File folder : folders)
            {
                if (!folder.exists()) continue; // vanished between confirmation and now silently skip
                if (fileIO.moveToTrash(folder)) succeeded.add(folder);
                else failed.add(folder);
            }

            Platform.runLater(() -> {
                if (!succeeded.isEmpty())
                {
                    for (File folder : succeeded)
                    {
                        navigateAwayIfCurrentFolderDeleted(folder, projectRoot);
                    }
                    NotificationUtil.showToast(folderTree, succeeded.size() == 1
                            ? "Folder moved to Trash" : succeeded.size() + " folders moved to Trash");
                }

                if (!failed.isEmpty())
                {
                    // Show fallback dialog (trash not supported or failed)
                    showTrashFailedFallback(failed, projectRoot);
                }
            });
        });
    }

    /**
     * Shows a dialog informing the user that trash is not supported for the
     * given folder(s) and offers permanent deletion instead. Called on the
     * JavaFX Application Thread.
     */
    private void showTrashFailedFallback(List<File> folders, File projectRoot)
    {
        List<File> existingFolders = folders.stream().filter(File::exists).collect(Collectors.toList());
        if (existingFolders.isEmpty())
        {
            NotificationUtil.showToast(folderTree, "Folder(s) no longer exist", "fas-exclamation-circle");
            return;
        }

        Alert fallbackConfirm = new Alert(Alert.AlertType.CONFIRMATION);
        fallbackConfirm.setTitle("Move to Trash Failed");
        fallbackConfirm.setHeaderText(null);
        fallbackConfirm.setContentText(
                (existingFolders.size() == 1
                        ? "Moving \"" + existingFolders.get(0).getName() + "\" to Trash is not supported on this system.\n"
                        : "Moving " + existingFolders.size() + " folder(s) to Trash is not supported on this system.\n")
                + "They will be permanently deleted and cannot be recovered.\n"
                + "Do you want to continue?"
        );
        fallbackConfirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK)
            {
                // Permanent delete in the background
                performPermanentDeleteInBackground(existingFolders, projectRoot);
            }
        });
    }

    /**
     * Permanently deletes each folder in the background.
     * Reports success/failure counts once, rather than one error dialog
     * per folder.
     */
    private void performPermanentDeleteInBackground(List<File> folders, File projectRoot)
    {
        BackgroundExecutor.getInstance().executeIO(() -> {
            List<File> deleted = new ArrayList<>();
            List<File> failedFolders = new ArrayList<>();
            IOException lastException = null;

            for (File folder : folders)
            {
                try
                {
                    fileIO.deleteRecursive(folder);
                    deleted.add(folder);
                }
                catch (IOException ex)
                {
                    failedFolders.add(folder);
                    lastException = ex;
                    logger.warn("Failed to permanently delete folder: {}", folder, ex);
                }
            }

            final IOException finalException = lastException;
            Platform.runLater(() -> {
                for (File folder : deleted)
                {
                    navigateAwayIfCurrentFolderDeleted(folder, projectRoot);
                }
                if (!deleted.isEmpty())
                {
                    NotificationUtil.showToast(folderTree, deleted.size() == 1
                            ? "Folder permanently deleted" : deleted.size() + " folders permanently deleted");
                }
                if (!failedFolders.isEmpty() && finalException != null)
                {
                    ErrorHandler.show(ownerStage, "Failed to delete " + failedFolders.size() + " folder(s)", finalException);
                }
            });
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

    private void expandAllFromSelection()
    {
        File selected = treeViewManager.getSelectedFolder();
        if (selected != null)
        {
            treeViewManager.expandAll(selected); // expand just the right-clicked subtree
        }
        else
        {
            treeViewManager.expandAll(null); // nothing selected, then expand the whole tree
        }
    }

    private void collapseAllFromSelection()
    {
        File selected = treeViewManager.getSelectedFolder();
        if (selected != null)
        {
            treeViewManager.collapseAll(selected); // collapse just the right-clicked subtree
        }
        else
        {
            treeViewManager.collapseAll(null); // nothing selected, then collapse the whole tree
        }
    }
}