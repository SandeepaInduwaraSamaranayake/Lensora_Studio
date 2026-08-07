package com.lensora.lensorastudio.managers;

import com.lensora.lensorastudio.model.ExternalApp;
import com.lensora.lensorastudio.services.AppSettings;
import com.lensora.lensorastudio.services.MetadataExtractionService;
import com.lensora.lensorastudio.util.ClipboardFormats;
import com.lensora.lensorastudio.util.EmailSendUtil;
import com.lensora.lensorastudio.util.ErrorHandler;
import com.lensora.lensorastudio.util.ExternalAppLauncher;
import com.lensora.lensorastudio.util.ExternalAppsDialog;
import com.lensora.lensorastudio.util.FileSizeFormatter;
import com.lensora.lensorastudio.util.ImageMetadataExtractor;
import com.lensora.lensorastudio.util.MetadataPanel;
import com.lensora.lensorastudio.util.NotificationUtil;
import com.lensora.lensorastudio.util.RemovableDriveUtil;
import com.lensora.lensorastudio.viewer.ImageViewerWindowService;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javafx.application.Platform;
import java.util.concurrent.CompletableFuture;

import org.kordamp.ikonli.javafx.FontIcon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snapfx.SnapFX;

/**
 * Owns file-level operations: the file table's context menu (open/rename/
 * copy/move/delete/show-in-explorer), and clipboard copy-paste of files or
 * folders with a progress-bound FileCopyTask.
 */
public class FileOperationsManager
{
    private static final Logger logger = LoggerFactory.getLogger(FileOperationsManager.class);

    private final MenuItem ctxFileOpen, ctxFileRename, ctxFileCopy, ctxFileCut, ctxFileMove, ctxFileDelete, ctxFileShowInExplorer, ctxFileProperties, ctxOpenInImageViewer;
    private final HBox progressContainer;
    private final ProgressBar progressBar;
    private final Label progressLabel, progressSpeedLabel, progressEtaLabel;

    private final Menu ctxOpenWithMenu, ctxSendToMenu;

    private final Supplier<File> selectedFileSupplier;
    private final Supplier<List<File>> selectedFilesSupplier;
    private final Runnable refreshCallback;

    private Stage ownerStage;
    private BooleanBinding multiSelectBinding;
    private FileCopyTask currentCopyTask;
    private SnapFX snapFX;
    private Consumer<File> showMetadataHandler;

    public FileOperationsManager(   MenuItem ctxFileOpen, 
                                    Menu ctxOpenWithMenu, 
                                    MenuItem ctxFileRename, 
                                    MenuItem ctxFileCopy, 
                                    MenuItem ctxFileCut,
                                    MenuItem ctxFileMove,
                                    Menu ctxSendToMenu,
                                    MenuItem ctxFileDelete, 
                                    MenuItem ctxFileShowInExplorer, 
                                    MenuItem ctxFileProperties, 
                                    MenuItem ctxOpenInImageViewer,
                                    HBox progressContainer, 
                                    ProgressBar progressBar,
                                    Label progressLabel, 
                                    Label progressSpeedLabel, 
                                    Label progressEtaLabel,
                                    Supplier<File> selectedFileSupplier,  
                                    Supplier<List<File>> selectedFilesSupplier, 
                                    Runnable refreshCallback, 
                                    BooleanBinding multiSelectBinding
                                )
    {
        this.ctxFileOpen = ctxFileOpen;
        this.ctxOpenWithMenu = ctxOpenWithMenu;
        this.ctxFileRename = ctxFileRename;
        this.ctxFileCopy = ctxFileCopy;
        this.ctxFileCut = ctxFileCut;
        this.ctxFileMove = ctxFileMove;
        this.ctxSendToMenu = ctxSendToMenu;
        this.ctxFileDelete = ctxFileDelete;
        this.ctxFileShowInExplorer = ctxFileShowInExplorer;
        this.ctxFileProperties = ctxFileProperties;
        this.ctxOpenInImageViewer = ctxOpenInImageViewer;
        this.progressContainer = progressContainer;
        this.progressBar = progressBar;
        this.progressLabel = progressLabel;
        this.progressSpeedLabel = progressSpeedLabel;
        this.progressEtaLabel = progressEtaLabel;
        this.selectedFileSupplier = selectedFileSupplier;
        this.selectedFilesSupplier = selectedFilesSupplier;
        this.refreshCallback = refreshCallback;
        this.multiSelectBinding = multiSelectBinding;

        setupContextMenu();
    }

    public void setStage(Stage stage)                               { this.ownerStage = stage; }
    public void setSnapFX(SnapFX snapFX)                            { this.snapFX = snapFX; }
    public void setShowMetadataHandler(Consumer<File> handler)      { this.showMetadataHandler = handler; }


    // ─── Context menu ───────────────────────────────────────────────────────

    private void setupContextMenu()
    {
        // Disable menuitems based on selcted file count
        ctxFileRename.disableProperty().bind(multiSelectBinding);
        ctxFileShowInExplorer.disableProperty().bind(multiSelectBinding);
        ctxFileProperties.disableProperty().bind(multiSelectBinding);

        // setup actions
        ctxFileOpen.setOnAction(e -> openSelectedFiles());
        ctxFileRename.setOnAction(e -> renameSelectedFile());
        ctxFileCopy.setOnAction(e -> copySelectedFiles());
        ctxFileCut.setOnAction(e -> cutSelectedFiles());
        ctxFileMove.setOnAction(e -> moveSelectedFiles());
        ctxFileDelete.setOnAction(e -> deleteSelectedFiles());
        ctxFileShowInExplorer.setOnAction(e -> showInExplorer());
        ctxFileProperties.setOnAction(e -> showMetadata());
        ctxOpenInImageViewer.setOnAction(e -> openInImageViewer());

        ContextMenu popup = ctxOpenWithMenu.getParentPopup();
        popup.addEventHandler(WindowEvent.WINDOW_SHOWING, e -> {
            rebuildOpenWithMenu();
            rebuildSendToMenu();
        });

    }

    private void openSelectedFiles()
    {
        List<File> files = selectedFilesSupplier.get();
        if (files.isEmpty()) return;
        // Run OS file launches in the background
        CompletableFuture.runAsync(() -> {
            for (File file : files) 
            {
                try { Desktop.getDesktop().open(file); }
                catch (IOException ex) 
                {
                    Platform.runLater(() ->
                        ErrorHandler.show(null, "Could not open file", ex)
                    );
                }
            }
        });
    }

    private void renameSelectedFile()
    {
        File file = selectedFileSupplier.get();
        if (file == null) return;
        TextInputDialog dialog = new TextInputDialog(file.getName());
        dialog.setTitle("Rename File");
        dialog.setHeaderText(null);
        dialog.setContentText("New name:");
        dialog.showAndWait().ifPresent(newName -> {
            if (newName == null || newName.trim().isEmpty()) return;
            File newFile = new File(file.getParentFile(), newName);
            if (newFile.exists()) { NotificationUtil.showToast(ownerStage, "File already exists", "fas-exclamation-circle"); return; }
            if (file.renameTo(newFile)) refreshCallback.run();
            else NotificationUtil.showToast(ownerStage, "Failed to rename file", "fas-exclamation-circle");
        });
    }

    private void moveSelectedFiles()
    {
        List<File> files = selectedFilesSupplier.get();
        if (files.isEmpty()) return;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Destination Folder");
        File initialDir = files.get(0).getParentFile();
        if (initialDir != null && initialDir.isDirectory()) chooser.setInitialDirectory(initialDir);
        File destDir = chooser.showDialog(ownerStage);
        if (destDir == null) return;

        int movedCount = 0;

        for (File file : files) 
        {
            try
            {
                if (file.getParentFile().equals(destDir)) continue;
                Files.move(
                    file.toPath(), 
                    destDir.toPath().resolve(file.getName()),
                    StandardCopyOption.REPLACE_EXISTING
                );
                movedCount++;
            }
            catch (IOException ex) 
            { 
                ErrorHandler.show(null, "Move failed for " + file.getName(), ex); 
                return;
            }
        }
        if(movedCount > 0)
        {
            refreshCallback.run();
            // Success Notification
            String message = movedCount == 1 
                    ? "File moved successfully" 
                    : movedCount + " files moved successfully";
            NotificationUtil.showToast(ownerStage, message);
        }
    }

    private void deleteSelectedFiles()
    {
        List<File> files = selectedFilesSupplier.get();
        if (files.isEmpty()) return;
        // Build a confirmation message
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                String fileList = files.stream()
                .map(File::getName)
                .collect(Collectors.joining("\n• ", "• ", ""));
        confirm.setTitle("Delete File");
        confirm.setHeaderText("Are you sure you want to delete the following " + files.size() + " files?");
        confirm.setContentText(fileList);
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;
            for (File file : files) 
            {
                if (!file.delete())
                {
                    NotificationUtil.showToast(ownerStage, "Failed to delete " + file.getName(), "fas-exclamation-circle");
                }
            }
            refreshCallback.run();
        });
    }

    private void showInExplorer()
    {
        File file = selectedFileSupplier.get();
        if (file == null) return;

        // Offload native OS calls to a background thread to prevent UI freezing on Linux
        CompletableFuture.runAsync(() -> {
            if (!Desktop.isDesktopSupported())
            {
                Platform.runLater(() -> 
                    NotificationUtil.showToast(ownerStage, "Desktop API is not supported", "fas-exclamation-circle")
                );
                return;
            }

            try
            {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR))
                {
                    desktop.browseFileDirectory(file);
                    return;
                }

                // Fallback: open the parent folder
                File parent = file.getParentFile();
                if (parent != null && parent.exists() && desktop.isSupported(Desktop.Action.OPEN)) 
                {
                    desktop.open(parent);
                    return;
                }

                Platform.runLater(() -> 
                    NotificationUtil.showToast(progressContainer, "Not supported. Cannot open file browser", "fas-exclamation-circle")
                );
            }
            catch (Exception e) 
            { 
                Platform.runLater(() -> 
                    ErrorHandler.show(null, "Could not open in explorer", e)
                ); 
            }
        });
    }

    private void showMetadata() 
    {
        File file = selectedFileSupplier.get();
        if (file == null) return;

        if (showMetadataHandler != null) 
        {
            showMetadataHandler.accept(file);
        } 
        else 
        {
            // Fallback to floating (if no handler set)
            MetadataExtractionService.extractAsync(
                file,
                metadata -> MetadataPanel.showFloating(metadata, snapFX),
                error -> ErrorHandler.show(null, "Failed to read metadata", error)
            );
        }
    }

    private void openInImageViewer()
    {
        List<File> selected = selectedFilesSupplier.get();
        if (selected == null || selected.isEmpty()) return;

        List<File> images = selected.stream()
                .filter(ImageMetadataExtractor::isSupportedImage)
                .toList();

        if (images.isEmpty())
        {
            NotificationUtil.showToast(ownerStage, "No supported image files in selection", "fas-exclamation-circle");
            return;
        }

        ImageViewerWindowService.getInstance().openImages(images);
    }

    // ─── Clipboard copy/paste ───────────────────────────────────────────────

    private void copySelectedFiles()
    {
        copyFilesToClipboard(selectedFilesSupplier.get());
    }

    private void cutSelectedFiles()
    {
        cutFilesToClipboard(selectedFilesSupplier.get());
    }

    public void copyFilesToClipboard(List<File> files)
    {
        putFilesOnClipboard(files, "Copy", false);
    }

    public void cutFilesToClipboard(List<File> files)
    {
        putFilesOnClipboard(files, "Cut", true);
    }

    private void putFilesOnClipboard(List<File> files, String actionLabel, boolean cut)
    {
        if (files == null || files.isEmpty()) 
        { 
            NotificationUtil.showToast(ownerStage, "No files selected", "fas-exclamation-circle");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.put(DataFormat.FILES, files);
        content.put(ClipboardFormats.CUT, cut);
        Clipboard.getSystemClipboard().setContent(content);

        NotificationUtil.showToast(ownerStage, files.size() + " file(s) " + actionLabel.toLowerCase() + "d");
    }

    /** Reads whichever file-list format is present on the clipboard, or an empty list if none. */
    @SuppressWarnings("unchecked")
    private List<File> readFilesFromClipboard()
    {
        Clipboard clipboard = Clipboard.getSystemClipboard();

        Object standard = clipboard.getContent(DataFormat.FILES);
        if (standard instanceof List<?> list) return (List<File>) list;

        return Collections.emptyList();
    }

    private boolean isClipboardMarkedAsCut()
    {
        Object cutFlag = Clipboard.getSystemClipboard().getContent(ClipboardFormats.CUT);
        return Boolean.TRUE.equals(cutFlag);
    }

    private void clearClipboardCutFlag()
    {
        ClipboardContent cleared = new ClipboardContent();
        cleared.put(ClipboardFormats.CUT, false);
        Clipboard.getSystemClipboard().setContent(cleared);
    }

    /** Pastes whatever is on the clipboard into targetFolder, with a progress-bound background copy. */
    public void pasteInto(File targetFolder)
    {
        if (targetFolder == null || !targetFolder.isDirectory())
        {
            NotificationUtil.showToast(ownerStage, "Please select a valid folder", "fas-exclamation-circle");
            return;
        }

        final List<File> sourceFiles = readFilesFromClipboard();
        if (sourceFiles.isEmpty())
        {
            NotificationUtil.showToast(ownerStage, "Clipboard does not contain any files/folders", "fas-exclamation-circle");
            return;
        }

        for (File src : sourceFiles)
        {
            if (isRecursivePaste(src, targetFolder))
            {
                NotificationUtil.showToast(ownerStage, "Cannot paste a folder into itself or its subfolder", "fas-exclamation-circle");
                return;
            }
        }

        final boolean isCut = isClipboardMarkedAsCut();

        currentCopyTask = new FileCopyTask(sourceFiles, targetFolder);
        bindProgress(currentCopyTask);

        currentCopyTask.setOnSucceeded(e -> onPasteSucceeded(sourceFiles, isCut));
        currentCopyTask.setOnFailed(e -> {
            hideProgress();
            ErrorHandler.show(null, "Paste failed", currentCopyTask.getException());
        });
        currentCopyTask.setOnCancelled(e -> hideProgress());

        showProgress();
        Thread thread = new Thread(currentCopyTask, "Lensora-file-copy-task");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Handles files dropped onto a folder — either from within the app
     * (move) or from the OS (copy). Reuses the same progress-bound
     * FileCopyTask pipeline as clipboard paste.
     */
    public void dropFilesInto(List<File> files, File targetFolder, boolean move)
    {
        if (files == null || files.isEmpty()) return;

        if (targetFolder == null || !targetFolder.isDirectory())
        {
            NotificationUtil.showToast(ownerStage, "Please drop onto a valid folder", "fas-exclamation-circle");
            return;
        }

        for (File src : files)
        {
            if (isRecursivePaste(src, targetFolder))
            {
                NotificationUtil.showToast(ownerStage, "Cannot move/copy a folder into itself or its subfolder", "fas-exclamation-circle");
                return;
            }
            if (src.getParentFile() != null && src.getParentFile().equals(targetFolder))
            {
                // Already in this folder — nothing to do for this entry.
                continue;
            }
        }

        currentCopyTask = new FileCopyTask(files, targetFolder);
        bindProgress(currentCopyTask);

        currentCopyTask.setOnSucceeded(e -> {
            hideProgress();
            if (move)
            {
                for (File src : files)
                {
                    if (src.getParentFile() == null || !src.getParentFile().equals(targetFolder))
                    {
                        deleteRecursive(src);
                    }
                }
            }
            refreshCallback.run();
        });
        currentCopyTask.setOnFailed(e -> {
            hideProgress();
            ErrorHandler.show(null, "Drop failed", currentCopyTask.getException());
        });
        currentCopyTask.setOnCancelled(e -> hideProgress());

        showProgress();
        Thread thread = new Thread(currentCopyTask, "Lensora-file-drop-task");
        thread.setDaemon(true);
        thread.start();
    }

    private void onPasteSucceeded(List<File> sourceFiles, boolean isCut)
    {
        hideProgress();

        if (isCut)
        {
            for (File src : sourceFiles) { deleteRecursive(src); }
            clearClipboardCutFlag();
        }

        refreshCallback.run();
    }

    private void bindProgress(FileCopyTask task)
    {
        progressBar.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(Bindings.createStringBinding(
                () -> task.getProgress() < 0 ? "…" : String.format("%.0f%%", task.getProgress() * 100),
                task.progressProperty()));
        progressSpeedLabel.textProperty().bind(Bindings.createStringBinding(
                () -> FileSizeFormatter.formatSpeed(task.speedProperty().get()),
                task.speedProperty()));
        progressEtaLabel.textProperty().bind(Bindings.createStringBinding(
                () -> FileSizeFormatter.formatEta(task.etaProperty().get()),
                task.etaProperty()));
    }

    private void deleteRecursive(File file) 
    {
        if (file.isDirectory()) 
        {
            File[] children = file.listFiles();
            if (children != null) 
            {
                for (File child : children) 
                {
                    deleteRecursive(child);
                }
            }
        }
        if (!file.delete()) 
        {
            logger.warn("Failed to delete: {}", file.getAbsolutePath());
        }
    }

    private boolean isRecursivePaste(File source, File target)
    {
        if (!source.isDirectory()) return false;
        if (source.equals(target)) return true;

        String srcPath = source.getAbsolutePath();
        String tgtPath = target.getAbsolutePath();

        if (tgtPath.startsWith(srcPath) && !tgtPath.equals(srcPath)) return true;
        return srcPath.startsWith(tgtPath) && !srcPath.equals(tgtPath);
    }

    private void showProgress()
    {
        progressContainer.setVisible(true);
        progressContainer.setManaged(true);
    }

    private void hideProgress()
    {
        progressContainer.setVisible(false);
        progressContainer.setManaged(false);
        progressBar.progressProperty().unbind();
        progressLabel.textProperty().unbind();
        progressSpeedLabel.textProperty().unbind();
        progressEtaLabel.textProperty().unbind();
        progressBar.setProgress(0);
        progressLabel.setText("0%");
        progressSpeedLabel.setText("0 B/s");
        progressEtaLabel.setText("ETA: --");
    }

    /** Rebuilds the Open With submenu each time it's about to show, so newly
     *  configured apps (added via the manage dialog) appear without a restart. */
    private void rebuildOpenWithMenu()
    {
        logger.info("rebuildOpenWithMenu() called");
        ctxOpenWithMenu.getItems().clear();

        List<File> selected = selectedFilesSupplier.get();
        boolean hasSelection = selected != null && !selected.isEmpty();

        List<ExternalApp> configuredApps = AppSettings.getInstance().getExternalApps();

        if (configuredApps.isEmpty())
        {
            MenuItem noneItem = new MenuItem("(No applications configured)");
            noneItem.setDisable(true);
            ctxOpenWithMenu.getItems().add(noneItem);
        }
        else
        {
            for (ExternalApp app : configuredApps)
            {
                MenuItem item = new MenuItem(app.getName()
                        + (selected != null && selected.size() > 1 ? " (" + selected.size() + " files)" : ""));
                item.setDisable(!hasSelection);
                item.setOnAction(e -> ExternalAppLauncher.openWith(app, selected));
                ctxOpenWithMenu.getItems().add(item);
            }
        }

        ctxOpenWithMenu.getItems().add(new SeparatorMenuItem());

        MenuItem nativePickerItem = new MenuItem("Choose Application…");
        nativePickerItem.setDisable(!hasSelection || selected.size() != 1);
        nativePickerItem.setOnAction(e -> {
            if (selected != null && selected.size() == 1)
            {
                ExternalAppLauncher.showNativeOpenWithDialog(selected.get(0));
            }
        });
        ctxOpenWithMenu.getItems().add(nativePickerItem);

        MenuItem manageAppsItem = new MenuItem("Manage Applications…");
        manageAppsItem.setOnAction(e -> ExternalAppsDialog.show(ownerStage));
        ctxOpenWithMenu.getItems().add(manageAppsItem);
    }

    /** Rebuilds Send To fresh each time it's shown, so newly attached drives appear without a restart. */
    private void rebuildSendToMenu()
    {
        ctxSendToMenu.getItems().clear();

        List<File> selected = selectedFilesSupplier.get();
        boolean hasSelection = selected != null && !selected.isEmpty();

        List<RemovableDriveUtil.DriveInfo> drives = RemovableDriveUtil.listRemovableDrives();

        if (drives.isEmpty())
        {
            MenuItem noneItem = new MenuItem("(No external drives detected)");
            noneItem.setDisable(true);
            ctxSendToMenu.getItems().add(noneItem);
        }
        else
        {
            for (var drive : drives)
            {
                String freeSpace = FileSizeFormatter.formatFileSize(drive.usableBytes());
                String title = drive.label() + " (" + freeSpace + " free)";
                
                // Create cascading menu for each drive
                Menu driveMenu = createDirectoryMenu(drive.rootPath().toFile(), title, hasSelection);
                ctxSendToMenu.getItems().add(driveMenu);
            }
        }

        ctxSendToMenu.getItems().add(new SeparatorMenuItem());

        MenuItem emailItem = new MenuItem("Email");
        emailItem.setDisable(!hasSelection);
        emailItem.setOnAction(e -> EmailSendUtil.sendFiles(selected, progressContainer));
        ctxSendToMenu.getItems().add(emailItem);
    }

    /**
     * Lazily creates a folder Menu with cascading subdirectories loaded on hover.
     */
    private Menu createDirectoryMenu(File directory, String menuTitle, boolean hasSelection)
    {
        Menu folderMenu = new Menu(menuTitle);
        folderMenu.setDisable(!hasSelection);

        // Dummy item to render the expand arrow (>) in JavaFX
        MenuItem dummyItem = new MenuItem("Loading…");
        dummyItem.setDisable(true);
        folderMenu.getItems().add(dummyItem);

        // Lazy-load contents when hovering over the menu item
        folderMenu.setOnShowing(e -> {
            folderMenu.getItems().clear();

            // Option 1: Direct copy action to THIS current directory
            MenuItem copyHereItem = new MenuItem("Copy directly to this folder");
            copyHereItem.setGraphic(new FontIcon("fas-copy"));
            copyHereItem.setOnAction(ev -> sendFilesToDrive(selectedFilesSupplier.get(), directory));
            folderMenu.getItems().add(copyHereItem);

            // Option 2: List subdirectories as further submenus
            List<File> subdirs = RemovableDriveUtil.listSubdirectories(directory);
            if (!subdirs.isEmpty())
            {
                folderMenu.getItems().add(new SeparatorMenuItem());
                for (File subdir : subdirs)
                {
                    folderMenu.getItems().add(createDirectoryMenu(subdir, subdir.getName(), hasSelection));
                }
            }
        });

        return folderMenu;
    }

    /** Copies the selected files to a drive's root, reusing the same progress-bound copy pipeline as paste/drag-drop. */
    private void sendFilesToDrive(List<File> files, File targetRoot)
    {
        if (files == null || files.isEmpty() || targetRoot == null || !targetRoot.exists())
        {
            NotificationUtil.showToast(ownerStage, "Selected drive is not online. Aborting...", "fas-exclamation-circle");
            return;
        }

        for (File src : files)
        {
            if (isRecursivePaste(src, targetRoot))
            {
                NotificationUtil.showToast(ownerStage, "Cannot send a folder into itself or its subfolder", "fas-exclamation-circle");
                return;
            }
        }

        currentCopyTask = new FileCopyTask(files, targetRoot);
        bindProgress(currentCopyTask);

        currentCopyTask.setOnSucceeded(e -> {
            hideProgress();
            NotificationUtil.showToast(ownerStage, files.size() + " file(s) sent to " + targetRoot.getPath());
        });
        currentCopyTask.setOnFailed(e -> {
            hideProgress();
            ErrorHandler.show(null, "Send To operation failed", currentCopyTask.getException());
        });
        currentCopyTask.setOnCancelled(e -> hideProgress());

        showProgress();
        Thread thread = new Thread(currentCopyTask, "send-to-drive-task");
        thread.setDaemon(true);
        thread.start();
    }
}