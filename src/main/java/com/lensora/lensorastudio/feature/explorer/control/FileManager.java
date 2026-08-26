package com.lensora.lensorastudio.feature.explorer.control;

import com.lensora.lensorastudio.core.context.ProjectContext;
import com.lensora.lensorastudio.core.io.InstrumentedFileIO;
import com.lensora.lensorastudio.core.watch.FolderWatchService;
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
    private final FolderWatchService folderWatchService;
    private final ProjectContext projectContext;
    private final InstrumentedFileIO fileIO;

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
        // Single source of truth context created first
        this.projectContext = new ProjectContext();

        // I/O layer binds directly to context (Zero UI coupling)
        this.fileIO = new InstrumentedFileIO(this::handleRefreshCallback, projectContext::getProjectRoot);

        this.folderTreeManager = new FolderTreeManager(
                folderTree, breadcrumbContainer, btnBack, btnForward, lblFolderHeader, projectContext, fileIO
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
                fileListingManager.moreThanOneSelectedBinding(),
                projectContext::getProjectRoot,
                fileIO
        );

        this.folderWatchService = new FolderWatchService();

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

        // External-change auto-refresh: supplements (does not replace) the
        // app's own explicit refresh-after-operation calls above.
        folderWatchService.setOnExternalChange(this::handleExternalChange);

        // Listen to root changes automatically if needed by other services
        projectContext.projectRootProperty().addListener((obs, oldRoot, newRoot) -> {
            if (newRoot != null) folderWatchService.watch(newRoot);
        });
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

    /** In-app operations call this. Declares the change as expected, then refreshes. */
    private void handleRefreshCallback(File target)
    {
        File folderToRefresh = (target != null) ? target : folderTreeManager.getCurrentFolder();
        if (folderToRefresh == null) return;

        refreshFolderAndListing(folderToRefresh);
    }

    /** FolderWatchService calls this - already confirmed genuinely external by the coordinator upstream. */
    private void handleExternalChange(File folder)
    {
        refreshFolderAndListing(folder);
    }

    private void refreshFolderAndListing(File folder)
    {
        folderTreeManager.refreshFolder(folder);
        if (folder.equals(folderTreeManager.getCurrentFolder()))
        {
            fileListingManager.refresh();
        }
    }

    // ─── Public API ─────────────────────────────────────────────────────────

    public void setStage(Stage stage) { fileOperationsManager.setStage(stage); }
    public void setSnapFX(SnapFX snapFX) { fileOperationsManager.setSnapFX(snapFX); fileListingManager.setSnapFX(snapFX); }
    public void setOnPathChanged(Consumer<String> callback) { folderTreeManager.setOnPathChanged(callback); }

    public FileListingManager getFileListingManager() { return fileListingManager; }
    public FileOperationsManager getFileOperationsManager() { return fileOperationsManager; }
    public FolderTreeManager getFolderTreeManager() { return folderTreeManager; }

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
        folderWatchService.stop();
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