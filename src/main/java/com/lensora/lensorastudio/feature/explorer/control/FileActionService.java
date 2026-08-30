package com.lensora.lensorastudio.feature.explorer.control;

import com.lensora.lensorastudio.core.io.InstrumentedFileIO;
import com.lensora.lensorastudio.core.threading.BackgroundExecutor;
import com.lensora.lensorastudio.feature.viewer.ImageViewerWindowService;
import com.lensora.lensorastudio.media.service.ImageValidator;
import com.lensora.lensorastudio.media.service.MetadataExtractionService;
import com.lensora.lensorastudio.ui.components.MetadataPanel;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;
import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import org.snapfx.SnapFX;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Executes core file actions (Open, Rename, Move, Delete, Show in Explorer,
 * Show Metadata) in the background or via UI dialogs.
 */
public class FileActionService 
{
    private final Supplier<File> selectedFileSupplier;
    private final Supplier<List<File>> selectedFilesSupplier;
    private final Consumer<File> refreshCallback;
    private final Supplier<File> watchRootSupplier;
    private Stage ownerStage;
    private SnapFX snapFX;
    private Consumer<File> showMetadataHandler;
    private final InstrumentedFileIO fileIO;

    public FileActionService(   Supplier<File> selectedFileSupplier,
                                Supplier<List<File>> selectedFilesSupplier,
                                Consumer<File> refreshCallback,
                                Supplier<File> watchRootSupplier,
                                InstrumentedFileIO fileIO) 
    {
        this.selectedFileSupplier = selectedFileSupplier;
        this.selectedFilesSupplier = selectedFilesSupplier;
        this.refreshCallback = refreshCallback;
        this.watchRootSupplier = watchRootSupplier;
        this.fileIO = fileIO;
    }

    public void setSnapFX(SnapFX snapFX) { this.snapFX = snapFX; }
    public void setShowMetadataHandler(Consumer<File> handler) { this.showMetadataHandler = handler; }
    public void setOwnerStage(Stage stage) { this.ownerStage = stage; }

    /** Opens selected files with the system default application. */
    public void openSelectedFiles()
    {
        List<File> files = selectedFilesSupplier.get();
        if (files == null || files.isEmpty()) return;

        BackgroundExecutor.getInstance().executeIO(() -> {
            for (File file : files)
            {
                try
                {
                    Desktop.getDesktop().open(file);
                } 
                catch (IOException ex) 
                {
                    Platform.runLater(() -> ErrorHandler.show(ownerStage, "Could not open file", ex));
                }
            }
        });
    }

    /** Opens supported images in the internal image viewer. */
    public void openInImageViewer()
    {
        List<File> selected = selectedFilesSupplier.get();
        if (selected == null || selected.isEmpty()) return;

        List<File> images = selected.stream()
                                .filter(ImageValidator::isJavaFXLoadable)
                                .toList();

        if (images.isEmpty())
        {
            NotificationUtil.showToast(ownerStage, "No supported image files in selection", "fas-exclamation-circle");
            return;
        }

        ImageViewerWindowService.getInstance().openImages(images);
    }

    /** Renames a single selected file. */
    public void renameSelectedFile()
    {
        File file = selectedFileSupplier.get();
        if (file == null) return;

        TextInputDialog dialog = new TextInputDialog(file.getName());
        dialog.setTitle("Rename File");
        dialog.setHeaderText(null);
        dialog.setContentText("New name:");
        dialog.showAndWait().ifPresent(newName -> {
            if (newName == null || newName.trim().isEmpty()) return;

            try
            {
                fileIO.rename(file, newName);
            }
            catch (FileAlreadyExistsException ex)
            {
                NotificationUtil.showToast(ownerStage, ex.getMessage(), "fas-exclamation-circle");
            }
            catch (IllegalArgumentException ex)
            {
                NotificationUtil.showToast(ownerStage, ex.getMessage(), "fas-exclamation-circle");
            }
            catch (IOException ex)
            {
                ErrorHandler.show(ownerStage, "Failed to rename file", ex);
            }
        });
    }

    /** Moves selected files to a user-chosen directory. */
    public void moveSelectedFiles() 
    {
        List<File> files = selectedFilesSupplier.get();
        if (files == null || files.isEmpty()) return;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Destination Folder");
        File initialDir = files.get(0).getParentFile();
        if (initialDir != null && initialDir.isDirectory()) chooser.setInitialDirectory(initialDir);
        File destDir = chooser.showDialog(ownerStage);
        if (destDir == null) return;

        BackgroundExecutor.getInstance().executeIO(() -> {
            InstrumentedFileIO.BatchResult result = fileIO.moveBatch(files, destDir);
            Platform.runLater(() -> {
                if (result.succeeded() > 0)
                {
                    String message = result.succeeded() == 1 ? "File moved successfully" : result.succeeded() + " files moved successfully";
                    NotificationUtil.showToast(ownerStage, message);
                }

                if (!result.failedFiles().isEmpty())
                {
                    NotificationUtil.showToast(ownerStage, "Failed to move " + result.failedFiles().size() + " file(s)", "fas-exclamation-circle");
                }
            });
        });
    }

    /** Deletes selected files after confirmation. */
    public void deleteSelectedFiles()
    {
        List<File> files = selectedFilesSupplier.get();
        if (files == null || files.isEmpty()) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        String fileList = files.stream()
                            .map(File::getName)
                            .collect(Collectors.joining("\n• ", "• ", ""));
        confirm.setTitle("Delete File");
        confirm.setHeaderText("Are you sure you want to delete the following " + files.size() + " file(s)?");
        confirm.setContentText(fileList);
        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;

            BackgroundExecutor.getInstance().executeIO(() -> {
                for (File file : files) 
                {
                    try
                    {
                        fileIO.deleteRecursive(file, true, false);
                    }
                    catch (IOException ex)
                    {
                        NotificationUtil.showToast(ownerStage, "Failed to delete " + file.getName(), "fas-exclamation-circle");
                    }
                }
                Platform.runLater(() -> refreshCallback.accept(null));
            });
        });
    }

    /** Reveals the selected file in the system file explorer (browse file directory). */
    public void showInExplorer() 
    {
        File file = selectedFileSupplier.get();
        if (file == null || !file.exists()) return;

        BackgroundExecutor.getInstance().executeIO(() -> {
            if (!Desktop.isDesktopSupported()) 
            {
                Platform.runLater(() -> NotificationUtil.showToast(ownerStage, "Desktop API is not supported", "fas-exclamation-circle"));
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

                File parent = file.getParentFile();
                if (parent != null && parent.exists() && desktop.isSupported(Desktop.Action.OPEN)) 
                {
                    desktop.open(parent);
                    return;
                }
                Platform.runLater(() -> NotificationUtil.showToast(ownerStage, "Not supported. Cannot open file browser", "fas-exclamation-circle"));
            } 
            catch (Exception e) 
            {
                Platform.runLater(() -> ErrorHandler.show(null, "Could not open in explorer", e));
            }
        });
    }

    /** Shows metadata for the selected file, either via a custom handler or the default floating panel. */
    public void showMetadata() 
    {
        File file = selectedFileSupplier.get();
        if (file == null) return;

        if (showMetadataHandler != null) 
        {
            showMetadataHandler.accept(file);
        } 
        else
        {
            MetadataExtractionService.extractAsync(
                file,
                metadata -> MetadataPanel.showFloating(metadata, snapFX),
                error -> ErrorHandler.show(ownerStage, "Failed to read metadata", error)
            );
        }
    }
}