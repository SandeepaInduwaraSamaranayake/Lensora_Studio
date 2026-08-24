package com.lensora.lensorastudio.feature.explorer.control;

import com.lensora.lensorastudio.feature.viewer.ImageViewerWindowService;
import com.lensora.lensorastudio.media.service.ImageValidator;
import com.lensora.lensorastudio.util.ExternalAppLauncher;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;

import org.snapfx.SnapFX;

/**
 * Facade over the file-explorer subsystem. Delegates to:
 *  - FolderTreeManager  (tree, navigation, folder context menu)
 *  - FileListingManager (table/list/icon views, search)
 *  - FileOperationsManager (file context menu, clipboard copy/cut/paste)
 */
public class FileManager
{
    private final FolderTreeManager folderTreeManager;
    private final FileListingManager fileListingManager;
    private final FileOperationsManager fileOperationsManager;
    private Integer currentProjectId;

    public FileManager( TreeView<File> folderTree,
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
                        HBox breadcrumbContainer,
                        Button btnBack,
                        Button btnForward,
                        Button btnRefreshFileList,
                        TextField fileSearchField,
                        ToggleButton btnDetails,
                        ToggleButton btnList,
                        ToggleButton btnIcons,
                        ToggleButton btnThumbnails,
                        ListView<File> fileListView,
                        StackPane iconGridHost
                        )
    {
        this.folderTreeManager = new FolderTreeManager(
                folderTree, breadcrumbContainer, btnBack, btnForward, lblFolderHeader
        );

        this.fileListingManager = new FileListingManager(
                fileTable, 
                colFileName, colFileType, colFileSize, colFileDimensions, colFileModified,
                fileListView, iconGridHost,
                lblCurrentFolder, lblFileCount, fileSearchField,
                btnDetails, btnList, btnIcons, btnThumbnails,
                btnRefreshFileList
        );

        this.fileOperationsManager = new FileOperationsManager(
                progressContainer, progressBar, progressLabel, progressSpeedLabel, progressEtaLabel,
                fileListingManager::getSelectedFile,
                fileListingManager::getSelectedFiles,
                this::handleRefreshCallback,
                fileListingManager.moreThanOneSelectedBinding()
        );

        // Attach context menu across all listing views
        fileListingManager.attachSharedContextMenu(fileOperationsManager.getContextMenu());

        // Folder tree listeners
        folderTreeManager.setOnFolderSelected(fileListingManager::loadFolder);
        folderTreeManager.setOnPasteRequested(() ->
                fileOperationsManager.pasteInto(folderTreeManager.getSelectedFolder()));
        folderTreeManager.setOnRefreshRequested(fileListingManager::refresh);
        folderTreeManager.setOnFilesDropped((files, targetFolder, isMove) ->
                fileOperationsManager.dropFilesInto(files, targetFolder, isMove));

        // Refresh file list & folder tree together
        fileListingManager.setRefreshCallback(folderTreeManager::refreshSelected);

        // External OS drag-and-drop into file listing area
        fileListingManager.setOnFilesDroppedIntoCurrentFolder((files, isMove) ->
                fileOperationsManager.dropFilesInto(files, folderTreeManager.getCurrentFolder(), isMove));

        // Handle double-click / enter navigation
        fileListingManager.setOnDoubleClick(this::handleDoubleClickOpen);
    }

    private void handleDoubleClickOpen(File file)
    {
        if (file == null || file.isDirectory()) return;

        if (ImageValidator.isJavaFXLoadable(file))
        {
            ImageViewerWindowService.getInstance().openImages(List.of(file));
        }
        else
        {
            ExternalAppLauncher.openWithSystemDefault(file);
        }
    }

    private void handleRefreshCallback(File target)
    {
        if (target != null) 
        {
            // Refresh the specific folder in the tree
            folderTreeManager.refreshFolder(target);
            // If the target is the currently visible folder, refresh the file listing too
            if (target.equals(folderTreeManager.getCurrentFolder())) 
            {
                fileListingManager.refresh();
            }
        } 
        else
        {
            // No specific target - refresh the currently selected folder (triggers file listing refresh)
            folderTreeManager.refreshSelected();
        }
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    public void setStage(Stage stage) { fileOperationsManager.setStage(stage); }
    public void setSnapFX(SnapFX snapFX) { fileOperationsManager.setSnapFX(snapFX); fileListingManager.setSnapFX(snapFX); }
    public void setOnPathChanged(Consumer<String> callback) { folderTreeManager.setOnPathChanged(callback); }

    public FileListingManager getFileListingManager() { return fileListingManager; }
    public FileOperationsManager getFileOperationsManager() { return fileOperationsManager; }

    public void loadProjectPath(String path) { folderTreeManager.loadProjectPath(path); }
    public void goBack() { folderTreeManager.goBack(); }
    public void goForward() { folderTreeManager.goForward(); }

    public void loadFolder(File folder) { fileListingManager.loadFolder(folder); }
    public String getCurrentFolderRelativePath() { return folderTreeManager.getCurrentFolderRelativePath(); }
    public void expandAndSelectRelativePath(String relativePath) { folderTreeManager.expandAndSelectRelativePath(relativePath); }
    public void setOnNavigationPersisted(Consumer<File> callback) { folderTreeManager.setOnNavigationPersisted(callback); }
    public void showVirtualFileSet(List<File> files) { fileListingManager.showVirtualFileSet(files); }

    public Integer getCurrentProjectId() { return currentProjectId; }
    public void setCurrentProjectId(Integer projectId) { this.currentProjectId = projectId; }

    /** Shuts down background image dimension extraction threads. */
    public void shutdown()
    {
        fileListingManager.shutdownDimensionExecutor();
    }

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
            else if (e.isControlDown() && e.getCode() == KeyCode.X)
            {
                if (fileListingManager.isFocused())
                {
                    fileOperationsManager.cutFilesToClipboard(fileListingManager.getSelectedFiles());
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
}