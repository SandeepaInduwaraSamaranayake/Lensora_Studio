package com.lensora.lensorastudio.feature.project.repository;

import com.lensora.lensorastudio.core.db.DatabaseManager;
import com.lensora.lensorastudio.feature.project.model.ProjectNote;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ProjectNoteRepository
{
    public static int insert(ProjectNote note) throws SQLException
    {
        String sql = """
            INSERT INTO project_note (project_id, note_title, note_content, created_at)
            VALUES (?, ?, ?, ?)
            """;

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            ps.setInt(1, note.getProjectId());
            ps.setString(2, note.getNoteTitle());
            ps.setString(3, note.getNoteContent());
            ps.setString(4, LocalDateTime.now().toString());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys())
            {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public static void update(ProjectNote note) throws SQLException
    {
        String sql = "UPDATE project_note SET note_title=?, note_content=? WHERE note_id=?";

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, note.getNoteTitle());
            ps.setString(2, note.getNoteContent());
            ps.setInt(3, note.getNoteId());
            ps.executeUpdate();
        }
    }

    public static void delete(int noteId) throws SQLException
    {
        String sql = "DELETE FROM project_note WHERE note_id=?";

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setInt(1, noteId);
            ps.executeUpdate();
        }
    }

    /** Returns all notes for a project, most recent first. */
    public static List<ProjectNote> findByProject(int projectId) throws SQLException
    {
        String sql = "SELECT * FROM project_note WHERE project_id=? ORDER BY created_at DESC";
        List<ProjectNote> list = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    private static ProjectNote map(ResultSet rs) throws SQLException
    {
        ProjectNote note = new ProjectNote();
        note.setNoteId(rs.getInt("note_id"));
        note.setProjectId(rs.getInt("project_id"));
        note.setNoteTitle(rs.getString("note_title"));
        note.setNoteContent(rs.getString("note_content"));

        String ca = rs.getString("created_at");
        if (ca != null)
        {
            note.setCreatedAt(LocalDateTime.parse(ca.replace(" ", "T").substring(0, 19)));
        }
        return note;
    }
}