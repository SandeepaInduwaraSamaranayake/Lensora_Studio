package com.lensora.lensorastudio.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generic container for extracted metadata - a simple ordered key/value map
 * per logical group (e.g. "File", "EXIF", "GPS", "Video", "Audio").
 */
public class MediaMetadata
{
    private final String filePath;
    private final MediaType type;
    private final Map<String, Map<String, String>> groups = new LinkedHashMap<>();

    public enum MediaType { IMAGE, VIDEO, UNSUPPORTED }

    public MediaMetadata(String filePath, MediaType type)
    {
        this.filePath = filePath;
        this.type = type;
    }

    public void put(String group, String key, String value)
    {
        groups.computeIfAbsent(group, g -> new LinkedHashMap<>()).put(key, value);
    }

    public String getFilePath() { return filePath; }
    public MediaType getType() { return type; }
    public Map<String, Map<String, String>> getGroups() { return groups; }

    public boolean isEmpty()
    {
        return groups.isEmpty();
    }
}