package com.lensora.lensorastudio.feature.explorer.repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import com.lensora.lensorastudio.core.db.DatabaseManager;

public class FileTagRepository
{
    public static void addTag(String filePath, String tag) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO file_tag (file_path, tag_name) VALUES (?,?)"))
        {
            ps.setString(1, filePath);
            ps.setString(2, tag.toLowerCase().trim());
            ps.executeUpdate();
        }
    }

    public static void removeTag(String filePath, String tag) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM file_tag WHERE file_path=? AND tag_name=?"))
        {
            ps.setString(1, filePath);
            ps.setString(2, tag.toLowerCase().trim());
            ps.executeUpdate();
        }
    }

    public static List<String> findPathsByTag(String tag) throws SQLException
    {
        List<String> paths = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement("SELECT file_path FROM file_tag WHERE tag_name=?"))
        {
            ps.setString(1, tag.toLowerCase().trim());
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next()) paths.add(rs.getString("file_path"));
            }
        }
        return paths;
    }

    public static List<String> findTagsForFile(String filePath) throws SQLException
    {
        List<String> tags = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement("SELECT tag_name FROM file_tag WHERE file_path=?"))
        {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next()) tags.add(rs.getString("tag_name"));
            }
        }
        return tags;
    }
}