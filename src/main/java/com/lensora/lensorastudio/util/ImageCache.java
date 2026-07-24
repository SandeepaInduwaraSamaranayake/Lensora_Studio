package com.lensora.lensorastudio.util;

import javafx.scene.image.Image;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Simple bounded LRU cache for JavaFX Images, keyed by absolute file path +
 * last-modified timestamp (so a re-saved/edited file doesn't serve a stale
 * cached preview). Not intended for full-resolution originals — used for
 * preview-sized thumbnails only, to keep memory bounded.
 */
public final class ImageCache
{
    private static final int MAX_ENTRIES = 200;

    private static final Map<String, Image> cache =
            new LinkedHashMap<>(16, 0.75f, true)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest)
                {
                    return size() > MAX_ENTRIES;
                }
            };

    private ImageCache() {}

    /**
     * Returns a cached preview-sized Image for the given file, loading and
     * caching it (background-loaded, bounded to requestedWidth) if not
     * already present or if the file has changed since it was cached.
     */
    public static synchronized Image getOrLoad(File file, double requestedWidth, double requestedHeight)
    {
        String key = buildKey(file, requestedWidth, requestedHeight);

        Image cached = cache.get(key);
        if (cached != null)
        {
            return cached;
        }

        Image image = new Image(
                file.toURI().toString(),
                requestedWidth,
                requestedHeight,
                true,  // preserveRatio
                true,  // smooth
                true   // backgroundLoading
        );

        cache.put(key, image);
        return image;
    }

    private static String buildKey(File file, double width, double height)
    {
        return file.getAbsolutePath() + "|" + file.lastModified() + "|" + (int) width + "x" + (int) height;
    }

    public static synchronized void clear()
    {
        cache.clear();
    }

    public static synchronized int size()
    {
        return cache.size();
    }
}