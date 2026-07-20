package com.lensora.lensorastudio.util;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import com.lensora.lensorastudio.model.MediaMetadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Wraps drewnoakes/metadata-extractor. Reads EXIF/IPTC/XMP/GPS metadata
 * from image files. Runs synchronously — callers should invoke this off
 * the FX thread (see MetadataExtractionService).
 */
public final class ImageMetadataExtractor
{
    private static final Logger logger = LoggerFactory.getLogger(ImageMetadataExtractor.class);

    private ImageMetadataExtractor() {}

    public static MediaMetadata extract(File imageFile)
    {
        MediaMetadata result = new MediaMetadata(imageFile.getAbsolutePath(), MediaMetadata.MediaType.IMAGE);

        result.put("File", "Name", imageFile.getName());
        result.put("File", "Size", FileSizeFormatter.formatFileSize(imageFile.length()));
        result.put("File", "Path", imageFile.getAbsolutePath());

        try
        {
            Metadata metadata = ImageMetadataReader.readMetadata(imageFile);

            for (Directory directory : metadata.getDirectories())
            {
                String groupName = directory.getName();

                for (Tag tag : directory.getTags())
                {
                    result.put(groupName, tag.getTagName(), tag.getDescription());
                }

                if (directory.hasErrors())
                {
                    for (String error : directory.getErrors())
                    {
                        logger.warn("[ImageMetadataExtractor] {} — {}: {}", imageFile.getName(), groupName, error);
                    }
                }
            }
        }
        catch (ImageProcessingException e)
        {
            logger.warn("[ImageMetadataExtractor] Could not process image {}: {}", imageFile.getName(), e.getMessage());
            result.put("Error", "Message", "Unsupported or corrupt image format: " + e.getMessage());
        }
        catch (IOException e)
        {
            logger.error("[ImageMetadataExtractor] I/O error reading {}", imageFile.getName(), e);
            result.put("Error", "Message", "Could not read file: " + e.getMessage());
        }

        return result;
    }

    public static boolean isSupportedImage(File file)
    {
        String ext = getExtension(file);
        return ext != null && (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")
                || ext.equals("tif") || ext.equals("tiff") || ext.equals("heic") || ext.equals("heif")
                || ext.equals("webp") || ext.equals("bmp") || ext.equals("gif")
                || ext.equals("cr2") || ext.equals("nef") || ext.equals("arw") || ext.equals("dng"));
    }

    private static String getExtension(File file)
    {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1).toLowerCase() : null;
    }
}