package com.lensora.lensorastudio.feature.explorer.control;

import com.lensora.lensorastudio.ui.dialogs.NotificationUtil;
import com.lensora.lensorastudio.util.ClipboardFormats;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Manages file/folder clipboard operations: Copy, Cut, Paste source reading,
 * and Cut flag management.
 */
public class FileClipboardService
{

    private final Supplier<List<File>> selectedFilesSupplier;
    private Stage ownerStage;

    public FileClipboardService(Supplier<List<File>> selectedFilesSupplier) 
    {
        this.selectedFilesSupplier = selectedFilesSupplier;
    }

    /** Copies the selected files to the clipboard (standard Copy). */
    public void copySelectedFiles() 
    {
        putFilesOnClipboard(selectedFilesSupplier.get(), "Copy", false);
    }

    /** overload to accept list of files */
    public void copySelectedFiles(List<File> files)
    {
        if (files == null || files.isEmpty()) return;
        putFilesOnClipboard(files,"Copy" ,false);
    }

    /** Cuts the selected files to the clipboard (marks for Move on Paste). */
    public void cutSelectedFiles() 
    {
        putFilesOnClipboard(selectedFilesSupplier.get(), "Cut", true);
    }

    /** overload to accept list of files */
    public void cutSelectedFiles(List<File> files)
    {
        if (files == null || files.isEmpty()) return;
        putFilesOnClipboard(files, "Cut", true);
    }

    /** Places arbitrary files on the clipboard. */
    public void copyFilesToClipboard(List<File> files) 
    {
        putFilesOnClipboard(files, "Copy", false);
    }

    /** Places arbitrary files on the clipboard with Cut flag. */
    public void cutFilesToClipboard(List<File> files) 
    {
        putFilesOnClipboard(files, "Cut", true);
    }

    private void putFilesOnClipboard(List<File> files, String actionLabel, boolean cut) 
    {
        if (files == null || files.isEmpty()) 
        {
            NotificationUtil.showToast(ownerStage, "No files selected", "fas-exclamation-circle");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.put(DataFormat.FILES, files);
        content.put(ClipboardFormats.CUT, cut);
        Clipboard.getSystemClipboard().setContent(content);

        NotificationUtil.showToast(ownerStage, files.size() + " file(s) " + actionLabel.toLowerCase() + " to the clipboard");
    }

    /** Reads the file list from the clipboard. Returns an empty list if none found. */
    @SuppressWarnings("unchecked")
    public List<File> readFilesFromClipboard() 
    {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        Object standard = clipboard.getContent(DataFormat.FILES);
        if (standard instanceof List<?> list) 
        {
            return (List<File>) list;
        }
        return Collections.emptyList();
    }

    /** Returns true if the clipboard content is marked as "Cut" (i.e., a Move operation). */
    public boolean isClipboardMarkedAsCut() 
    {
        Object cutFlag = Clipboard.getSystemClipboard().getContent(ClipboardFormats.CUT);
        return Boolean.TRUE.equals(cutFlag);
    }

    /** Clears the Cut flag from the clipboard (called after a successful Move/Paste). */
    public void clearCutFlag() 
    {
        ClipboardContent cleared = new ClipboardContent();
        cleared.put(ClipboardFormats.CUT, false);
        Clipboard.getSystemClipboard().setContent(cleared);
    }

    /** Checks if a paste operation would recursively copy a folder into itself. */
    public boolean isRecursivePaste(File source, File target)
    {
        if (source == null || target == null || !source.isDirectory()) return false;

        Path src = source.toPath().toAbsolutePath().normalize();
        Path tgt = target.toPath().toAbsolutePath().normalize();

        return tgt.equals(src) || tgt.startsWith(src);
    }

    public void setOwnerStage(Stage stage) { this.ownerStage = stage; }
}