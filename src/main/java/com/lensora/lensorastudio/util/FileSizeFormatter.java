package com.lensora.lensorastudio.util;

public final class FileSizeFormatter
{
    private FileSizeFormatter() {}

    public static String formatFileSize(long size)
    {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
    }

    public static String formatSpeed(double bytesPerSecond)
    {
        if (bytesPerSecond < 1024) return String.format("%.0f B/s", bytesPerSecond);
        if (bytesPerSecond < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSecond / 1024);
        return String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024));
    }

    public static String formatEta(long seconds)
    {
        if (seconds <= 0) return "ETA: --";
        long mins = seconds / 60;
        long secs = seconds % 60;
        if (mins > 0) return String.format("ETA: %d min %d s", mins, secs);
        return String.format("ETA: %d s", secs);
    }
}