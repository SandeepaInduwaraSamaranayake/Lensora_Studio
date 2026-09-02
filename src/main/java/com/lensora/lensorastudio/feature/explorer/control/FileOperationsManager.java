package com.lensora.lensorastudio.feature.explorer.control;

import com.lensora.lensorastudio.core.io.InstrumentedFileIO;
import com.lensora.lensorastudio.ui.util.ContextMenuFactory;

import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.snapfx.SnapFX;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Orchestrating all file operations. Delegates to focused services
 * for clipboard, actions, transfers, menu building, rating, and collections.
 */
public class FileOperationsManager 
{
    private final ContextMenuFactory.FileContextMenu ctx;
    private final FileActionService actionService;
    private final FileClipboardService clipboardService;
    private final FileTransferService transferService;
    private final FileMenuBuilder menuBuilder;
    private final CollectionService collectionService;
    Supplier<List<File>> selectedFilesSupplier;

    public FileOperationsManager(
                                    HBox progressContainer,
                                    ProgressBar progressBar,
                                    Label progressLabel,
                                    Label progressSpeedLabel,
                                    Label progressEtaLabel,
                                    Supplier<File> selectedFileSupplier,
                                    Supplier<List<File>> selectedFilesSupplier,
                                    Consumer<File> refreshCallback,
                                    BooleanBinding multiSelectBinding,
                                    Supplier<File> watchRootSupplier,
                                    InstrumentedFileIO fileIO
                                )
    {
        this.ctx = ContextMenuFactory.build();

        // Instantiate services (order matters for dependencies)
        this.clipboardService = new FileClipboardService(selectedFilesSupplier);
        
        this.transferService = new FileTransferService(
                                                        progressContainer, 
                                                        progressBar, 
                                                        progressLabel,
                                                        progressSpeedLabel, 
                                                        progressEtaLabel,
                                                        refreshCallback, 
                                                        clipboardService,
                                                        watchRootSupplier,
                                                        fileIO
        );
        
        this.actionService = new FileActionService(
                                                        selectedFileSupplier, 
                                                        selectedFilesSupplier, 
                                                        refreshCallback,
                                                        watchRootSupplier,
                                                        fileIO
        );

        this.menuBuilder = new FileMenuBuilder(
                                                        selectedFileSupplier, 
                                                        selectedFilesSupplier, 
                                                        ctx, 
                                                        transferService
        );

        this.collectionService = new CollectionService();

        this.selectedFilesSupplier = selectedFilesSupplier;

        wireMenuActions(multiSelectBinding);
    }

    private void wireMenuActions(BooleanBinding multiSelectBinding) 
    {
        // Disable single-item actions when multiple files are selected
        if (multiSelectBinding != null) 
        {
            ctx.rename().disableProperty().bind(multiSelectBinding);
            ctx.showInExplorer().disableProperty().bind(multiSelectBinding);
            ctx.properties().disableProperty().bind(multiSelectBinding);
        }

        ctx.open().setOnAction(e -> actionService.openSelectedFiles());
        ctx.openInImageViewer().setOnAction(e -> actionService.openInImageViewer());
        ctx.rename().setOnAction(e -> actionService.renameSelectedFile());
        ctx.copy().setOnAction(e -> clipboardService.copySelectedFiles());
        ctx.cut().setOnAction(e -> clipboardService.cutSelectedFiles());
        ctx.move().setOnAction(e -> actionService.moveSelectedFiles());
        ctx.delete().setOnAction(e -> actionService.deleteSelectedFiles());
        ctx.showInExplorer().setOnAction(e -> actionService.showInExplorer());
        ctx.properties().setOnAction(e -> actionService.showMetadata());
        ctx.addToCollection().setOnAction(e -> collectionService.addSelectedToCollection(selectedFilesSupplier.get()));

        // Rebuild dynamic submenus whenever the main menu is shown
        ctx.menu().setOnShowing(e -> {
            menuBuilder.rebuildOpenWithMenu();
            menuBuilder.rebuildSendToMenu();
            menuBuilder.rebuildRatingMenu();
        });
    }

    // ─── Public API (delegated to services) ─────────────────────────────────

    public ContextMenu getContextMenu() { return ctx.menu(); }

    public void setStage(Stage stage)
    {
        actionService.setOwnerStage(stage);
        clipboardService.setOwnerStage(stage);
        transferService.setOwnerStage(stage);
        menuBuilder.setOwnerStage(stage);
        collectionService.setOwnerStage(stage);
    }

    public void setSnapFX(SnapFX snapFX) 
    {
        actionService.setSnapFX(snapFX);
    }

    public void setShowMetadataHandler(Consumer<File> handler) 
    {
        actionService.setShowMetadataHandler(handler);
    }

    // FileTransferService passthrough
    public void copyFilesToClipboard(List<File> files) { clipboardService.copyFilesToClipboard(files); }
    public void cutFilesToClipboard(List<File> files)  { clipboardService.cutFilesToClipboard(files); }

    public void pasteInto(File targetFolder) 
    {
        List<File> sources = clipboardService.readFilesFromClipboard();
        boolean isCut = clipboardService.isClipboardMarkedAsCut();
        transferService.pasteInto(targetFolder, sources, isCut);
    }

    public void dropFilesInto(List<File> files, File targetFolder, boolean move) 
    {
        transferService.dropFilesInto(files, targetFolder, move);
    }

    // FileActionService passthrough
    public void openSelectedFiles()    { actionService.openSelectedFiles(); }
    public void renameSelectedFile()   { actionService.renameSelectedFile(); }
    public void deleteSelectedFiles()  { actionService.deleteSelectedFiles(); }
    public void showMetadata()         { actionService.showMetadata(); }
}