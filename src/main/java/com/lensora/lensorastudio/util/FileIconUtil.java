package com.lensora.lensorastudio.util;

import org.kordamp.ikonli.javafx.FontIcon;
import javafx.scene.Node;

import java.io.File;

/**
 * Utility for obtaining FontAwesome file and folder icons.
 */
public class FileIconUtil 
{
    private FileIconUtil() {} // prevent instantiation

    /**
     * Returns a FontIcon for the given file/folder with a default size of 16px.
     *
     * @param file the file or directory
     * @return a FontIcon node
     */
    public static Node getFileIcon(File file) 
    {
        return getFileIcon(file, 500);
    }

    /**
     * Returns a FontIcon for the given file/folder with the specified size.
     *
     * @param file the file or directory
     * @param size the icon size in pixels
     * @return a FontIcon node
     */
    public static Node getFileIcon(File file, int size) 
    {
        if (file.isDirectory())
    {
            FontIcon icon = new FontIcon("fas-folder");
            icon.getStyleClass().add("icon-size-60");
            return icon;
        }

        String ext = getFileExtension(file);
        String iconLiteral = switch (ext != null ? ext.toLowerCase() : "") 
        {
            case "jpg", "jpeg", "png", "gif", "bmp", "tiff" -> "fas-file-image";
            case "pdf"                                      -> "fas-file-pdf";
            case "doc", "docx"                              -> "fas-file-word";
            case "xls", "xlsx"                              -> "fas-file-excel";
            case "ppt", "pptx"                              -> "fas-file-powerpoint";
            case "zip", "rar", "7z", "gz"                   -> "fas-file-archive";
            case "mp4", "avi", "mkv", "mov"                 -> "fas-file-video";
            case "mp3", "wav", "flac"                       -> "fas-file-audio";
            case "txt"                                      -> "fas-file-alt";
            case "java", "class", "js", "html", "css"       -> "fas-file-code";
            default                                         -> "fas-file";
        };
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(size);
        return icon;
    }

    /**
     * Helper to extract file extension (without the dot).
     */
    private static String getFileExtension(File file) 
    {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1).toLowerCase() : null;
    }
}
