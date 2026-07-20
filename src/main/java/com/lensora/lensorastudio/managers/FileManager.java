// package com.lensora.lensorastudio.managers;

// import com.lensora.lensorastudio.util.Dialogs;
// import com.lensora.lensorastudio.util.ErrorHandler;
// import com.lensora.lensorastudio.util.FileIconUtil;

// import javafx.application.Platform;
// import javafx.beans.binding.Bindings;
// import javafx.beans.property.SimpleStringProperty;
// import javafx.collections.ObservableList;
// import javafx.concurrent.Task;
// import javafx.geometry.Pos;
// import javafx.scene.Node;
// import javafx.scene.Scene;
// import javafx.scene.control.*;
// import javafx.scene.image.Image;
// import javafx.scene.image.ImageView;
// import javafx.scene.input.*;
// import javafx.scene.layout.FlowPane;
// import javafx.scene.layout.HBox;
// import javafx.scene.layout.VBox;
// import javafx.stage.DirectoryChooser;
// import javafx.stage.Stage;
// import org.kordamp.ikonli.javafx.FontIcon;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// import java.awt.Desktop;
// import java.io.File;
// import java.io.IOException;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.StandardCopyOption;
// import java.time.Instant;
// import java.time.LocalDateTime;
// import java.time.ZoneId;
// import java.time.format.DateTimeFormatter;
// import java.util.ArrayList;
// import java.util.Arrays;
// import java.util.List;
// import java.util.Map;
// import java.util.Stack;
// import java.util.concurrent.ConcurrentHashMap;
// import java.util.function.Consumer;

// public class FileManager 
// {
//     private static final Logger logger               = LoggerFactory.getLogger(FileManager.class);
//     private final TreeView<File> folderTree;
//     private final TableView<File> fileTable;
//     private final TableColumn<File, String> colFileName, colFileType, colFileSize, colFileDimensions, colFileModified;
//     private final Label lblCurrentFolder, lblFileCount, lblFolderHeader;
//     private final HBox progressContainer;
//     private final ProgressBar progressBar;
//     private final Label progressLabel, progressSpeedLabel, progressEtaLabel;
//     private final MenuItem ctxFileOpen, ctxFileRename, ctxFileCopy, ctxFileMove, ctxFileDelete, ctxFileShowInExplorer;
//     private Stage ownerStage;

//     private final HBox breadcrumbContainer;
//     private final Button btnBack, btnForward;
//     private final TextField fileSearchField;
//     private ToggleGroup viewToggleGroup;
//     private final ToggleButton btnDetails, btnList, btnIcons, btnThumbnails;
//     private final ListView<File> fileListView;
//     private final ScrollPane iconScrollPane;
//     private final FlowPane iconFlowPane;

//     // Navigation history
//     private final Stack<File> backStack = new Stack<>();
//     private final Stack<File> forwardStack = new Stack<>();
//     private File currentFolder;
//     private boolean isNavigatingHistory = false;
//     private Task<Void> searchTask;
//     private File projectRoot;

//     // File Dimensions cache to avoid repeated calculations for the same file
//     private final Map<File, SimpleStringProperty> dimensionProps = new ConcurrentHashMap<>();
//     private final Map<File, String> dimensionCache = new ConcurrentHashMap<>();

//     // Clipboard formats
//     private static final DataFormat FILE_DATA_FORMAT = new DataFormat("lensora/file");
//     private static final DataFormat FILE_LIST_FORMAT = new DataFormat("lensora/file-list");

//     private FileCopyTask currentCopyTask;

//     private Consumer<String> onPathChanged;

//     public FileManager(TreeView<File> folderTree,
//                         TableView<File> fileTable,
//                         TableColumn<File, String> colFileName,
//                         TableColumn<File, String> colFileType,
//                         TableColumn<File, String> colFileSize,
//                         TableColumn<File, String> colFileDimensions,
//                         TableColumn<File, String> colFileModified,
//                         Label lblCurrentFolder,
//                         Label lblFileCount,
//                         Label lblFolderHeader,
//                         HBox progressContainer,
//                         ProgressBar progressBar,
//                         Label progressLabel,
//                         Label progressSpeedLabel,
//                         Label progressEtaLabel,
//                         MenuItem ctxFileOpen,
//                         MenuItem ctxFileRename,
//                         MenuItem ctxFileCopy,
//                         MenuItem ctxFileMove,
//                         MenuItem ctxFileDelete,
//                         MenuItem ctxFileShowInExplorer,
                    
//                     HBox breadcrumbContainer,
//                     Button btnBack,
//                     Button btnForward,
//                     TextField fileSearchField,
//                     ToggleGroup viewToggleGroup,
//                     ToggleButton btnDetails,
//                     ToggleButton btnList,
//                     ToggleButton btnIcons,
//                     ToggleButton btnThumbnails,
//                     ListView<File> fileListView,
//                     ScrollPane iconScrollPane,
//                     FlowPane iconFlowPane
//                 ) 
//     {
//         this.folderTree = folderTree;
//         this.fileTable = fileTable;
//         this.colFileName = colFileName;
//         this.colFileType = colFileType;
//         this.colFileSize = colFileSize;
//         this.colFileDimensions = colFileDimensions;
//         this.colFileModified = colFileModified;
//         this.lblCurrentFolder = lblCurrentFolder;
//         this.lblFileCount = lblFileCount;
//         this.lblFolderHeader = lblFolderHeader;
//         this.progressContainer = progressContainer;
//         this.progressBar = progressBar;
//         this.progressLabel = progressLabel;
//         this.progressSpeedLabel = progressSpeedLabel;
//         this.progressEtaLabel = progressEtaLabel;
//         this.ctxFileOpen = ctxFileOpen;
//         this.ctxFileRename = ctxFileRename;
//         this.ctxFileCopy = ctxFileCopy;
//         this.ctxFileMove = ctxFileMove;
//         this.ctxFileDelete = ctxFileDelete;
//         this.ctxFileShowInExplorer = ctxFileShowInExplorer;

//         this.breadcrumbContainer = breadcrumbContainer;
//         this.btnBack = btnBack;
//         this.btnForward = btnForward;
//         this.fileSearchField = fileSearchField;
//         this.viewToggleGroup = viewToggleGroup;
//         this.btnDetails = btnDetails;
//         this.btnList = btnList;
//         this.btnIcons = btnIcons;
//         this.btnThumbnails = btnThumbnails;
//         this.fileListView = fileListView;
//         this.iconScrollPane = iconScrollPane;
//         this.iconFlowPane = iconFlowPane;


//         setupViewToggleGroup();
//         setupFileTableColumns();
//         setupFileContextMenu();
//         setupFolderTree();
//         setupFolderContextMenu();
//         setupFolderTreeListener();

//         // ─── NEW: view switching, navigation, search ───
//         setupViewSwitching();
//         setupNavigationButtons();
//         setupSearch();
//         setupBreadcrumbClick();
//     }

//     public void setStage(Stage stage) 
//     {
//         this.ownerStage = stage;
//     }

//     public void setOnPathChanged(Consumer<String> callback) 
//     {
//         this.onPathChanged = callback;
//     }


//     /**
//      * Sets up the folder tree selection listener.
//      */
//     private void setupFolderTreeListener() 
//     {
//         folderTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
//         if (newVal != null && newVal.getValue() != null && newVal.getValue().isDirectory()) {
//             navigateTo(newVal.getValue());
//         }   
//         });
//     }

//     private void setupFolderTree() 
//     {
//         folderTree.setCellFactory(tv -> new TreeCell<>() {
//             @Override
//             protected void updateItem(File item, boolean empty) 
//             {
//                 super.updateItem(item, empty);
//                 if (empty || item == null) 
//                 {
//                     setText(null);
//                     setGraphic(null);
//                 } 
//                 else 
//                 {
//                     setText(item.getName());  // display only the folder name

//                     // Use FontAwesome folder icon
//                     FontIcon folderIcon = new FontIcon("fas-folder");
//                     folderIcon.setIconSize(16);
//                     setGraphic(folderIcon);
//                 }
//             }
//         });
//     }

//     private void loadFolderTree(String rootPath)
//     {
//         if (rootPath == null || rootPath.isEmpty()) 
//         {
//             folderTree.setRoot(null);
//             return;
//         }
//         File rootDir = new File(rootPath);
//         if (!rootDir.exists() || !rootDir.isDirectory()) 
//         {
//             folderTree.setRoot(null);
//             return;
//         }
//         TreeItem<File> rootItem = new TreeItem<>(rootDir);
//         rootItem.setExpanded(true);
//         addChildren(rootItem);
//         folderTree.setRoot(rootItem);
//         folderTree.setShowRoot(false);
//     }


//     private void loadFileTable(File folder) 
//     {
//         if (folder == null || !folder.isDirectory()) 
//         {
//             fileTable.getItems().clear();
//             lblCurrentFolder.setText("");
//             lblFileCount.setText("");
//             return;
//         }
//         File[] files = folder.listFiles(file -> !file.isDirectory());
//         if (files == null) files = new File[0];
//         fileTable.getItems().setAll(files);
//         lblCurrentFolder.setText(folder.getName());
//         lblFileCount.setText(files.length + " files");
//     }



//     /**
//      * Recursively copies a directory.
//      */
//     private void copyDirectory(File source, File dest) throws IOException 
//     {
//         if (!dest.exists())
//         {
//             Files.createDirectories(dest.toPath());
//         }
//         File[] children = source.listFiles();
//         if (children == null) return;
//         for (File child : children) 
//         {
//             File destChild = new File(dest, child.getName());
//             if (child.isDirectory()) 
//             {
//                 copyDirectory(child, destChild);
//             } 
//             else 
//             {
//                 Files.copy(child.toPath(), destChild.toPath(), StandardCopyOption.REPLACE_EXISTING);
//             }
//         }
//     }

//         private void copySelectedFilesToList() 
//     {
//         List<File> selectedFiles = fileTable.getSelectionModel().getSelectedItems();
//         if (selectedFiles.isEmpty()) 
//         {
//             Dialogs.showInfo(null, "Copy", null, "No files selected.");
//             return;
//         }
//         ClipboardContent content = new ClipboardContent();
//         content.put(FILE_LIST_FORMAT, selectedFiles);
//         content.put(DataFormat.FILES, selectedFiles); // for external apps
//         Clipboard.getSystemClipboard().setContent(content);
//         Dialogs.showInfo(null, "Copy", null, selectedFiles.size() + " file(s) copied.");
//     }

//     private void displaySearchResults(List<File> results) 
//     {
//         // Update all views with the results
//         fileTable.getItems().setAll(results);
//         String currentView = getCurrentView();
//         if (currentView.equals("list")) populateListView(fileTable.getItems());
//         else if (currentView.equals("icons") || currentView.equals("thumbnails"))
//             populateIconView(currentView.equals("thumbnails"));
//     }

//     private void openFile(File file) 
//     {
//         if (file.isDirectory())
//         {
//             navigateTo(file);
//         } 
//         else 
//         {
//             try 
//             {
//                 Desktop.getDesktop().open(file);
//             } 
//             catch (IOException ex) 
//             {
//                 ErrorHandler.show(null, "Could not open file", ex);
//             }
//         }
//     }
// }


















































package com.lensora.lensorastudio.managers;

import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.io.File;
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
                        FlowPane iconFlowPane)
    {
        this.folderTreeManager = new FolderTreeManager(folderTree, breadcrumbContainer, btnBack, btnForward, lblFolderHeader);

        this.fileListingManager = new FileListingManager(
                fileTable, colFileName, colFileType, colFileSize, colFileDimensions, colFileModified,
                lblCurrentFolder, lblFileCount, fileSearchField,
                btnDetails, btnList, btnIcons, btnThumbnails,
                fileListView, iconScrollPane, iconFlowPane);

        this.fileOperationsManager = new FileOperationsManager(
                ctxFileOpen, ctxFileRename, ctxFileCopy, ctxFileCut, ctxFileMove, ctxFileDelete, ctxFileShowInExplorer, ctxFileProperties,
                progressContainer, progressBar, progressLabel, progressSpeedLabel, progressEtaLabel,
                fileListingManager::getSelectedFile,
                fileListingManager::getSelectedFiles,
                folderTreeManager::refreshSelected);

        // Selecting a folder in the tree loads its files into the listing.
        folderTreeManager.setOnFolderSelected(fileListingManager::loadFolder);

        // Folder-tree paste delegates to the shared operations manager.
        folderTreeManager.setOnPasteRequested(() ->
                fileOperationsManager.pasteInto(folderTreeManager.getSelectedFolder()));

        // After any file operation, also refresh the tree in case folders changed.
        folderTreeManager.setOnRefreshRequested(fileListingManager::refresh);
    }

    // ─── Public API (unchanged) ─────────────────────────────────────────────

    public void setStage(Stage stage) { fileOperationsManager.setStage(stage); }
    public void setSnapFX(SnapFX snapFX) { fileOperationsManager.setSnapFX(snapFX); }
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
}