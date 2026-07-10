package com.lensora.lensorastudio.managers;

import com.lensora.lensorastudio.util.Dialogs;
import com.lensora.lensorastudio.util.ErrorHandler;
import com.lensora.lensorastudio.util.FileIconUtil;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;

public class FileManager 
{
    private static final Logger logger               = LoggerFactory.getLogger(FileManager.class);
    private final TreeView<File> folderTree;
    private final TableView<File> fileTable;
    private final TableColumn<File, String> colFileName, colFileType, colFileSize, colFileDimensions, colFileModified;
    private final Label lblCurrentFolder, lblFileCount, lblFolderHeader;
    private final HBox progressContainer;
    private final ProgressBar progressBar;
    private final Label progressLabel, progressSpeedLabel, progressEtaLabel;
    private final MenuItem ctxFileOpen, ctxFileRename, ctxFileCopy, ctxFileMove, ctxFileDelete, ctxFileShowInExplorer;
    private Stage ownerStage;

    private final HBox breadcrumbContainer;
    private final Button btnBack, btnForward;
    private final TextField fileSearchField;
    private ToggleGroup viewToggleGroup;
    private final ToggleButton btnDetails, btnList, btnIcons, btnThumbnails;
    private final ListView<File> fileListView;
    private final ScrollPane iconScrollPane;
    private final FlowPane iconFlowPane;

    // Navigation history
    private final Stack<File> backStack = new Stack<>();
    private final Stack<File> forwardStack = new Stack<>();
    private File currentFolder;
    private boolean isNavigatingHistory = false;
    private Task<Void> searchTask;
    private File projectRoot;

    // File Dimensions cache to avoid repeated calculations for the same file
    private final Map<File, SimpleStringProperty> dimensionProps = new ConcurrentHashMap<>();
    private final Map<File, String> dimensionCache = new ConcurrentHashMap<>();

    // Clipboard formats
    private static final DataFormat FILE_DATA_FORMAT = new DataFormat("lensora/file");
    private static final DataFormat FILE_LIST_FORMAT = new DataFormat("lensora/file-list");

    private FileCopyTask currentCopyTask;

    // Callback to refresh project details if needed
    private Runnable onFileOperationCompleted;

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
                        MenuItem ctxFileMove,
                        MenuItem ctxFileDelete,
                        MenuItem ctxFileShowInExplorer,
                    
                    HBox breadcrumbContainer,
                    Button btnBack,
                    Button btnForward,
                    TextField fileSearchField,
                    ToggleGroup viewToggleGroup,
                    ToggleButton btnDetails,
                    ToggleButton btnList,
                    ToggleButton btnIcons,
                    ToggleButton btnThumbnails,
                    ListView<File> fileListView,
                    ScrollPane iconScrollPane,
                    FlowPane iconFlowPane
                ) 
    {
        this.folderTree = folderTree;
        this.fileTable = fileTable;
        this.colFileName = colFileName;
        this.colFileType = colFileType;
        this.colFileSize = colFileSize;
        this.colFileDimensions = colFileDimensions;
        this.colFileModified = colFileModified;
        this.lblCurrentFolder = lblCurrentFolder;
        this.lblFileCount = lblFileCount;
        this.lblFolderHeader = lblFolderHeader;
        this.progressContainer = progressContainer;
        this.progressBar = progressBar;
        this.progressLabel = progressLabel;
        this.progressSpeedLabel = progressSpeedLabel;
        this.progressEtaLabel = progressEtaLabel;
        this.ctxFileOpen = ctxFileOpen;
        this.ctxFileRename = ctxFileRename;
        this.ctxFileCopy = ctxFileCopy;
        this.ctxFileMove = ctxFileMove;
        this.ctxFileDelete = ctxFileDelete;
        this.ctxFileShowInExplorer = ctxFileShowInExplorer;

        this.breadcrumbContainer = breadcrumbContainer;
        this.btnBack = btnBack;
        this.btnForward = btnForward;
        this.fileSearchField = fileSearchField;
        this.viewToggleGroup = viewToggleGroup;
        this.btnDetails = btnDetails;
        this.btnList = btnList;
        this.btnIcons = btnIcons;
        this.btnThumbnails = btnThumbnails;
        this.fileListView = fileListView;
        this.iconScrollPane = iconScrollPane;
        this.iconFlowPane = iconFlowPane;


        setupViewToggleGroup();
        setupFileTableColumns();
        setupFileContextMenu();
        setupFolderTree();
        setupFolderContextMenu();
        setupFolderTreeListener();

        // ─── NEW: view switching, navigation, search ───
        setupViewSwitching();
        setupNavigationButtons();
        setupSearch();
        setupBreadcrumbClick();
    }

    private void setupViewToggleGroup() 
    {
        // Add toggles into a group, so only one can be selected
        viewToggleGroup = new ToggleGroup();
        btnDetails.setToggleGroup(viewToggleGroup);
        btnList.setToggleGroup(viewToggleGroup);
        btnIcons.setToggleGroup(viewToggleGroup);
        btnThumbnails.setToggleGroup(viewToggleGroup);
    }

    public void setStage(Stage stage) 
    {
        this.ownerStage = stage;
    }

    private void refreshCurrentFolder() 
    {
        TreeItem<File> selected = folderTree.getSelectionModel().getSelectedItem();
        if (selected != null && selected.getValue().isDirectory()) 
        {
            loadFileTable(selected.getValue());
            // Refresh folder tree children
            selected.getChildren().clear();
            addChildren(selected);
            selected.setExpanded(true);
        }
    }

    /**
     * Sets up the file table columns (name, type, size, modified).
     */
    private void setupFileTableColumns() 
    {
        colFileName.setCellValueFactory(cellData -> 
            new SimpleStringProperty(cellData.getValue().getName()));
        
        colFileType.setCellValueFactory(cellData -> {
            String name = cellData.getValue().getName();
            int idx = name.lastIndexOf('.');
            return new SimpleStringProperty(idx > 0 ? name.substring(idx + 1) : "");
        });
        
        colFileSize.setCellValueFactory(cellData -> {
            long size = cellData.getValue().length();
            return new SimpleStringProperty(size > 0 ? formatFileSize(size) : "");
        });

        colFileDimensions.setCellValueFactory(cellData -> {
            File file = cellData.getValue();
            SimpleStringProperty prop = dimensionProps.get(file);
            if (prop == null) {
                prop = new SimpleStringProperty("");
                dimensionProps.put(file, prop);
                loadDimensions(file, prop);
            }
            return prop;
        });
        
        colFileModified.setCellValueFactory(cellData ->
            new SimpleStringProperty(
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(cellData.getValue().lastModified()),
                    ZoneId.systemDefault()
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            )
        );
    }

    // File Dimensions loading logic
    private void loadDimensions(File file, SimpleStringProperty prop) {
    if (!isImageFile(file)) {
        prop.set("");
        return;
    }
    // Check cache first
    String cached = dimensionCache.get(file);
    if (cached != null) {
        prop.set(cached);
        return;
    }
    // Load image in background
    Image img = new Image(file.toURI().toString(), true);
    img.progressProperty().addListener((obs, old, progress) -> {
        if (progress.doubleValue() >= 1.0) {
            String dims = (int) img.getWidth() + "×" + (int) img.getHeight();
            dimensionCache.put(file, dims);
            Platform.runLater(() -> prop.set(dims));
        }
    });
    img.exceptionProperty().addListener((obs, old, ex) -> {
        if (ex != null) {
            dimensionCache.put(file, "");
            Platform.runLater(() -> prop.set(""));
        }
    });
}

    // File context menu actions
    private void setupFileContextMenu()
    {
        ctxFileOpen.setOnAction(e -> openSelectedFile());
        ctxFileRename.setOnAction(e -> renameSelectedFile());
        ctxFileCopy.setOnAction(e -> copySelectedFile());
        ctxFileMove.setOnAction(e -> moveSelectedFile());
        ctxFileDelete.setOnAction(e -> deleteSelectedFile());
        ctxFileShowInExplorer.setOnAction(e -> showInExplorer());
    }

    private File getSelectedFile() 
    {
        return fileTable.getSelectionModel().getSelectedItem();
    }

    private void openSelectedFile() 
    {
        File file = getSelectedFile();
        if (file == null) return;
        try 
        {
            Desktop.getDesktop().open(file);
        } 
        catch (IOException ex) 
        {
            ErrorHandler.show(null, "Could not open file", ex);
        }
    }

    private void renameSelectedFile() 
    {
        File file = getSelectedFile();
        if (file == null) return;
        TextInputDialog dialog = new TextInputDialog(file.getName());
        dialog.setTitle("Rename File");
        dialog.setHeaderText(null);
        dialog.setContentText("New name:");
        dialog.showAndWait().ifPresent(newName -> {
            if (newName == null || newName.trim().isEmpty()) return;
            File newFile = new File(file.getParentFile(), newName);
            if (newFile.exists()) 
            {
                Dialogs.showInfo(null, "Rename", null, "File already exists.");
                return;
            }
            if (file.renameTo(newFile)) 
            {
                refreshCurrentFolder();
            } 
            else 
            {
                Dialogs.showInfo(null, "Rename", null, "Failed to rename file.");
            }
        });
    }

    private void copySelectedFile() 
    {
        File file = getSelectedFile();
        if (file == null) return;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Destination Folder");
        File destDir = chooser.showDialog(ownerStage);
        if (destDir == null) return;
        try 
        {
            Files.copy(file.toPath(), new File(destDir, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            refreshCurrentFolder();
        } 
        catch (IOException ex)
        {
            ErrorHandler.show(null, "Copy failed", ex);
        }
    }

    private void moveSelectedFile() 
    {
        File file = getSelectedFile();
        if (file == null) return;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Destination Folder");
        File destDir = chooser.showDialog(ownerStage);
        if (destDir == null) return;
        try 
        {
            Files.move(file.toPath(), new File(destDir, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            refreshCurrentFolder();
        } 
        catch (IOException ex) 
        {
            ErrorHandler.show(null, "Move failed", ex);
        }
    }

    private void deleteSelectedFile() 
    {
        File file = getSelectedFile();
        if (file == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete File");
        confirm.setHeaderText(null);
        confirm.setContentText("Are you sure you want to delete " + file.getName() + "?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) 
            {
                if (file.delete()) 
                {
                    refreshCurrentFolder();
                } 
                else 
                {
                    Dialogs.showInfo(null, "Delete", null, "Failed to delete file.");
                }
            }
        });
    }

    private void showInExplorer() 
    {
        File file = getSelectedFile();
        if (file == null) return;
        try 
        {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE_FILE_DIR)) 
            {
                Desktop.getDesktop().browseFileDirectory(file);
            } 
            else 
            {
                Dialogs.showInfo(null, "Not Supported", null, "Cannot open file browser on this system.");
            }
        } 
        catch (Exception e) 
        {
            ErrorHandler.show(null, "Could not open in explorer", e);
        }
    }

    /**
     * Sets up the folder tree selection listener.
     */
    private void setupFolderTreeListener() 
    {
        folderTree.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
        if (newVal != null && newVal.getValue() != null && newVal.getValue().isDirectory()) {
            navigateTo(newVal.getValue());
        }   
        });
    }

    private void setupFolderTree() 
    {
        folderTree.setCellFactory(tv -> new TreeCell<>() {
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
                    setText(item.getName());  // display only the folder name

                    // Use FontAwesome folder icon
                    FontIcon folderIcon = new FontIcon("fas-folder");
                    folderIcon.setIconSize(16);
                    setGraphic(folderIcon);
                }
            }
        });
    }

    private void loadFolderTree(String rootPath)
    {
        if (rootPath == null || rootPath.isEmpty()) 
        {
            folderTree.setRoot(null);
            return;
        }
        File rootDir = new File(rootPath);
        if (!rootDir.exists() || !rootDir.isDirectory()) 
        {
            folderTree.setRoot(null);
            return;
        }
        TreeItem<File> rootItem = new TreeItem<>(rootDir);
        rootItem.setExpanded(true);
        addChildren(rootItem);
        folderTree.setRoot(rootItem);
        folderTree.setShowRoot(false);
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
            // Add a placeholder to allow expansion
            if (child.listFiles(File::isDirectory) != null && child.listFiles(File::isDirectory).length > 0) 
            {
                addChildren(item);
            }
        }
    }

public void loadProjectPath(String path) {
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

    this.projectRoot = folder; // store root

    // 1. Build the folder tree with the project directory as root
    TreeItem<File> rootItem = new TreeItem<>(folder);
    rootItem.setExpanded(true);
    addChildren(rootItem);            // recursively adds subdirectories
    folderTree.setRoot(rootItem);
    folderTree.setShowRoot(false);    // hide the root node itself (show only its children)

    // 2. Navigate to the root (updates file area, breadcrumb, and selects the root in the tree)
    navigateTo(folder);
}

private void loadFolderContent(File folder) 
{
    // clear dimension cache
    dimensionProps.clear();
    dimensionCache.clear();

    if (folder == null) return;
    if (!folder.isDirectory()) return;

    // Get only files (not directories)
    File[] files = folder.listFiles(File::isFile);
    if (files == null) files = new File[0];
    Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

    fileTable.getItems().setAll(files);
    lblCurrentFolder.setText(folder.getName());
    lblFileCount.setText(files.length + " files");
    lblFolderHeader.setText("Folders  [" + folder.getAbsolutePath() + "]");

    String currentView = getCurrentView();
    if (currentView.equals("list")) populateListView(fileTable.getItems());
    else if (currentView.equals("icons") || currentView.equals("thumbnails"))
        populateIconView(currentView.equals("thumbnails"));
}


    private void loadFileTable(File folder) 
    {
        if (folder == null || !folder.isDirectory()) 
        {
            fileTable.getItems().clear();
            lblCurrentFolder.setText("");
            lblFileCount.setText("");
            return;
        }
        File[] files = folder.listFiles(file -> !file.isDirectory());
        if (files == null) files = new File[0];
        fileTable.getItems().setAll(files);
        lblCurrentFolder.setText(folder.getName());
        lblFileCount.setText(files.length + " files");
    }

    private String formatFileSize(long size)
    {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }

    // ─── Folder Tree Context Menu ───────────────────────────────────────────────

    /**
     * Sets up the folder tree context menu (Copy, Paste, Open in Explorer).
     */
    private void setupFolderContextMenu() 
    {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Copy");
        MenuItem pasteItem = new MenuItem("Paste");
        MenuItem openItem = new MenuItem("Open in Explorer");
        MenuItem copyPathItem = new MenuItem("Copy Directory Path");

        copyItem.setOnAction(e -> copySelectedFolder());
        pasteItem.setOnAction(e -> pasteIntoSelectedFolder());
        openItem.setOnAction(e -> openSelectedFolderInExplorer());
        copyPathItem.setOnAction(e -> copySelectedFolderPath());

        contextMenu.getItems().addAll(copyItem, new SeparatorMenuItem(), pasteItem, openItem, copyPathItem);
        folderTree.setContextMenu(contextMenu);
    }

    /**
     * Copies the selected folder to the system clipboard.
     * Uses a custom DataFormat to store the File object.
     */
    private void copySelectedFolder() 
    {
        TreeItem<File> selectedItem = folderTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;
        File folder = selectedItem.getValue();
        if (folder == null || !folder.isDirectory()) 
        {
            Dialogs.showInfo(null, "Copy", null, "Please select a folder.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        // Custom format for internal use
        content.put(FILE_DATA_FORMAT, folder);
        // Standard format for external apps
        content.put(DataFormat.FILES, List.of(folder));
        Clipboard.getSystemClipboard().setContent(content);
        Dialogs.showInfo(null, "Copy", null, "Folder '" + folder.getName() + "' copied to clipboard.");
    }

    /**
     * Recursively copies a directory.
     */
    private void copyDirectory(File source, File dest) throws IOException 
    {
        if (!dest.exists())
        {
            Files.createDirectories(dest.toPath());
        }
        File[] children = source.listFiles();
        if (children == null) return;
        for (File child : children) 
        {
            File destChild = new File(dest, child.getName());
            if (child.isDirectory()) 
            {
                copyDirectory(child, destChild);
            } 
            else 
            {
                Files.copy(child.toPath(), destChild.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * Pastes a folder/file from the clipboard into the selected folder.
     * Supports both files and folders (recursive copy).
     */
    private void pasteIntoSelectedFolder() 
    {
        TreeItem<File> selectedItem = folderTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) 
        {
            Dialogs.showInfo(null, "Paste", null, "Please select a destination folder.");
            return;
        }
        File targetFolder = selectedItem.getValue();
        if (targetFolder == null || !targetFolder.isDirectory()) 
        {
            Dialogs.showInfo(null, "Paste", null, "Please select a valid folder.");
            return;
        }

        Clipboard clipboard = Clipboard.getSystemClipboard();
        File source = null;
        List<File> fileList = null;

        // 1. Try custom format (internal)
        Object obj = clipboard.getContent(FILE_DATA_FORMAT);
        if (obj instanceof File) 
        {
            source = (File) obj;
        } 
        else 
        {
            // 2. Try standard FILES format (from external file manager)
            Object filesObj = clipboard.getContent(DataFormat.FILES);
            if (filesObj instanceof List) 
            {
                fileList = (List<File>) filesObj;
                if (!fileList.isEmpty()) 
                {
                    source = fileList.get(0); // take first file/folder
                }
            }
        }

        if (source == null) 
        {
            Dialogs.showInfo(null, "Paste", null, "Clipboard does not contain a valid file/folder.");
            return;
        }

        if (!source.exists()) 
        {
            Dialogs.showInfo(null, "Paste", null, "Source file/folder no longer exists.");
            return;
        }

        // Prevent pasting into itself
        if (isRecursivePaste(source, targetFolder)) 
        {
            Dialogs.showInfo(null, "Paste", null, "Cannot paste a folder into itself or its subfolder.");
            return;
        }

        File dest = new File(targetFolder, source.getName());
        if (dest.exists()) 
        {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Overwrite");
            confirm.setHeaderText("File/folder already exists");
            confirm.setContentText("'" + dest.getName() + "' already exists. Overwrite?");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) 
            {
                return;
            }
        }

        try 
        {
            if (source.isDirectory()) 
            {
                copyDirectory(source, dest);
            } 
            else 
            {
                Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            refreshCurrentFolder();
            Dialogs.showInfo(null, "Paste", null, "Pasted successfully.");
        } 
        catch (IOException ex) 
        {
            ErrorHandler.show(null, "Paste failed", ex);
        }
    }

    /**
     * Opens the selected folder in the system's file explorer.
     */
    private void openSelectedFolderInExplorer() 
    {
        TreeItem<File> selectedItem = folderTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;
        File folder = selectedItem.getValue();
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
        catch (IOException ex)
        {
            ErrorHandler.show(null, "Could not open folder", ex);
        }
    }

        private void copySelectedFolderPath() 
    {
        TreeItem<File> selectedItem = folderTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;
        File folder = selectedItem.getValue();
        if (folder == null) return;
        ClipboardContent content = new ClipboardContent();
        content.putString(folder.getAbsolutePath());
        Clipboard.getSystemClipboard().setContent(content);
        Dialogs.showInfo(null, "Copy Path", null, "Path copied to clipboard.");
    }

        private void copySelectedFilesToList() 
    {
        List<File> selectedFiles = fileTable.getSelectionModel().getSelectedItems();
        if (selectedFiles.isEmpty()) 
        {
            Dialogs.showInfo(null, "Copy", null, "No files selected.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.put(FILE_LIST_FORMAT, selectedFiles);
        content.put(DataFormat.FILES, selectedFiles); // for external apps
        Clipboard.getSystemClipboard().setContent(content);
        Dialogs.showInfo(null, "Copy", null, selectedFiles.size() + " file(s) copied.");
    }

    private void copySelectedFolderToList() 
    {
        TreeItem<File> selectedItem = folderTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) return;
        File folder = selectedItem.getValue();
        if (folder == null || !folder.isDirectory()) 
        {
            Dialogs.showInfo(null, "Copy", null, "Please select a folder.");
            return;
        }
        List<File> folderList = List.of(folder);
        ClipboardContent content = new ClipboardContent();
        content.put(FILE_LIST_FORMAT, folderList);
        content.put(DataFormat.FILES, folderList);
        Clipboard.getSystemClipboard().setContent(content);
        Dialogs.showInfo(null, "Copy", null, "Folder '" + folder.getName() + "' copied.");
    }

    private void pasteIntoSelectedFolderMulti() 
    {
        TreeItem<File> selectedItem = folderTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) 
        {
            Dialogs.showInfo(null, "Paste", null, "Please select a destination folder.");
            return;
        }
        File targetFolder = selectedItem.getValue();
        if (targetFolder == null || !targetFolder.isDirectory()) 
        {
            Dialogs.showInfo(null, "Paste", null, "Please select a valid folder.");
            return;
        }

        Clipboard clipboard = Clipboard.getSystemClipboard();
        List<File> sourceFiles = null;

        Object obj = clipboard.getContent(FILE_LIST_FORMAT);
        if (obj instanceof List) 
        {
            sourceFiles = (List<File>) obj;
        } 
        else 
        {
            Object filesObj = clipboard.getContent(DataFormat.FILES);
            if (filesObj instanceof List) 
            {
                sourceFiles = (List<File>) filesObj;
            }
        }

        if (sourceFiles == null || sourceFiles.isEmpty()) 
        {
            Dialogs.showInfo(null, "Paste", null, "Clipboard does not contain any files/folders.");
            return;
        }

        for (File src : sourceFiles) 
        {
            if (isRecursivePaste(src, targetFolder)) 
            {
                Dialogs.showInfo(null, "Paste", null, "Cannot paste a folder into itself or its subfolder.");
                return;
            }
        }

        currentCopyTask = new FileCopyTask(sourceFiles, targetFolder);

        // Bind progress bar and labels
        progressBar.progressProperty().bind(currentCopyTask.progressProperty());
        progressLabel.textProperty().bind(Bindings.createStringBinding(
            () -> String.format("%.0f%%", currentCopyTask.getProgress() * 100),
            currentCopyTask.progressProperty()
        ));
        progressSpeedLabel.textProperty().bind(Bindings.createStringBinding(
            () -> formatSpeed(currentCopyTask.speedProperty().get()),
            currentCopyTask.speedProperty()
        ));
        progressEtaLabel.textProperty().bind(Bindings.createStringBinding(
            () -> formatEta(currentCopyTask.etaProperty().get()),
            currentCopyTask.etaProperty()
        ));

        currentCopyTask.setOnSucceeded(e -> {
            hideProgress();
            refreshCurrentFolder();
        });
        currentCopyTask.setOnFailed(e -> {
            hideProgress();
            Throwable ex = currentCopyTask.getException();
            ErrorHandler.show(null, "Paste failed", ex);
        });
        currentCopyTask.setOnCancelled(e -> hideProgress());

        showProgress("Copying...");
        new Thread(currentCopyTask).start();
    }

    private boolean isRecursivePaste(File source, File target) 
    {
        if (!source.isDirectory()) return false;

        // Prevent copying a folder into itself
        if (source.equals(target)) return true;
        // If target is inside source OR source is inside target (should not happen but guard)
        String srcPath = source.getAbsolutePath();
        String tgtPath = target.getAbsolutePath();
        // Prevent: target is inside source (copying a folder into its own subfolder)
        if (tgtPath.startsWith(srcPath) && !tgtPath.equals(srcPath)) 
        {
            return true;
        }
        // Prevent: source is inside target (copying a folder into itself)
        if (srcPath.startsWith(tgtPath) && !srcPath.equals(tgtPath))
        {
            return true;
        }
        return false;
    }

    // ─── Progress UI helpers ─────────────────────────────────────────────────────

    private void showProgress(String status) 
    {
        progressContainer.setVisible(true);
        progressContainer.setManaged(true);
        // progressBar.setProgress(progress);
        // lblStatusText.setText(status);
    }

    private void hideProgress() 
    {
        progressContainer.setVisible(false);
        progressContainer.setManaged(false);
        // lblStatusText.setText("Ready");
        // Optionally unbind and reset:
        progressBar.progressProperty().unbind();
        progressLabel.textProperty().unbind();
        progressSpeedLabel.textProperty().unbind();
        progressEtaLabel.textProperty().unbind();
        progressBar.setProgress(0);
        progressLabel.setText("0%");
        progressSpeedLabel.setText("0 B/s");
        progressEtaLabel.setText("ETA: --");
    }

    private String formatSpeed(double bytesPerSecond) 
    {
        if (bytesPerSecond < 1024) return String.format("%.0f B/s", bytesPerSecond);
        if (bytesPerSecond < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSecond / 1024);
        return String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024));
    }

    private String formatEta(long seconds) 
    {
        if (seconds <= 0) return "ETA: --";
        long mins = seconds / 60;
        long secs = seconds % 60;
        if (mins > 0) return String.format("ETA: %d min %d s", mins, secs);
        return String.format("ETA: %d s", secs);
    }


    public void setupCopyPasteShortcuts(Scene scene) 
    {
        // Scene is guaranteed non-null here
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.isControlDown() && e.getCode() == KeyCode.C) 
            {
                if (folderTree.isFocused()) 
                {
                    copySelectedFolderToList();
                    e.consume();
                } 
                else if (fileTable.isFocused()) 
                {
                    copySelectedFilesToList();
                    e.consume();
                }
            } 
            else if (e.isControlDown() && e.getCode() == KeyCode.V) 
            {
                if (folderTree.isFocused()) 
                {
                    pasteIntoSelectedFolderMulti();
                    e.consume();
                }
            }
        });
    }

     // ─── View Switching ───────────────────────────────────────────────────

    private void setupViewSwitching() 
    {
        viewToggleGroup.selectedToggleProperty().addListener((obs, old, newVal) -> {
            if (newVal == btnDetails) switchView("details");
            else if (newVal == btnList) switchView("list");
            else if (newVal == btnIcons) switchView("icons");
            else if (newVal == btnThumbnails) switchView("thumbnails");
        });
        // Default to Details
        btnDetails.setSelected(true);
        switchView("details");
    }

    private void switchView(String view) 
    {
        fileTable.setVisible(false);
        fileTable.setManaged(false);
        fileListView.setVisible(false);
        fileListView.setManaged(false);
        iconScrollPane.setVisible(false);
        iconScrollPane.setManaged(false);

        switch (view) 
        {
            case "details":
                fileTable.setVisible(true);
                fileTable.setManaged(true);
                break;
            case "list":
                fileListView.setVisible(true);
                fileListView.setManaged(true);
                populateListView(fileTable.getItems());
                break;
            case "icons":
            case "thumbnails":
                iconScrollPane.setVisible(true);
                iconScrollPane.setManaged(true);
                populateIconView(view.equals("thumbnails"));
                break;
        }
    }

    private String getCurrentView() 
    {
        Toggle selected = viewToggleGroup.getSelectedToggle();
        if (selected == btnDetails) return "details";
        if (selected == btnList) return "list";
        if (selected == btnIcons) return "icons";
        if (selected == btnThumbnails) return "thumbnails";
        return "details";
    }

    // ─── Navigation (Back/Forward) ──────────────────────────────────────

    private void setupNavigationButtons() 
    {
        btnBack.setOnAction(e -> goBack());
        btnForward.setOnAction(e -> goForward());
        updateButtonStates();
    }

public void navigateTo(File folder) 
{
    if (folder == null) return;
    if (!isNavigatingHistory && currentFolder != null && !currentFolder.equals(folder)) 
    {
        backStack.push(currentFolder);
        forwardStack.clear();
    }
    currentFolder = folder;
    loadFolderContent(folder);
    updateBreadcrumb(folder);
    updateButtonStates();
    // Clear search
    if (!fileSearchField.getText().isEmpty()) 
    {
        fileSearchField.setText("");
    }
    // If this navigation was not triggered by tree selection, update tree selection
    if (!isNavigatingHistory) 
    {
        selectFolderInTree(folder);
    }
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
            // Scroll to the selected item if needed (optional)
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

    private void updateButtonStates() 
    {
        btnBack.setDisable(backStack.isEmpty());
        btnForward.setDisable(forwardStack.isEmpty());
    }

    // ─── NEW: Breadcrumb ──────────────────────────────────────────────────────

    private void setupBreadcrumbClick() 
    {
        // Click on breadcrumb container to edit path? We'll keep it simple: just click a segment to navigate.
    }

    private void updateBreadcrumb(File folder) 
    {
    breadcrumbContainer.getChildren().clear();
    if (folder == null) return;

    if (projectRoot == null) 
    {
        // Fallback: show full absolute path as a single segment (rare case)
        Button btn = new Button(folder.getAbsolutePath());
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: -color-fg-default;");
        breadcrumbContainer.getChildren().add(btn);
        return;
    }

    // Build list of segments: root first, then subfolders
    List<File> segmentFiles = new ArrayList<>();
    List<String> segmentNames = new ArrayList<>();

    segmentFiles.add(projectRoot);
    segmentNames.add(projectRoot.getName());

    if (!folder.equals(projectRoot)) 
    {
        Path relative = projectRoot.toPath().relativize(folder.toPath());
        for (int i = 0; i < relative.getNameCount(); i++) 
        {
            Path part = relative.getName(i);
            Path fullPath = projectRoot.toPath().resolve(relative.subpath(0, i+1));
            segmentFiles.add(fullPath.toFile());
            segmentNames.add(part.toString());
        }
    }

    // Add segments with separators
    for (int i = 0; i < segmentFiles.size(); i++) 
    {
        if (i > 0) 
        {
            Label sep = new Label(">");
            sep.setStyle("-fx-text-fill: -color-fg-default;");
            breadcrumbContainer.getChildren().add(sep);
        }
        Button btn = new Button(segmentNames.get(i));
        btn.setStyle("-fx-background-color: transparent;");
        final File target = segmentFiles.get(i);
        btn.setOnAction(e -> navigateTo(target));
        breadcrumbContainer.getChildren().add(btn);
    }
}


    // ─── NEW: Search ──────────────────────────────────────────────────────────

    private void setupSearch() 
    {
        fileSearchField.textProperty().addListener((obs, old, newVal) -> {
            if (searchTask != null) searchTask.cancel();
            searchTask = new Task<>() {
                @Override
                protected Void call() {
                    if (newVal == null || newVal.isEmpty()) 
                    {
                        Platform.runLater(() -> {
                            if (currentFolder != null) loadFolderContent(currentFolder);
                        });
                        return null;
                    }
                    List<File> results = new ArrayList<>();
                    searchRecursive(currentFolder, newVal.toLowerCase(), results);
                    Platform.runLater(() -> displaySearchResults(results));
                    return null;
                }
            };
            new Thread(searchTask).start();
        });
    }

    private void searchRecursive(File folder, String query, List<File> results) 
    {
        if (folder == null || !folder.isDirectory()) return;
        if (searchTask.isCancelled()) return;
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File f : files) 
        {
            if (searchTask.isCancelled()) return;
            if (f.getName().toLowerCase().contains(query)) 
            {
                results.add(f);
            }
            if (f.isDirectory()) 
            {
                searchRecursive(f, query, results);
            }
        }
    }

    private void displaySearchResults(List<File> results) 
    {
        // Update all views with the results
        fileTable.getItems().setAll(results);
        String currentView = getCurrentView();
        if (currentView.equals("list")) populateListView(fileTable.getItems());
        else if (currentView.equals("icons") || currentView.equals("thumbnails"))
            populateIconView(currentView.equals("thumbnails"));
    }

    // ─── NEW: Populate ListView ──────────────────────────────────────────────

    private void populateListView(ObservableList<File> files) 
    {
        fileListView.setItems(files);
        fileListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(File file, boolean empty) 
            {
                super.updateItem(file, empty);
                if (empty || file == null) 
                {
                    setText(null);
                    setGraphic(null);
                } 
                else 
                {
                    setText(file.getName());
                    // Use the utility with a size of 20
                    setGraphic(FileIconUtil.getFileIcon(file, 20));
                }
            }
        });
    }

    // ─── NEW: Populate Icon/Thumbnail View ──────────────────────────────────

    private void populateIconView(boolean thumbnails) 
    {
        iconFlowPane.getChildren().clear();
        for (File file : fileTable.getItems()) 
        {
            VBox card = new VBox(5);
            card.setAlignment(Pos.CENTER);
            card.setPrefSize(120, 120);
            card.setStyle("-fx-background-color: -color-bg-muted; -fx-border-color: -color-border-default; -fx-border-radius: 4; -fx-background-radius: 4;");

            Node iconNode;
            if (thumbnails && isImageFile(file)) 
            {
                // Keep thumbnail as ImageView
                ImageView imageView = new ImageView();
                loadThumbnail(file, imageView);
                imageView.setFitWidth(80);
                imageView.setFitHeight(80);
                iconNode = imageView;
            } 
            else 
            {
                // Use utility with size 64
                iconNode = FileIconUtil.getFileIcon(file, 64);
            }

            Label nameLabel = new Label(file.getName());
            nameLabel.setWrapText(true);
            nameLabel.setMaxWidth(100);
            nameLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            card.getChildren().addAll(iconNode, nameLabel);
            card.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) openFile(file);
            });
            iconFlowPane.getChildren().add(card);
        }
    }

    private boolean isImageFile(File file) 
    {
        String ext = getFileExtension(file);
        return ext != null && (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("gif") || ext.equals("bmp") || ext.equals("tiff"));
    }

    private String getFileExtension(File file) 
    {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1).toLowerCase() : null;
    }

    private void loadThumbnail(File file, ImageView target) 
    {
        Task<Image> task = new Task<>() {
            @Override
            protected Image call() throws Exception 
            {
                return new Image(file.toURI().toString(), 80, 80, true, true);
            }
        };
        task.setOnSucceeded(e -> target.setImage(task.getValue()));
        new Thread(task).start();
    }

    private void openFile(File file) 
    {
        if (file.isDirectory()) 
        {
            navigateTo(file);
        } 
        else 
        {
            try 
            {
                Desktop.getDesktop().open(file);
            } 
            catch (IOException ex) 
            {
                ErrorHandler.show(null, "Could not open file", ex);
            }
        }
    }
}