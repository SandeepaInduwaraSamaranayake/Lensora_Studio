package com.lensora.lensorastudio.managers;

import javafx.beans.binding.BooleanBinding;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.snapfx.SnapFX;

/**
 * Facade over the file-explorer subsystem. Delegates to:
 *  - FolderTreeManager  (tree, navigation, folder context menu)
 *  - FileListingManager (table/list/icon views, search)
 *  - FileOperationsManager (file context menu, clipboard copy/paste)
 *
 * Keeps the exact same public API FileExplorerController already uses,
 * so no changes are needed there.
 */
public class FileManager
{
    private final FolderTreeManager folderTreeManager;
    private final FileListingManager fileListingManager;
    private final FileOperationsManager fileOperationsManager;

    public FileManager(TreeView<File> folderTree,
                        TableView<File> fileTable,
                        TableColumn<File, String> colFileName,
                        TableColumn<File, String> colFileType,
                        TableColumn<File, String> colFileSize,
                        TableColumn<File, String> colFileDimensions,
                        TableColumn<File, String> colFileModified,
                        Label lblCurrentFolder,
                        Label lblFileCount,
                        Label lblFolderHeader,
                        HBox progressContainer,
                        ProgressBar progressBar,
                        Label progressLabel,
                        Label progressSpeedLabel,
                        Label progressEtaLabel,
                        MenuItem ctxFileOpen,
                        Menu ctxOpenWithMenu,
                        MenuItem ctxFileRename,
                        MenuItem ctxFileCopy,
                        MenuItem ctxFileCut,
                        MenuItem ctxFileMove,
                        MenuItem ctxFileDelete,
                        MenuItem ctxFileShowInExplorer,
                        MenuItem ctxFileProperties,
                        HBox breadcrumbContainer,
                        Button btnBack,
                        Button btnForward,
                        TextField fileSearchField,
                        ToggleGroup viewToggleGroupUnused, // kept for signature compatibility
                        ToggleButton btnDetails,
                        ToggleButton btnList,
                        ToggleButton btnIcons,
                        ToggleButton btnThumbnails,
                        ListView<File> fileListView,
                        ScrollPane iconScrollPane,
                        FlowPane iconFlowPane,
                        BooleanBinding multiSelectBinding)
    {
        this.folderTreeManager = new FolderTreeManager(folderTree, breadcrumbContainer, btnBack, btnForward, lblFolderHeader);

        this.fileListingManager = new FileListingManager(
                fileTable, colFileName, colFileType, colFileSize, colFileDimensions, colFileModified,
                lblCurrentFolder, lblFileCount, fileSearchField,
                btnDetails, btnList, btnIcons, btnThumbnails,
                fileListView, iconScrollPane, iconFlowPane);

        this.fileOperationsManager = new FileOperationsManager(
                ctxFileOpen, ctxOpenWithMenu, ctxFileRename, ctxFileCopy, ctxFileCut, ctxFileMove, ctxFileDelete, ctxFileShowInExplorer, ctxFileProperties,
                progressContainer, progressBar, progressLabel, progressSpeedLabel, progressEtaLabel,
                fileListingManager::getSelectedFile,
                fileListingManager::getSelectedFiles,
                folderTreeManager::refreshSelected, multiSelectBinding);

        // Selecting a folder in the tree loads its files into the listing.
        folderTreeManager.setOnFolderSelected(fileListingManager::loadFolder);

        // Folder-tree paste delegates to the shared operations manager.
        folderTreeManager.setOnPasteRequested(() ->
                fileOperationsManager.pasteInto(folderTreeManager.getSelectedFolder()));

        // After any file operation, also refresh the tree in case folders changed.
        folderTreeManager.setOnRefreshRequested(fileListingManager::refresh);

        // Internal drag from file listing → folder tree = move.
        // External OS drag-in → folder tree = copy.
        folderTreeManager.setOnFilesDropped((files, targetFolder, isMove) ->
                fileOperationsManager.dropFilesInto(files, targetFolder, isMove));

        // External OS drag-in → file listing area = copy into currently open folder.
        fileListingManager.setOnFilesDroppedIntoCurrentFolder((files, isMove) ->
                fileOperationsManager.dropFilesInto(files, folderTreeManager.getCurrentFolder(), isMove));
    }

    // ─── Public API (unchanged) ─────────────────────────────────────────────

    public void setStage(Stage stage) { fileOperationsManager.setStage(stage); }
    public void setSnapFX(SnapFX snapFX) { fileOperationsManager.setSnapFX(snapFX); fileListingManager.setSnapFX(snapFX);}
    public void setOnPathChanged(Consumer<String> callback) { folderTreeManager.setOnPathChanged(callback); }

    public FileListingManager getFileListingManager() { return fileListingManager; }
    public FileOperationsManager getFileOperationsManager() { return fileOperationsManager;}

    public void loadProjectPath(String path)
    {
        folderTreeManager.loadProjectPath(path);
    }

    public void goBack() { folderTreeManager.goBack(); }
    public void goForward() { folderTreeManager.goForward(); }

    public void setupCopyPasteShortcuts(Scene scene)
    {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.C)
            {
                if (folderTreeManager.isFocused())
                {
                    folderTreeManager.copySelectedFolder();
                    e.consume();
                }
                else if (fileListingManager.isFocused())
                {
                    fileOperationsManager.copyFilesToClipboard(fileListingManager.getSelectedFiles());
                    e.consume();
                }
            }
            else if (e.isControlDown() && e.getCode() == KeyCode.V)
            {
                if (folderTreeManager.isFocused())
                {
                    fileOperationsManager.pasteInto(folderTreeManager.getSelectedFolder());
                    e.consume();
                }
            }
        });
    }

    public void setOnNavigationPersisted(Consumer<File> callback)
    {
        folderTreeManager.setOnNavigationPersisted(callback);
    }

    public String getCurrentFolderRelativePath()
    {
        return folderTreeManager.getCurrentFolderRelativePath();
    }

    public void expandAndSelectRelativePath(String relativePath)
    {
        folderTreeManager.expandAndSelectRelativePath(relativePath);
    }
}