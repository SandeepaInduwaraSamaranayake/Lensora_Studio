package com.lensora.lensorastudio.feature.project.repository;

import java.sql.*;

import com.lensora.lensorastudio.core.db.DatabaseManager;

public class ProjectLastFolderRepository
{
    /** Returns the relative path, or null if none stored yet. */
    public static String findByProject(int projectId) throws SQLException
    {
        String sql = "SELECT relative_path FROM project_last_folder WHERE project_id=?";
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() ? rs.getString("relative_path") : null;
            }
        }
    }

    public static void save(int projectId, String relativePath) throws SQLException
    {
        String sql = """
            INSERT INTO project_last_folder (project_id, relative_path)
            VALUES (?, ?)
            ON CONFLICT(project_id) DO UPDATE SET relative_path = excluded.relative_path
            """;
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setInt(1, projectId);
            ps.setString(2, relativePath);
            ps.executeUpdate();
        }
    }
}