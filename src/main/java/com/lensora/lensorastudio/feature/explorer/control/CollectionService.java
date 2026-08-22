package com.lensora.lensorastudio.feature.explorer.control;

import com.lensora.lensorastudio.feature.project.model.Collection;
import com.lensora.lensorastudio.feature.project.repository.CollectionRepository;
import com.lensora.lensorastudio.ui.dialogs.Dialogs;
import com.lensora.lensorastudio.ui.dialogs.ErrorHandler;

import javafx.scene.control.ChoiceDialog;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException;
import java.util.List;

/**
 * Service for adding files to manual collections.
 */
public class CollectionService 
{

    private Stage ownerStage;

    public CollectionService() {}

    public void addSelectedToCollection(List<File> files) 
    {
        if (files == null || files.isEmpty()) return;

        try 
        {
            List<Collection> manual = CollectionRepository.findAll().stream()
                    .filter(c -> c.getType() == Collection.Type.MANUAL)
                    .toList();

            if (manual.isEmpty())
            {
                Dialogs.showInfo(ownerStage, "Add to Collection", null, "No manual collections exist yet. Create one first.");
                return;
            }

            ChoiceDialog<Collection> dialog = new ChoiceDialog<>(manual.get(0), manual);
            dialog.setTitle("Add to Collection");
            dialog.setHeaderText(null);
            dialog.setContentText("Add " + files.size() + " file(s) to:");

            dialog.showAndWait().ifPresent(collection -> {
                try 
                {
                    for (File f : files) 
                    {
                        CollectionRepository.addItem(collection.getCollectionId(), f.getAbsolutePath());
                    }
                    Dialogs.showInfo(ownerStage, "Add to Collection", null,
                            files.size() + " file(s) added to \"" + collection.getName() + "\".");
                } 
                catch (SQLException e) 
                {
                    ErrorHandler.show(ownerStage, "Failed to add to collection", e);
                }
            });
        } 
        catch (SQLException e)
        {
            ErrorHandler.show(ownerStage, "Failed to load collections", e);
        }
    }

    public void setOwnerStage(Stage stage) { this.ownerStage = stage; }
}