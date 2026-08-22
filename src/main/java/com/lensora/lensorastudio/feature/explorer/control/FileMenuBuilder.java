package com.lensora.lensorastudio.feature.explorer.control;

import com.lensora.lensorastudio.core.config.AppSettings;
import com.lensora.lensorastudio.feature.project.model.FileRating;
import com.lensora.lensorastudio.feature.settings.model.ExternalApp;
import com.lensora.lensorastudio.feature.settings.ui.ExternalAppsDialog;
import com.lensora.lensorastudio.ui.util.ContextMenuFactory;
import com.lensora.lensorastudio.util.EmailSendUtil;
import com.lensora.lensorastudio.util.ExternalAppLauncher;
import com.lensora.lensorastudio.util.FileSizeFormatter;
import com.lensora.lensorastudio.util.RemovableDriveUtil;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;

/**
 * Responsible for building and refreshing the dynamic portions of the file
 * context menu (Open With, Send To, Rating/Flag).
 */
public class FileMenuBuilder 
{

    private final Supplier<File> selectedFileSupplier;
    private final Supplier<List<File>> selectedFilesSupplier;
    private Stage ownerStage;
    private final ContextMenuFactory.FileContextMenu ctx;
    private final FileTransferService transferService;

    public FileMenuBuilder(Supplier<File> selectedFileSupplier,
                            Supplier<List<File>> selectedFilesSupplier,
                            ContextMenuFactory.FileContextMenu ctx,
                            FileTransferService transferService) 
    {
        this.selectedFileSupplier = selectedFileSupplier;
        this.selectedFilesSupplier = selectedFilesSupplier;
        this.ctx = ctx;
        this.transferService = transferService;
    }

    /** Rebuilds the "Open With" submenu with configured external apps and management options. */
    public void rebuildOpenWithMenu() 
    {
        ctx.openWith().getItems().clear();

        List<File> selected = selectedFilesSupplier.get();
        boolean hasSelection = selected != null && !selected.isEmpty();

        List<ExternalApp> configuredApps = AppSettings.getInstance().getExternalApps();

        if (configuredApps.isEmpty()) 
        {
            MenuItem noneItem = new MenuItem("(No applications configured)");
            noneItem.setDisable(true);
            ctx.openWith().getItems().add(noneItem);
        } 
        else 
        {
            for (ExternalApp app : configuredApps) 
            {
                MenuItem item = new MenuItem(app.getName()
                        + (selected != null && selected.size() > 1 ? " (" + selected.size() + " files)" : ""));
                item.setDisable(!hasSelection);
                item.setOnAction(e -> ExternalAppLauncher.openWith(app, selected));
                ctx.openWith().getItems().add(item);
            }
        }

        ctx.openWith().getItems().add(new SeparatorMenuItem());

        MenuItem nativePickerItem = new MenuItem("Choose Application…");
        nativePickerItem.setDisable(!hasSelection || selected.size() != 1);
        nativePickerItem.setOnAction(e -> {
            if (selected != null && selected.size() == 1) 
            {
                ExternalAppLauncher.showNativeOpenWithDialog(selected.get(0));
            }
        });

        MenuItem manageAppsItem = new MenuItem("Manage Applications…");
        manageAppsItem.setOnAction(e -> ExternalAppsDialog.show(ownerStage));

        ctx.openWith().getItems().addAll(nativePickerItem, manageAppsItem);
    }

    /** Rebuilds the "Send To" submenu with external drives, subdirectories, and Email. */
    public void rebuildSendToMenu()
    {
        ctx.sendTo().getItems().clear();

        List<File> selected = selectedFilesSupplier.get();
        boolean hasSelection = selected != null && !selected.isEmpty();

        List<RemovableDriveUtil.DriveInfo> drives = RemovableDriveUtil.listRemovableDrives();

        if (drives.isEmpty()) 
        {
            MenuItem noneItem = new MenuItem("(No external drives detected)");
            noneItem.setDisable(true);
            ctx.sendTo().getItems().add(noneItem);
        } 
        else 
        {
            for (var drive : drives) 
            {
                String freeSpace = FileSizeFormatter.formatFileSize(drive.usableBytes());
                String title = drive.label() + " (" + freeSpace + " free)";
                Menu driveMenu = createDirectoryMenu(drive.rootPath().toFile(), title, hasSelection);
                ctx.sendTo().getItems().add(driveMenu);
            }
        }

        ctx.sendTo().getItems().add(new SeparatorMenuItem());

        MenuItem emailItem = new MenuItem("Email");
        emailItem.setDisable(!hasSelection);
        emailItem.setOnAction(e -> EmailSendUtil.sendFiles(selected, ownerStage.getScene().getRoot()));
        ctx.sendTo().getItems().add(emailItem);
    }

    /** Rebuilds the "Rate / Flag" submenu with star ratings and flag options. */
    public void rebuildRatingMenu() 
    {
        ctx.rating().getItems().clear();
        
        File selected = selectedFileSupplier.get();
        if (selected == null) 
        {
            MenuItem noneItem = new MenuItem("(Select a single file to rate)");
            noneItem.setDisable(true);
            ctx.rating().getItems().add(noneItem);
            return;
        }

        for (int stars = 0; stars <= 5; stars++) 
        {
            int finalStars = stars;
            MenuItem item = new MenuItem(stars == 0 ? "Clear Rating" : "★".repeat(stars));
            item.setOnAction(e -> FileRatingService.setRating(selected, finalStars));
            ctx.rating().getItems().add(item);
        }
        ctx.rating().getItems().add(new SeparatorMenuItem());

        MenuItem favorite = new MenuItem("❤️ Favorite");
        favorite.setOnAction(e -> FileRatingService.setFlag(selected, FileRating.Flag.FAVORITE));

        MenuItem reject = new MenuItem("🚫 Reject");
        reject.setOnAction(e -> FileRatingService.setFlag(selected, FileRating.Flag.REJECTED));

        MenuItem clearFlag = new MenuItem("Clear Flag");
        clearFlag.setOnAction(e -> FileRatingService.setFlag(selected, FileRating.Flag.NONE));

        ctx.rating().getItems().addAll(favorite, reject, clearFlag);
    }

    /** Creates a cascading menu for a directory, allowing direct copy or further subdirectory navigation. */
    private Menu createDirectoryMenu(File directory, String menuTitle, boolean hasSelection) 
    {
        Menu folderMenu = new Menu(menuTitle);
        folderMenu.setDisable(!hasSelection);

        MenuItem dummyItem = new MenuItem("Loading…");
        dummyItem.setDisable(true);
        folderMenu.getItems().add(dummyItem);

        folderMenu.setOnShowing(e -> {
            folderMenu.getItems().clear();

            MenuItem copyHereItem = new MenuItem("Copy directly to this folder");
            copyHereItem.setGraphic(new FontIcon("fas-copy"));
            copyHereItem.setOnAction(ev -> transferService.dropFilesInto(selectedFilesSupplier.get(), directory, false));
            folderMenu.getItems().add(copyHereItem);

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

    public void setOwnerStage(Stage stage) { this.ownerStage = stage; }
}