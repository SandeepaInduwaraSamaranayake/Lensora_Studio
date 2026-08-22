package com.lensora.lensorastudio.feature.project.repository;

import com.lensora.lensorastudio.core.db.DatabaseManager;
import com.lensora.lensorastudio.feature.project.model.FileRating;

import java.sql.*;
import java.time.LocalDateTime;

public class FileRatingRepository
{
    public static FileRating find(String filePath) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM file_rating WHERE file_path=?"))
        {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    public static void upsert(FileRating rating) throws SQLException
    {
        String sql = """
            INSERT INTO file_rating (file_path, project_id, rating, flag, updated_at)
            VALUES (?,?,?,?,?)
            ON CONFLICT(file_path) DO UPDATE SET
                project_id=excluded.project_id, rating=excluded.rating,
                flag=excluded.flag, updated_at=excluded.updated_at
            """;
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, rating.getFilePath());
            if (rating.getProjectId() != null) ps.setInt(2, rating.getProjectId()); else ps.setNull(2, Types.INTEGER);
            ps.setInt(3, rating.getRating());
            ps.setString(4, rating.getFlag().name());
            ps.setString(5, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    public static java.util.List<String> findPathsByRating(int rating) throws SQLException
    {
        return findPaths("SELECT file_path FROM file_rating WHERE rating>=?", ps -> ps.setInt(1, rating));
    }

    public static java.util.List<String> findPathsByFlag(FileRating.Flag flag) throws SQLException
    {
        return findPaths("SELECT file_path FROM file_rating WHERE flag=?", ps -> ps.setString(1, flag.name()));
    }

    private interface Binder { void bind(PreparedStatement ps) throws SQLException; }

    private static java.util.List<String> findPaths(String sql, Binder binder) throws SQLException
    {
        java.util.List<String> paths = new java.util.ArrayList<>();
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql))
        {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next()) paths.add(rs.getString("file_path"));
            }
        }
        return paths;
    }

    private static FileRating map(ResultSet rs) throws SQLException
    {
        FileRating r = new FileRating();
        r.setFilePath(rs.getString("file_path"));
        int pid = rs.getInt("project_id");
        r.setProjectId(rs.wasNull() ? null : pid);
        r.setRating(rs.getInt("rating"));
        r.setFlag(FileRating.Flag.valueOf(rs.getString("flag")));
        return r;
    }
}