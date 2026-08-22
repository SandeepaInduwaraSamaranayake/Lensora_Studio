package com.lensora.lensorastudio.media.cache;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lensora.lensorastudio.core.config.AppPaths;
import com.lensora.lensorastudio.core.config.AppSettings;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two-tier bounded cache for JavaFX Images:
 *   1. In-memory LRU - fast RAM access.
 *   2. On-disk PNG cache - survives app restarts; auto-evicts oldest entries.
 */
public final class ImageCache 
{
    private static final Logger logger = LoggerFactory.getLogger(ImageCache.class);

    private static volatile int maxMemoryEntries    = AppSettings.getInstance().getImageCacheMemoryEntries();
    private static volatile int maxDiskEntries      = AppSettings.getInstance().getImageCacheDiskEntries();

    private static final Map<String, Image> memoryCache =
            new LinkedHashMap<>(16, 0.75f, true) 
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) 
                {
                    return size() > maxMemoryEntries;
                }
            };

    private static final Path diskCacheDir = AppPaths.getCacheDirectory("LensoraStudio");

    private static final ExecutorService diskWriteExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Lensora-image-cache-disk-writer");
        t.setDaemon(true);
        return t;
    });

    static
    {
        try 
        {
            Files.createDirectories(diskCacheDir);
        } 
        catch (IOException e)
        {
            logger.warn("[ImageCache] Could not create disk cache directory: {}", diskCacheDir, e);
        }
    }

    private ImageCache() {}

    public static void setMaxMemoryEntries(int limit) 
    {
        maxMemoryEntries = Math.max(50, limit);
    }

    public static void setMaxDiskEntries(int limit) 
    {
        maxDiskEntries = Math.max(50, limit);
    }

    public static synchronized int getMemorySize() 
    {
        return memoryCache.size();
    }

    public static int getDiskSize() 
    {
        File[] files = diskCacheDir.toFile().listFiles((dir, name) -> name.endsWith(".png"));
        return files != null ? files.length : 0;
    }

    public static Image getOrLoad(File file, double requestedWidth, double requestedHeight)
    {
        return getOrLoad(file, requestedWidth, requestedHeight, true);
    }

    /**
     * @param backgroundLoading pass false when the caller is already
     *        executing off the FX thread inside its own bounded thread pool
     *        (e.g. ThumbnailService) — letting JavaFX ALSO spin up its own
     *        unbounded background-loading threads on top defeats any
     *        external concurrency cap.
     */
    public static Image getOrLoad(File file, double requestedWidth, double requestedHeight, boolean backgroundLoading)
    {
        String key = buildKey(file, requestedWidth, requestedHeight);

        Image memHit = getFromMemory(key);
        if (memHit != null) return memHit;

        Image diskHit = loadFromDisk(key);
        if (diskHit != null)
        {
            putInMemory(key, diskHit);
            return diskHit;
        }

        Image freshImage = new Image(
                file.toURI().toString(),
                requestedWidth, requestedHeight,
                true, true,
                backgroundLoading
        );

        putInMemory(key, freshImage);
        scheduleDiskWrite(key, freshImage);
        return freshImage;
    }

    // ─── Memory Tier ────────────────────────────────────────────────────────

    private static synchronized Image getFromMemory(String key) 
    {
        return memoryCache.get(key);
    }

    private static synchronized void putInMemory(String key, Image image) 
    {
        memoryCache.put(key, image);
    }

    // ─── Disk Tier ──────────────────────────────────────────────────────────

    private static Image loadFromDisk(String key) 
    {
        File diskFile = diskFileFor(key);
        if (!diskFile.exists()) return null;

        try 
        {
            Image image = new Image(diskFile.toURI().toString(), true);
            diskFile.setLastModified(System.currentTimeMillis()); // Touch file for LRU disk policy
            return image;
        } 
        catch (Exception e) 
        {
            logger.debug("[ImageCache] Failed to load disk-cached image for key {}", key, e);
            return null;
        }
    }

    /**
     * Writes the image to disk off the caller's thread - background-loaded
     * JavaFX Images may still be decoding, so the actual write waits for
     * the image to finish loading before encoding it, without blocking
     * the caller either way.
     */
    private static void scheduleDiskWrite(String key, Image image) 
    {
        diskWriteExecutor.submit(() -> {
            try 
            {
                if (image.isError()) return;

                // Wait for background loading to complete before encoding.
                // getOrLoad() already returned the live Image to the caller,
                // this only affects when the disk copy is written.
                while (image.getProgress() < 1.0 && !image.isError()) 
                {
                    Thread.sleep(20);
                }
                if (image.isError()) return;

                writeToDisk(key, image);
                enforceDiskCacheLimit();
            } 
            catch (InterruptedException ignored) 
            {
                Thread.currentThread().interrupt();
            } 
            catch (Exception e) 
            {
                logger.debug("[ImageCache] Failed to write disk cache entry for key {}", key, e);
            }
        });
    }

    private static void writeToDisk(String key, Image image) throws IOException 
    {
        File diskFile = diskFileFor(key);
        if (diskFile.exists()) return;  // another writer already cached this key

        // Convert JavaFX Image -> BufferedImage -> PNG on disk.
        BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);
        if (bufferedImage != null)
        {
            ImageIO.write(bufferedImage, "png", diskFile);
        }
    }

    private static File diskFileFor(String key) 
    {
        return diskCacheDir.resolve(hash(key) + ".png").toFile();
    }

    /** Caps the disk cache by entry count, evicting least-recently-touched files first. */
    private static void enforceDiskCacheLimit() 
    {
        File[] files = diskCacheDir.toFile().listFiles((dir, name) -> name.endsWith(".png"));
        if (files == null || files.length <= maxDiskEntries) return;

        Arrays.sort(files, Comparator.comparingLong(File::lastModified));

        int toRemove = files.length - maxDiskEntries;
        for (int i = 0; i < toRemove; i++) 
        {
            files[i].delete();
        }
    }

    // ─── Eviction & Maintenance ─────────────────────────────────────────────

    public static synchronized void evictAllForFile(File file)
    {
        if (file == null) return;
        String pathPrefix = file.getAbsolutePath() + "|";

        memoryCache.keySet().removeIf(key -> {
            if (key.startsWith(pathPrefix)) 
            {
                File diskFile = diskFileFor(key);
                if (diskFile.exists()) diskFile.delete();
                return true;
            }
            return false;
        });
    }

    public static synchronized void clearMemory() 
    {
        memoryCache.clear();
    }

    public static void clearDisk() 
    {
        clearDisk(null);
    }

    public static void clearDisk(Runnable onComplete) 
    {
        diskWriteExecutor.submit(() -> {
            try 
            {
                File[] files = diskCacheDir.toFile().listFiles((dir, name) -> name.endsWith(".png"));
                if (files != null) 
                {
                    for (File f : files) f.delete();
                }
            } 
            finally 
            {
                if (onComplete != null) 
                {
                    Platform.runLater(onComplete);
                }
            }
        });
    }

    /**
     * Clears both the in-memory RAM cache and the persistent disk cache.
     */
    public static void clearAll() 
    {
        clearAll(null);
    }

    public static void clearAll(Runnable onComplete) 
    {
        clearMemory();
        clearDisk(onComplete);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static String buildKey(File file, double width, double height) 
    {
        return file.getAbsolutePath() + "|" + file.lastModified() + "|" + (int) width + "x" + (int) height;
    }

    /** SHA-256 hash of the cache key, used as the disk filename (avoids illegal path characters). */
    private static String hash(String input) 
    {
        try 
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } 
        catch (Exception e)
        {
            // SHA-256 is always available on any JVM, this should never happen.
            return String.valueOf(input.hashCode());
        }
    }
}