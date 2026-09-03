package com.lensora.lensorastudio.feature.explorer.control;

import com.lensora.lensorastudio.core.io.InstrumentedFileIO;
import com.lensora.lensorastudio.core.threading.BackgroundExecutor;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;
import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;
import com.lensora.lensorastudio.util.FileSizeFormatter;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Handles the transfer (copy/move) of files and folders, including progress
 * display and recursive cleanup for moves.
 */
public class FileTransferService 
{
    private static final Logger logger = LoggerFactory.getLogger(FileTransferService.class);

    private final HBox progressContainer;
    private final ProgressBar progressBar;
    private final Label progressLabel;
    private final Label progressSpeedLabel;
    private final Label progressEtaLabel;
    private Stage ownerStage;
    private final Consumer<File> refreshCallback;
    private final FileClipboardService clipboardService;
    private final Supplier<File> watchRootSupplier;
    private final InstrumentedFileIO fileIO;

    private FileCopyTask currentCopyTask;

    public FileTransferService( HBox progressContainer,
                                ProgressBar progressBar,
                                Label progressLabel,
                                Label progressSpeedLabel,
                                Label progressEtaLabel,
                                Consumer<File> refreshCallback,
                                FileClipboardService clipboardService,
                                Supplier<File> watchRootSupplier,
                                InstrumentedFileIO fileIO)
    {
        this.progressContainer = progressContainer;
        this.progressBar = progressBar;
        this.progressLabel = progressLabel;
        this.progressSpeedLabel = progressSpeedLabel;
        this.progressEtaLabel = progressEtaLabel;
        this.refreshCallback = refreshCallback;
        this.clipboardService = clipboardService;
        this.watchRootSupplier = watchRootSupplier;
        this.fileIO = fileIO;
    }

    /** Pastes files from the clipboard into the target folder. */
    public void pasteInto(File targetFolder, List<File> sourceFiles, boolean isCut)
    {
        if (targetFolder == null || !targetFolder.isDirectory()) 
        {
            NotificationUtil.showToast(ownerStage, "Please select a valid folder", "fas-exclamation-circle");
            return;
        }
        if (sourceFiles.isEmpty()) 
        {
            NotificationUtil.showToast(ownerStage, "Clipboard does not contain any files/folders", "fas-exclamation-circle");
            return;
        }

        for (File src : sourceFiles) 
        {
            if (clipboardService.isRecursivePaste(src, targetFolder)) 
            {
                NotificationUtil.showToast(ownerStage, "Cannot paste a folder into itself or its subfolder", "fas-exclamation-circle");
                return;
            }
        }

        startTransfer(sourceFiles, targetFolder, isCut);
    }

    /** Handles drop (from drag-and-drop) of files into a target folder, optionally moving. */
    public void dropFilesInto(List<File> files, File targetFolder, boolean move) 
    {
        if (files == null || files.isEmpty()) return;

        if (targetFolder == null || !targetFolder.isDirectory() || !targetFolder.exists()) 
        {
            NotificationUtil.showToast(ownerStage, "Please drop onto a valid folder", "fas-exclamation-circle");
            return;
        }

        for (File src : files)
        {
            if (clipboardService.isRecursivePaste(src, targetFolder))
            {
                NotificationUtil.showToast(ownerStage, "Cannot move/copy a folder into itself or its subfolder", "fas-exclamation-circle");
                return;
            }
            if (src.getParentFile() != null && src.getParentFile().equals(targetFolder)) 
            {
                continue; // Already in the target, skip silently
            }
        }

        startTransfer(files, targetFolder, move);
    }

    private void startTransfer(List<File> sourceFiles, File targetFolder, boolean isCut)
    {
        // Register expectations BEFORE disk operations begin
        for (File src : sourceFiles) 
        {
            File destItem = new File(targetFolder, src.getName());
            fileIO.expectChange(destItem); // Marks targetFolder/item + targetFolder + all ancestors to root

            if (isCut)
            {
                fileIO.expectChange(src);  // Marks source item + source directory + all ancestors to root
            }
        }

        currentCopyTask = new FileCopyTask(sourceFiles, targetFolder);
        bindProgress(currentCopyTask);

        currentCopyTask.setOnSucceeded(e -> {
            hideProgress();
            if (isCut)
            {
                Set<File> sourceParents = new HashSet<>();

                for (File src : sourceFiles)
                {
                    File parent = src.getParentFile();
                    if (parent != null)
                    {
                        sourceParents.add(parent);
                    }

                    try
                    {
                        // onSucceeded runs on the JavaFX thread
                        // TODO:: This delete recursive runs on fx thread, migrate this to background thread as soon as possible
                        fileIO.deleteRecursive(src, false, false);
                    }
                    catch (IOException ex)
                    {
                        logger.warn("Failed to delete source file after cut operation: {}", src.getAbsolutePath(), ex);
                    }
                }
                clipboardService.clearCutFlag();
                // Refresh all source directories that lost files
                for (File sourceParent : sourceParents)
                {
                    refreshCallback.accept(sourceParent);
                }
            }
            // Refresh destination directory that gained files
            refreshCallback.accept(targetFolder);
        });

        currentCopyTask.setOnFailed(e -> {
            hideProgress();
            clearAllExpectations(sourceFiles, targetFolder, isCut);
            ErrorHandler.show(ownerStage, "Transfer failed", currentCopyTask.getException());
        });

        currentCopyTask.setOnCancelled(e -> {
            hideProgress();
            clearAllExpectations(sourceFiles, targetFolder, isCut);
        });

        showProgress();
        BackgroundExecutor.getInstance().submitIO(currentCopyTask);
    }

    /**
     * Clears watcher change expectations for all destination and source paths associated 
     * with a failed or cancelled file transfer.
     * <p>
     * Unwinds the expectations registered at the start of {@link #startTransfer} to ensure 
     * that future external change notifications for these paths are not mistakenly suppressed.
     *
     * @param sourceFiles  the original files or directories included in the transfer
     * @param targetFolder the destination directory of the transfer
     * @param isCut        {@code true} if the transfer was a move operation (where source paths 
     *                     were also registered); {@code false} if a copy operation
     */
    private void clearAllExpectations(List<File> sourceFiles, File targetFolder, boolean isCut)
    {
        for (File src : sourceFiles)
        {
            fileIO.clearExpectation(new File(targetFolder, src.getName()));
            if (isCut)
            {
                fileIO.clearExpectation(src);
            }
        }
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

    public void setOwnerStage(Stage stage) { this.ownerStage = stage; }
}