package com.lensora.lensorastudio.feature.explorer.view;

import javafx.scene.control.ListView;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Toggle;

import java.io.File;
import java.util.function.Consumer;

/** Owns the Details/List/Icons/Thumbnails toggle group and which view container is currently visible. */
public class FileViewSwitcher
{
    public enum ViewMode { DETAILS, LIST, ICONS, THUMBNAILS }

    private final TableView<File> fileTable;
    private final ListView<File> fileListView;
    private final javafx.scene.Node iconViewNode;
    private final ToggleButton btnDetails, btnList, btnIcons, btnThumbnails;
    private final ToggleGroup group = new ToggleGroup();

    private Consumer<ViewMode> onViewChanged;
    private ViewMode lastNotifiedMode = null;

    public FileViewSwitcher(TableView<File> fileTable, ListView<File> fileListView,
                        javafx.scene.Node iconViewNode,
                        ToggleButton btnDetails, ToggleButton btnList, ToggleButton btnIcons, ToggleButton btnThumbnails)
    {
        this.fileTable = fileTable;
        this.fileListView = fileListView;
        this.iconViewNode = iconViewNode;
        this.btnDetails = btnDetails;
        this.btnList = btnList;
        this.btnIcons = btnIcons;
        this.btnThumbnails = btnThumbnails;

        btnDetails.setToggleGroup(group);
        btnList.setToggleGroup(group);
        btnIcons.setToggleGroup(group);
        btnThumbnails.setToggleGroup(group);

        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null) 
            {
                // Prevent empty selection by re-selecting the previous toggle
                group.selectToggle(oldToggle != null ? oldToggle : btnDetails);
            } 
            else 
            {
                applyVisibility(newToggle);
            }
        });
        btnDetails.setSelected(true);
        applyVisibility(btnDetails);
    }

    public void setOnViewChanged(Consumer<ViewMode> callback) { this.onViewChanged = callback; }

    public ViewMode getCurrentMode()
    {
        Toggle selected = group.getSelectedToggle();
        if (selected == btnList) return ViewMode.LIST;
        if (selected == btnIcons) return ViewMode.ICONS;
        if (selected == btnThumbnails) return ViewMode.THUMBNAILS;
        return ViewMode.DETAILS;
    }

    private void applyVisibility(Toggle selected)
    {
        if (selected == null) return;

        fileTable.setVisible(false); 
        fileTable.setManaged(false);
        fileListView.setVisible(false); 
        fileListView.setManaged(false);
        iconViewNode.setVisible(false); 
        iconViewNode.setManaged(false);

        if (selected == btnDetails) 
        { 
            fileTable.setVisible(true); 
            fileTable.setManaged(true); 
        } 
        else if (selected == btnList) 
        { 
            fileListView.setVisible(true); 
            fileListView.setManaged(true); 
        } 
        else if (selected == btnIcons || selected == btnThumbnails) 
        { 
            iconViewNode.setVisible(true); 
            iconViewNode.setManaged(true); 
        }
        
        ViewMode newMode = getCurrentMode();
        // Only trigger external reload/callback if the actual ViewMode changed
        if (newMode != lastNotifiedMode) 
        {
            lastNotifiedMode = newMode;
            if (onViewChanged != null) 
            {
                onViewChanged.accept(newMode);
            }
        }
    }
}