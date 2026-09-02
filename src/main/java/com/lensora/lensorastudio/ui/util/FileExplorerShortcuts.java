package com.lensora.lensorastudio.ui.util;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * Single source of truth for every keyboard shortcut shared between the
 * file listing and folder tree views. Each constant here is used BOTH to
 * set a MenuItem's displayed accelerator text (ContextMenuFactory) AND to
 * drive the actual key-dispatch logic (FileExplorerShortcutHandler) - so
 * the label shown to the user and the real behavior can never drift apart.
 */
public final class FileExplorerShortcuts
{
    public static final KeyCombination OPEN =
            new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN);

    public static final KeyCombination RENAME =
            new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN);

    public static final KeyCombination COPY =
            new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN);

    public static final KeyCombination CUT =
            new KeyCodeCombination(KeyCode.X, KeyCombination.SHORTCUT_DOWN);

    public static final KeyCombination PASTE =
            new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);

    public static final KeyCombination DELETE =
            new KeyCodeCombination(KeyCode.DELETE);

    public static final KeyCombination PROPERTIES =
            new KeyCodeCombination(KeyCode.ENTER, KeyCombination.ALT_DOWN);
            
    public static final KeyCombination NEW_FOLDER =
            new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

    private FileExplorerShortcuts() {}
}