package com.lensora.lensorastudio.media.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Set;

public final class ImageValidator 
{

    // Formats JavaFX can natively load and render in ImageView
    private static final Set<String> JAVAFX_SUPPORTED_EXTENSIONS = Set.of(
                                                                            "jpg", 
                                                                            "jpeg", 
                                                                            "png", 
                                                                            "gif", 
                                                                            "bmp"
    );

    // All image and RAW formats supported across the app for metadata indexing
    private static final Set<String> METADATA_SUPPORTED_EXTENSIONS = Set.of(
                                                                            // Standard & Web Formats
                                                                "jpg", 
                                                                            "jpeg", 
                                                                            "png", 
                                                                            "gif", 
                                                                            "bmp",
                                                                            "tif", 
                                                                            "tiff", 
                                                                            "webp",
                                                                            "heic",
                                                                            "heif", 
                                                                            "avif",
                                                                            "ico",
                                                                            "psd",

                                                                            // Camera RAW Formats
                                                                            "cr2", "cr3",           // Canon
                                                                            "nef", "nrw",           // Nikon              
                                                                            "arw",                  // Sony
                                                                            "dng",                  // Adobe Universal RAW
                                                                            "orf",                  // Olympus / OM System
                                                                            "rw2", "rwl",           // Panasonic / Leica
                                                                            "raf",                  // Fujifilm
                                                                            "pef",                  // Pentax
                                                                            "srw"                   // Samsung
    );

    private ImageValidator() {}

    /**
     * Checks if a file is safe for JavaFX Image loading.
     * Validates extension AND magic header bytes to prevent ImageStorageException console log spam.
     */
    public static boolean isJavaFXLoadable(File file) 
    {
        if (file == null || !file.exists() || !file.isFile() || file.length() < 8) 
        {
            return false;
        }

        String ext = getExtension(file);
        if (ext == null || !JAVAFX_SUPPORTED_EXTENSIONS.contains(ext)) 
        {
            return false;
        }

        return hasValidJavaFXHeader(file);
    }

    /**
     * Checks if a file is a supported image format for metadata extraction.
     */
    public static boolean isSupportedMetadataImage(File file) 
    {
        if (file == null || !file.exists() || !file.isFile()) 
        {
            return false;
        }

        String ext = getExtension(file);
        return ext != null && METADATA_SUPPORTED_EXTENSIONS.contains(ext);
    }

    /**
     * Inspects magic bytes (header signature) for JavaFX-compatible image streams.
     */
    public static boolean hasValidJavaFXHeader(File file) 
    {
        try (InputStream is = new FileInputStream(file)) 
        {
            byte[] header = new byte[8];
            int read = is.read(header);
            if (read < 8) return false;

            // PNG: 89 50 4E 47
            if ((header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') return true;

            // JPEG: FF D8 FF
            if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) return true;

            // GIF: GIF8 (47 49 46 38)
            if (header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8') return true;

            // BMP: BM (42 4D)
            if (header[0] == 'B' && header[1] == 'M') return true;

            return false;
        }
        catch (Exception e) 
        {
            return false;
        }
    }

    public static String getExtension(File file) 
    {
        if (file == null) return null;
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1).toLowerCase() : null;
    }
}