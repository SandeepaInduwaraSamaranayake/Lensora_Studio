package com.lensora.lensorastudio.feature.explorer.control;

import com.lensora.lensorastudio.ui.util.FileExplorerShortcuts;

import javafx.scene.Scene;
import javafx.scene.input.KeyEvent;

/**
 * Single dispatch point for every keyboard shortcut shared between the
 * folder tree and file listing views. Uses the SAME KeyCombination
 * constants ContextMenuFactory uses for display, matched via
 * KeyCombination.match(event) - which correctly handles modifier state
 * and cross-platform SHORTCUT_DOWN mapping, unlike manual isControlDown()
 * checks.
 *
 * Routes purely by which pane currently has focus - JavaFX focus is
 * always exclusive to one node, so there is no ambiguity about which
 * action a given key press should trigger.
 */
public class FileExplorerShortcutHandler
{
    private final FolderTreeManager folderTreeManager;
    private final FileListingManager fileListingManager;
    private final FileOperationsManager fileOperationsManager;

    public FileExplorerShortcutHandler(FolderTreeManager folderTreeManager,
                                        FileListingManager fileListingManager,
                                        FileOperationsManager fileOperationsManager)
    {
        this.folderTreeManager = folderTreeManager;
        this.fileListingManager = fileListingManager;
        this.fileOperationsManager = fileOperationsManager;
    }

    public void attach(Scene scene)
    {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handle);
    }

    private void handle(KeyEvent e)
    {
        if (FileExplorerShortcuts.COPY.match(e))
        {
            if (folderTreeManager.isFocused())              { folderTreeManager.copySelectedFolder(); e.consume(); }
            else if (fileListingManager.isFocused())        { fileOperationsManager.copyFilesToClipboard(fileListingManager.getSelectedFiles()); e.consume(); }
        }
        else if (FileExplorerShortcuts.CUT.match(e))
        {
            if (fileListingManager.isFocused())             { fileOperationsManager.cutFilesToClipboard(fileListingManager.getSelectedFiles()); e.consume(); }
        }
        else if (FileExplorerShortcuts.PASTE.match(e))
        {
            if (folderTreeManager.isFocused())              { fileOperationsManager.pasteInto(folderTreeManager.getSelectedFolder()); e.consume(); }
            else if (fileListingManager.isFocused())        { fileOperationsManager.pasteInto(folderTreeManager.getCurrentFolder()); e.consume(); }
        }
        else if (FileExplorerShortcuts.DELETE.match(e))
        {
            if (folderTreeManager.isFocused())              { folderTreeManager.deleteSelectedFolder(); e.consume(); }
            else if (fileListingManager.isFocused())        { fileOperationsManager.deleteSelectedFiles(); e.consume(); }
        }
        else if (FileExplorerShortcuts.RENAME.match(e))
        {
            if (fileListingManager.isFocused())             { fileOperationsManager.renameSelectedFile(); e.consume(); }
            else if (folderTreeManager.isFocused())         { folderTreeManager.renameSelectedFolder(); e.consume(); }
        }
        else if (FileExplorerShortcuts.OPEN.match(e))
        {
            if (fileListingManager.isFocused())             { fileOperationsManager.openSelectedFiles(); e.consume(); }
        }
        else if (FileExplorerShortcuts.PROPERTIES.match(e))
        {
            if (fileListingManager.isFocused())             { fileOperationsManager.showMetadata(); e.consume(); }
        }
        else if (FileExplorerShortcuts.NEW_FOLDER.match(e))
        {
            if (folderTreeManager.isFocused())              { folderTreeManager.createNewFolder(); e.consume(); }
        }
    }
}