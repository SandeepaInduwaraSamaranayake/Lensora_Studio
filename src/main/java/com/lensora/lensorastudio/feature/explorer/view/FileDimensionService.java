package com.lensora.lensorastudio.feature.explorer.view;

import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lensora.lensorastudio.media.metadata.ImageMetadataExtractor;
import com.lensora.lensorastudio.media.repository.ImageCacheRepository;
import com.lensora.lensorastudio.media.service.ImageValidator;

import java.io.File;
import java.util.Map;
import java.util.concurrent.*;

/** Loads and caches image dimensions ("1920×1080") for the file table's Dimensions column. */
public class FileDimensionService
{
    private static final Logger logger = LoggerFactory.getLogger(FileDimensionService.class);

    private final Map<File, SimpleStringProperty> props = new ConcurrentHashMap<>();
    private final Map<File, String> cache = new ConcurrentHashMap<>();
    private final ExecutorService dimensionExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
    private final Map<File, Future<?>> dimensionFutures = new ConcurrentHashMap<>();

    public SimpleStringProperty propertyFor(File file)
    {
        SimpleStringProperty prop = props.get(file);
        if (prop == null)
        {
            prop = new SimpleStringProperty("");
            props.put(file, prop);
            loadAsync(file, prop);
        }
        return prop;
    }

    public void clear()
    {
        cancelAllDimensionTasks();
        props.clear();
        cache.clear();
    }

    public void cancelAllDimensionTasks()
    {
        for (Future<?> future : dimensionFutures.values())
        {
            if (!future.isDone())
            {
                future.cancel(true);
            }
        }
        dimensionFutures.clear();
    }

    public void shutdownDimensionExecutor()
    {
        dimensionExecutor.shutdownNow();
    }

    private void loadAsync(File file, SimpleStringProperty prop)
    {
        if (!ImageValidator.isSupportedMetadataImage(file))
        {
            prop.set("");
            return;
        }

        // In-memory fast path - same session, already resolved once.
        String cached = cache.get(file);
        if (cached != null)
        {
            prop.set(cached);
            return;
        }

        Future<?> oldFuture = dimensionFutures.remove(file);
        if (oldFuture != null && !oldFuture.isDone())
        {
            oldFuture.cancel(true);
        }

        Task<String> task = new Task<>()
        {
            @Override
            protected String call()
            {
                if (isCancelled() || Thread.currentThread().isInterrupted()) return "";
                
                // Persistent DB cache - survives across folder reloads and
                // app restarts. Only recomputes if the file's size or
                // modified time changed since it was last measured.
                try
                {
                    var dbHit = ImageCacheRepository.findValidDimensions(file);
                    if (dbHit.isPresent())
                    {
                        return dbHit.get().format();
                    }
                }
                catch (Exception e)
                {
                    logger.debug("Dimension cache lookup failed for {}, falling back to extraction", file.getName(), e);
                }

                if (isCancelled() || Thread.currentThread().isInterrupted()) return "";

                // Cache miss or invalidated — actually run the (expensive)
                // metadata extraction, then persist the result so the next
                // folder load skips this cost entirely.
                String dimensions = ImageMetadataExtractor.getDimensions(file);
                persistDimensionsAsync(file, dimensions);
                return dimensions;
            }
        };

        task.setOnSucceeded(e -> {
            if (!task.isCancelled())
            {
                String dimensions = task.getValue();
                cache.put(file, dimensions);
                prop.set(dimensions);
            }
            dimensionFutures.remove(file);
        });

        task.setOnFailed(e -> {
            logger.warn("Failed to load dimensions for {}", file.getName(), task.getException());
            cache.put(file, "");
            prop.set("");
            dimensionFutures.remove(file);
        });

        task.setOnCancelled(e -> dimensionFutures.remove(file));

        Future<?> future = dimensionExecutor.submit(task);
        dimensionFutures.put(file, future);
    }

    /** Fire-and-forget DB write, off this same worker thread - never blocks the UI-bound result. */
    private void persistDimensionsAsync(File file, String dimensionsFormatted)
    {
        if (dimensionsFormatted == null || dimensionsFormatted.isBlank()) return;

        String[] parts = dimensionsFormatted.split("x");
        if (parts.length != 2) return;

        try
        {
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            ImageCacheRepository.saveDimensions(file, width, height);
        }
        catch (Exception e)
        {
            logger.debug("Failed to persist dimensions for {}", file.getName(), e);
        }
    }
}