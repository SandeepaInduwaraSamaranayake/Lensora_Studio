package com.lensora.lensorastudio.media.repository;

import java.io.File;
import java.sql.*;
import java.util.Optional;

import com.lensora.lensorastudio.core.db.DatabaseManager;

/**
 * Persists per-file dimension metadata (width/height), keyed by path,
 * invalidated automatically when a file's size or last-modified timestamp
 * so folder re-listing never re-runs metadata extraction for unchanged files, even
 * across app restarts.
 */
public final class ImageCacheRepository
{
    private ImageCacheRepository() {}

    public record CachedDimensions(int width, int height)
    {
        public String format()
        {
            return width + "x" + height;
        }
    }

    /** Returns cached dimensions only if the file's size+lastModified still match what was cached. */
    public static Optional<CachedDimensions> findValidDimensions(File file) throws SQLException
    {
        String sql = """
            SELECT width, height FROM image_cache
            WHERE file_path=? AND file_size=? AND last_modified=?
                AND width IS NOT NULL AND height IS NOT NULL
            """;

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, file.getAbsolutePath());
            ps.setLong(2, file.length());
            ps.setLong(3, file.lastModified());

            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next())
                {
                    return Optional.of(new CachedDimensions(rs.getInt("width"), rs.getInt("height")));
                }
            }
        }
        return Optional.empty();
    }

    /** Upserts dimensions for a file, replacing any stale entry for the same path. */
    public static void saveDimensions(File file, int width, int height) throws SQLException
    {
        String sql = """
            INSERT INTO image_cache (file_path, file_name, extension, file_size, last_modified, width, height)
            VALUES (?,?,?,?,?,?,?)
            ON CONFLICT(file_path) DO UPDATE SET
                file_size=excluded.file_size,
                last_modified=excluded.last_modified,
                width=excluded.width,
                height=excluded.height
            """;

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, file.getAbsolutePath());
            ps.setString(2, file.getName());
            ps.setString(3, extensionOf(file));
            ps.setLong(4, file.length());
            ps.setLong(5, file.lastModified());
            ps.setInt(6, width);
            ps.setInt(7, height);
            ps.executeUpdate();
        }
    }

    /** Removes a stale entry — call when a file is known to have been deleted. */
    public static void remove(File file) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM image_cache WHERE file_path=?"))
        {
            ps.setString(1, file.getAbsolutePath());
            ps.executeUpdate();
        }
    }

    private static String extensionOf(File file)
    {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1).toLowerCase() : "";
    }
}
