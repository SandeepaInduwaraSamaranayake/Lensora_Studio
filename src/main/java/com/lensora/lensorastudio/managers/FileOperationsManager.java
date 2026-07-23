package com.lensora.lensorastudio.managers;

import com.lensora.lensorastudio.services.MetadataExtractionService;
import com.lensora.lensorastudio.util.ClipboardFormats;
import com.lensora.lensorastudio.util.Dialogs;
import com.lensora.lensorastudio.util.ErrorHandler;
import com.lensora.lensorastudio.util.FileSizeFormatter;
import com.lensora.lensorastudio.util.MetadataPanel;


import javafx.beans.binding.Bindings;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

    private final MenuItem ctxFileOpen, ctxFileRename, ctxFileCopy, ctxFileCut, ctxFileMove, ctxFileDelete, ctxFileShowInExplorer, ctxFileProperties;
    private final HBox progressContainer;
    private final ProgressBar progressBar;
    private final Label progressLabel, progressSpeedLabel, progressEtaLabel;

    private final Supplier<File> selectedFileSupplier;
    private final Supplier<List<File>> selectedFilesSupplier;
    private final Runnable refreshCallback;

    private Stage ownerStage;
    private FileCopyTask currentCopyTask;
    private SnapFX snapFX;
    private Consumer<File> showMetadataHandler;

    public FileOperationsManager(MenuItem ctxFileOpen, MenuItem ctxFileRename, MenuItem ctxFileCopy, MenuItem ctxFileCut,
                                MenuItem ctxFileMove, MenuItem ctxFileDelete, MenuItem ctxFileShowInExplorer, MenuItem ctxFileProperties,
                                HBox progressContainer, ProgressBar progressBar,
                                Label progressLabel, Label progressSpeedLabel, Label progressEtaLabel,
                                Supplier<File> selectedFileSupplier,  Supplier<List<File>> selectedFilesSupplier, Runnable refreshCallback)
    {
        this.ctxFileOpen = ctxFileOpen;
        this.ctxFileRename = ctxFileRename;
        this.ctxFileCopy = ctxFileCopy;
        this.ctxFileCut = ctxFileCut;
        this.ctxFileMove = ctxFileMove;
        this.ctxFileDelete = ctxFileDelete;
        this.ctxFileShowInExplorer = ctxFileShowInExplorer;
        this.ctxFileProperties = ctxFileProperties;
        this.progressContainer = progressContainer;
        this.progressBar = progressBar;
        this.progressLabel = progressLabel;
        this.progressSpeedLabel = progressSpeedLabel;
        this.progressEtaLabel = progressEtaLabel;
        this.selectedFileSupplier = selectedFileSupplier;
        this.selectedFilesSupplier = selectedFilesSupplier;
        this.refreshCallback = refreshCallback;

        setupContextMenu();
    }

    public void setStage(Stage stage)                               { this.ownerStage = stage; }
    public void setSnapFX(SnapFX snapFX)                            { this.snapFX = snapFX; }
    public void setShowMetadataHandler(Consumer<File> handler)      { this.showMetadataHandler = handler;}


    // ─── Context menu ───────────────────────────────────────────────────────

    private void setupContextMenu()
    {
        ctxFileOpen.setOnAction(e -> openSelectedFile());
        ctxFileRename.setOnAction(e -> renameSelectedFile());
        ctxFileCopy.setOnAction(e -> copySelectedFiles());
        ctxFileCut.setOnAction(e -> cutSelectedFiles());
        ctxFileMove.setOnAction(e -> moveSelectedFile());
        ctxFileDelete.setOnAction(e -> deleteSelectedFile());
        ctxFileShowInExplorer.setOnAction(e -> showInExplorer());
        ctxFileProperties.setOnAction(e -> showMetadata());
    }

    private void openSelectedFile()
    {
        File file = selectedFileSupplier.get();
        if (file == null) return;
        try { Desktop.getDesktop().open(file); }
        catch (IOException ex) { ErrorHandler.show(null, "Could not open file", ex); }
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
            if (newFile.exists()) { Dialogs.showInfo(null, "Rename", null, "File already exists."); return; }
            if (file.renameTo(newFile)) refreshCallback.run();
            else Dialogs.showInfo(null, "Rename", null, "Failed to rename file.");
        });
    }

    private void moveSelectedFile()
    {
        File file = selectedFileSupplier.get();
        if (file == null) return;
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setInitialDirectory(file.getParentFile());
        chooser.setTitle("Select Destination Folder");
        File destDir = chooser.showDialog(ownerStage);
        if (destDir == null) return;
        try
        {
            Files.move(file.toPath(), new File(destDir, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            refreshCallback.run();
        }
        catch (IOException ex) { ErrorHandler.show(null, "Move failed", ex); }
    }

    private void deleteSelectedFile()
    {
        File file = selectedFileSupplier.get();
        if (file == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete File");
        confirm.setContentText("Are you sure you want to delete " + file.getName() + "?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK)
            {
                if (file.delete()) refreshCallback.run();
                else Dialogs.showInfo(null, "Delete", null, "Failed to delete file.");
            }
        });
    }

    private void showInExplorer()
    {
        File file = selectedFileSupplier.get();
        if (file == null) return;

        if (!Desktop.isDesktopSupported())
        {
            Dialogs.showInfo(null, "Not Supported", null, "Desktop API is not supported.");
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
            Dialogs.showInfo(null, "Not Supported", null, "Cannot open file browser.");
        }
        catch (Exception e) { ErrorHandler.show(null, "Could not open in explorer", e); }
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
        if (files == null || files.isEmpty()) { Dialogs.showInfo(null, actionLabel, null, "No files selected."); return;}
        ClipboardContent content = new ClipboardContent();
        content.put(DataFormat.FILES, files);
        content.put(ClipboardFormats.CUT, cut);
        Clipboard.getSystemClipboard().setContent(content);

        Dialogs.showInfo(null, actionLabel, null, files.size() + " file(s) " + actionLabel.toLowerCase() + ".");
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
            Dialogs.showInfo(null, "Paste", null, "Please select a valid folder.");
            return;
        }

        final List<File> sourceFiles = readFilesFromClipboard();
        if (sourceFiles.isEmpty())
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
            Dialogs.showInfo(null, "Drop", null, "Please drop onto a valid folder.");
            return;
        }

        for (File src : files)
        {
            if (isRecursivePaste(src, targetFolder))
            {
                Dialogs.showInfo(null, "Drop", null, "Cannot move/copy a folder into itself or its subfolder.");
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
}