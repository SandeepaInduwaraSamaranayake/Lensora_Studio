package com.lensora.lensorastudio.feature.backup.repository;

import com.lensora.lensorastudio.core.db.DatabaseManager;
import com.lensora.lensorastudio.feature.backup.model.BackupHistoryItem;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BackupHistoryRepository
{
    public static int insert(BackupHistoryItem item) throws SQLException
    {
        String sql = """
            INSERT INTO backup_history (
                schedule_id, schedule_name, project_id, project_number, client_name,
                file_path, file_size, total_files, status, error_message, verified,
                started_at, completed_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            bind(ps, item);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys())
            {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public static void updateVerified(int historyId, boolean verified) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement("UPDATE backup_history SET verified=? WHERE history_id=?"))
        {
            ps.setBoolean(1, verified);
            ps.setInt(2, historyId);
            ps.executeUpdate();
        }
    }

    public static void delete(int historyId) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement("DELETE FROM backup_history WHERE history_id=?"))
        {
            ps.setInt(1, historyId);
            ps.executeUpdate();
        }
    }

    public static List<BackupHistoryItem> findAll() throws SQLException
    {
        List<BackupHistoryItem> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM backup_history ORDER BY started_at DESC");
            ResultSet rs = ps.executeQuery())
        {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public static List<BackupHistoryItem> findByProject(int projectId) throws SQLException
    {
        List<BackupHistoryItem> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM backup_history WHERE project_id=? ORDER BY started_at DESC"))
        {
            ps.setInt(1, projectId);
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next()) list.add(map(rs));
            }
        }
        return list;
    }

    private static void bind(PreparedStatement ps, BackupHistoryItem item) throws SQLException
    {
        if (item.getScheduleId() != null) ps.setInt(1, item.getScheduleId()); else ps.setNull(1, Types.INTEGER);
        ps.setString(2, item.getScheduleName());
        ps.setInt(3, item.getProjectId());
        ps.setString(4, item.getProjectNumber());
        ps.setString(5, item.getClientName());
        ps.setString(6, item.getFilePath());
        ps.setLong(7, item.getFileSize());
        ps.setInt(8, item.getTotalFiles());
        ps.setString(9, item.getStatus());
        ps.setString(10, item.getErrorMessage());
        ps.setBoolean(11, item.isVerified());
        ps.setString(12, item.getStartedAt() != null ? item.getStartedAt().toString() : LocalDateTime.now().toString());
        ps.setString(13, item.getCompletedAt() != null ? item.getCompletedAt().toString() : null);
    }

    private static BackupHistoryItem map(ResultSet rs) throws SQLException
    {
        BackupHistoryItem item = new BackupHistoryItem();
        item.setHistoryId(rs.getInt("history_id"));

        int scheduleId = rs.getInt("schedule_id");
        item.setScheduleId(rs.wasNull() ? null : scheduleId);

        item.setScheduleName(rs.getString("schedule_name"));
        item.setProjectId(rs.getInt("project_id"));
        item.setProjectNumber(rs.getString("project_number"));
        item.setClientName(rs.getString("client_name"));
        item.setFilePath(rs.getString("file_path"));
        item.setFileSize(rs.getLong("file_size"));
        item.setTotalFiles(rs.getInt("total_files"));
        item.setStatus(rs.getString("status"));
        item.setErrorMessage(rs.getString("error_message"));
        item.setVerified(rs.getBoolean("verified"));

        String started = rs.getString("started_at");
        if (started != null) item.setStartedAt(LocalDateTime.parse(started));

        String completed = rs.getString("completed_at");
        if (completed != null) item.setCompletedAt(LocalDateTime.parse(completed));

        return item;
    }
}