package com.lensora.lensorastudio.managers;

import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.concurrent.Task;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;

/**
 * Background task for copying multiple files/folders with progress, speed, ETA.
 */
public class FileCopyTask extends Task<Void>
{
    private final List<File> sources;
    private final File target;
    private long totalBytes = 0;
    private long copiedBytes = 0;
    private long startTime = 0;
    private long lastUpdateTime = 0;
    private long lastCopiedBytes = 0;

    private final DoubleProperty speed = new SimpleDoubleProperty(0);
    private final LongProperty eta = new SimpleLongProperty(0);

    public FileCopyTask(List<File> sources, File target)
    {
        this.sources = sources;
        this.target = target;
    }

    private long getSize(File file)
    {
        if (isCancelled()) return 0;
        if (file.isDirectory())
        {
            long size = 0;
            File[] children = file.listFiles();
            if (children != null)
            {
                for (File child : children)
                {
                    size += getSize(child);
                }
            }
            return size;
        }
        else
        {
            return file.length();
        }
    }

    @Override
    protected Void call() throws Exception
    {
        // Indeterminate progress while scanning — progress < 0 tells any
        // bound ProgressBar to show its indeterminate animation instead
        // of sitting at 0%.
        updateProgress(-1, 1);
        updateMessage("Calculating size...");

        for (File src : sources)
        {
            if (isCancelled()) return null;
            totalBytes += getSize(src);
        }

        updateProgress(0, totalBytes);
        updateMessage("Copying...");

        startTime = System.currentTimeMillis();
        lastUpdateTime = startTime;
        for (File src : sources)
        {
            if (isCancelled()) break;
            copyRecursive(src, new File(target, src.getName()));
            updateProgress(copiedBytes, totalBytes);
        }
        return null;
    }

    private void copyRecursive(File src, File dest) throws IOException
    {
        if (isCancelled()) return;
        if (src.isDirectory())
        {
            if (!dest.exists())
            {
                Files.createDirectories(dest.toPath());
            }
            File[] children = src.listFiles();
            if (children != null)
            {
                for (File child : children)
                {
                    copyRecursive(child, new File(dest, child.getName()));
                }
            }
        }
        else
        {
            try (InputStream in = Files.newInputStream(src.toPath());
                OutputStream out = Files.newOutputStream(dest.toPath()))
            {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1)
                {
                    if (isCancelled()) break;
                    out.write(buffer, 0, bytesRead);
                    copiedBytes += bytesRead;
                    long now = System.currentTimeMillis();
                    if (now - lastUpdateTime > 100)
                    {
                        updateProgress(copiedBytes, totalBytes);
                        updateSpeedAndEta(now);
                        lastUpdateTime = now;
                    }
                }
            }
        }
    }

    private void updateSpeedAndEta(long now)
    {
        long timeDiff = now - lastUpdateTime;
        if (timeDiff > 0)
        {
            long bytesDiff = copiedBytes - lastCopiedBytes;
            double speedVal = bytesDiff / (timeDiff / 1000.0);
            lastCopiedBytes = copiedBytes;
            Platform.runLater(() -> {
                speed.set(speedVal);
                if (speedVal > 0)
                {
                    long remainingBytes = totalBytes - copiedBytes;
                    long etaSeconds = (long) (remainingBytes / speedVal);
                    eta.set(etaSeconds);
                }
                else
                {
                    eta.set(0);
                }
            });
        }
    }

    public DoubleProperty speedProperty() { return speed; }
    public LongProperty etaProperty() { return eta; }
    public double getSpeed() { return speed.get(); }
    public long getEta() { return eta.get(); }
}