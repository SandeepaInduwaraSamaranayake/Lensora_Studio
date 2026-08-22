package com.lensora.lensorastudio.media.service;

import com.lensora.lensorastudio.media.metadata.ImageMetadataExtractor;
import com.lensora.lensorastudio.media.metadata.MediaMetadata;
import com.lensora.lensorastudio.media.metadata.VideoMetadataExtractor;

import javafx.concurrent.Task;

import java.io.File;
import java.util.function.Consumer;

/**
 * Runs metadata extraction (metadata-extractor for images, ffprobe for
 * videos) on a background thread and hands the result back on the FX thread.
 */
public final class MetadataExtractionService
{
    private MetadataExtractionService() {}

    public static Task<MediaMetadata> createTask(File file)
    {
        return new Task<>() {
            @Override
            protected MediaMetadata call()
            {
                if (ImageValidator.isSupportedMetadataImage(file))
                {
                    return ImageMetadataExtractor.extract(file);
                }
                else if (VideoMetadataExtractor.isSupportedVideo(file))
                {
                    return VideoMetadataExtractor.extract(file);
                }
                else
                {
                    MediaMetadata unsupported = new MediaMetadata(file.getAbsolutePath(), MediaMetadata.MediaType.UNSUPPORTED);
                    unsupported.put("File", "Name", file.getName());
                    unsupported.put("Info", "Message", "Metadata extraction is not supported for this file type.");
                    return unsupported;
                }
            }
        };
    }

    public static void extractAsync(File file, Consumer<MediaMetadata> onResult, Consumer<Throwable> onError)
    {
        Task<MediaMetadata> task = createTask(file);
        task.setOnSucceeded(e -> onResult.accept(task.getValue()));
        task.setOnFailed(e -> onError.accept(task.getException()));
        Thread thread = new Thread(task, "Lensora-metadata-extraction");
        thread.setDaemon(true);
        thread.start();
    }
}