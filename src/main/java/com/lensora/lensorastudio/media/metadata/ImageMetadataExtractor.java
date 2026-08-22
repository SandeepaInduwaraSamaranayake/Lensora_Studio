package com.lensora.lensorastudio.media.metadata;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.bmp.BmpHeaderDirectory;
import com.drew.metadata.exif.ExifDirectoryBase;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.jpeg.JpegDirectory;
import com.drew.metadata.png.PngDirectory;
import com.lensora.lensorastudio.util.FileSizeFormatter;

import javafx.scene.image.Image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Wraps drewnoakes/metadata-extractor. Reads EXIF/IPTC/XMP/GPS metadata
 * from image files. Runs synchronously - callers should invoke this off
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
                        logger.warn("[ImageMetadataExtractor] {} - {}: {}", imageFile.getName(), groupName, error);
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

    public static String getDimensions(File imageFile)
    {
        try
        {
            Metadata metadata = ImageMetadataReader.readMetadata(imageFile);
            ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);

            if (exif != null)
            {
                Integer width = exif.getInteger(ExifDirectoryBase.TAG_EXIF_IMAGE_WIDTH);
                Integer height = exif.getInteger(ExifDirectoryBase.TAG_EXIF_IMAGE_HEIGHT);

                if (width != null && height != null) { return width + "x" + height; }
            }

            JpegDirectory jpeg = metadata.getFirstDirectoryOfType(JpegDirectory.class);

            if (jpeg != null)
            {
                Integer width = jpeg.getInteger(JpegDirectory.TAG_IMAGE_WIDTH);
                Integer height = jpeg.getInteger(JpegDirectory.TAG_IMAGE_HEIGHT);

                if (width != null && height != null) { return width + "x" + height; }
            }

            PngDirectory png = metadata.getFirstDirectoryOfType(PngDirectory.class);

            if (png != null)
            {
                Integer width = png.getInteger(PngDirectory.TAG_IMAGE_WIDTH);
                Integer height = png.getInteger(PngDirectory.TAG_IMAGE_HEIGHT);

                if (width != null && height != null) { return width + "x" + height; }
            }

            BmpHeaderDirectory bmp = metadata.getFirstDirectoryOfType(BmpHeaderDirectory.class);

            if (bmp != null)
            {
                Integer width = bmp.getInteger(BmpHeaderDirectory.TAG_IMAGE_WIDTH);
                Integer height = bmp.getInteger(BmpHeaderDirectory.TAG_IMAGE_HEIGHT);

                if (width != null && height != null) { return width + "x" + height; }
            }

            // Fallback (if metadata does not contain dimensions)
            return getDimensionsFromImage(imageFile);

        }
        catch (ImageProcessingException | IOException e)
        {
            logger.warn("Failed to read dimensions for {}", imageFile.getName(), e);
        }

        return "";
    }

    private static String getDimensionsFromImage(File file)
    {
        try
        {
            Image image = new Image(file.toURI().toString(),true);
            return (int) image.getWidth() + "x" + (int) image.getHeight();
        }
        catch (Exception e)
        {
            return "";
        }
    }
}