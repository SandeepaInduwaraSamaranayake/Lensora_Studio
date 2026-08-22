package com.lensora.lensorastudio.media.service;

import com.lensora.lensorastudio.media.cache.ImageCache;

import javafx.application.Platform;
import javafx.scene.image.Image;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class ThumbnailService
{
    private static final ThumbnailService INSTANCE = new ThumbnailService();
    private static final int THUMBNAIL_SIZE = 160;
    private static final int MAX_CONCURRENT_DECODES = 4; // bounds CPU/RAM regardless of scroll speed
    private static final int MAX_FAILED_ENTRIES = 2000;

    private final Set<File> failedFiles = Collections.synchronizedSet(new LinkedHashSet<>());

    private final ExecutorService decodeExecutor = new ThreadPoolExecutor(
            MAX_CONCURRENT_DECODES, MAX_CONCURRENT_DECODES,
            0L, TimeUnit.MILLISECONDS,
            // Bounded queue + DiscardOldestPolicy: if the user scrolls fast,
            // stale queued requests for cells no longer visible are dropped
            // rather than piling up unboundedly.
            new LinkedBlockingQueue<>(64),
            r -> { Thread t = new Thread(r, "Lensora-thumbnail-decoder"); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    private ThumbnailService() {}

    public static ThumbnailService getInstance() { return INSTANCE; }

    public void requestThumbnail(File file, Consumer<Image> callback)
    {
        if (file == null || failedFiles.contains(file) || !ImageValidator.isJavaFXLoadable(file))
        {
            callback.accept(null);
            return;
        }

        decodeExecutor.submit(() -> {
            try
            {
                // backgroundLoading=false: we're already off the FX thread
                // inside our own bounded pool, so a synchronous decode here
                // is what actually enforces the concurrency cap — letting
                // JavaFX's own backgroundLoading=true spin up unbounded
                // internal threads defeats the purpose of this pool.
                Image image = ImageCache.getOrLoad(file, THUMBNAIL_SIZE, THUMBNAIL_SIZE, false);

                if (image == null || image.isError())
                {
                    addFailed(file);
                    Platform.runLater(() -> callback.accept(null));
                }
                else
                {
                    Platform.runLater(() -> callback.accept(image));
                }
            }
            catch (Exception e)
            {
                addFailed(file);
                Platform.runLater(() -> callback.accept(null));
            }
        });
    }

    private void addFailed(File file)
    {
        if (failedFiles.size() > MAX_FAILED_ENTRIES)
        {
            failedFiles.clear();
        }
        failedFiles.add(file);
    }

    public void clearFailedState()
    {
        failedFiles.clear();
    }
}