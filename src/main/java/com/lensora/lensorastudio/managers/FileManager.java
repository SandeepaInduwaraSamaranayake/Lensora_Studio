package com.lensora.lensorastudio.managers;

import com.lensora.lensorastudio.util.Dialogs;
import com.lensora.lensorastudio.util.ErrorHandler;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FileManager 
{
    private static final Logger logger = LoggerFactory.getLogger(FileManager.class);

    private final TreeView<File> folderTree;
    private final TableView<File> fileTable;
    private final TableColumn<File, String> colFileName, colFileType, colFileSize, colFileModified;
    private final Label lblCurrentFolder, lblFileCount, lblFolderHeader;
    private final HBox progressContainer;
    private final ProgressBar progressBar;
    private final Label progressLabel, progressSpeedLabel, progressEtaLabel;
    private final MenuItem ctxFileOpen, ctxFileRename, ctxFileCopy, ctxFileMove, ctxFileDelete, ctxFileShowInExplorer;
    private Stage ownerStage;

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
                        MenuItem ctxFileShowInExplorer) 
    {
        this.folderTree = folderTree;
        this.fileTable = fileTable;
        this.colFileName = colFileName;
        this.colFileType = colFileType;
        this.colFileSize = colFileSize;
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


        setupFileTableColumns();
        setupFileContextMenu();
        setupFolderTree();
        setupFolderContextMenu();
        setupFolderTreeListener();
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
        
        colFileModified.setCellValueFactory(cellData ->
            new SimpleStringProperty(
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(cellData.getValue().lastModified()),
                    ZoneId.systemDefault()
                ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            )
        );
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
            if (newVal != null && newVal.getValue() != null && newVal.getValue().isDirectory()) 
            {
                loadFileTable(newVal.getValue());
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

    public void loadProjectPath(String path)
    {
        if (path == null || path.isEmpty()) 
        {
            logger.warn("[FileManager] Project path is null or empty - clearing tree.");
            folderTree.setRoot(null);
            return;
        }

        File rootDir = new File(path);
        if (!rootDir.exists() || !rootDir.isDirectory()) 
        {
            logger.warn("[FileManager] Project path does not exist or not a directory: {}", path);
            folderTree.setRoot(null);
            return;
        }

        TreeItem<File> rootItem = new TreeItem<>(rootDir);
        rootItem.setExpanded(true);
        addChildren(rootItem);
        folderTree.setRoot(rootItem);
        folderTree.setShowRoot(false);
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
}
