package com.lensora.lensorastudio.ui.util;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

/** Builds the single shared file context menu used by every file view (table, list, icons). */
public final class ContextMenuFactory
{
        private ContextMenuFactory() {}

        public record FileContextMenu(
                ContextMenu menu,
                MenuItem open,
                Menu openWith,
                MenuItem openInImageViewer,
                MenuItem rename,
                MenuItem copy,
                MenuItem cut,
                MenuItem move,
                Menu sendTo,
                Menu rating,
                MenuItem addToCollection,
                MenuItem delete,
                MenuItem showInExplorer,
                MenuItem properties)
        {}

        public static FileContextMenu build()
        {
                MenuItem open = new MenuItem("_Open");
        
                Menu openWith = new Menu("Open _With");

                MenuItem openInImageViewer = new MenuItem("Open in Lensora Image Viewer");

                MenuItem rename = new MenuItem("_Rename…");
                MenuItem copy = new MenuItem("_Copy");
                MenuItem cut = new MenuItem("Cu_t");
                MenuItem move = new MenuItem("_Move to…");

                Menu sendTo = new Menu("_Send To");
                Menu rating = new Menu("Rate / Flag");

                MenuItem addToCollection = new MenuItem("_Add to Collection…");
                MenuItem delete = new MenuItem("_Delete");
                MenuItem showInExplorer = new MenuItem("Show in _Explorer");
                MenuItem properties = new MenuItem("Metada_ta");
                

                // Seed dummy items so JavaFX renders submenus with expansion arrows upfront
                openWith.getItems().add(new MenuItem("Loading…"));
                sendTo.getItems().add(new MenuItem("Loading…"));
                rating.getItems().add(new MenuItem("Loading…"));

                // set accelerators for the menu items, using the shared constants from FileExplorerShortcuts
                open.setAccelerator(FileExplorerShortcuts.OPEN);
                rename.setAccelerator(FileExplorerShortcuts.RENAME);
                copy.setAccelerator(FileExplorerShortcuts.COPY);
                cut.setAccelerator(FileExplorerShortcuts.CUT);
                delete.setAccelerator(FileExplorerShortcuts.DELETE);
                properties.setAccelerator(FileExplorerShortcuts.PROPERTIES);

                ContextMenu menu = new ContextMenu(
                        open, openWith, openInImageViewer, new SeparatorMenuItem(),
                        rename, copy, cut, move, sendTo, rating, addToCollection, new SeparatorMenuItem(),
                        delete, new SeparatorMenuItem(),
                        showInExplorer, properties);

                return new FileContextMenu(menu, open, openWith, openInImageViewer, rename, copy, cut,
                        move, sendTo, rating, addToCollection, delete, showInExplorer, properties);
        }
}