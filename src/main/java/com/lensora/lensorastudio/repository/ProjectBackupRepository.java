package com.lensora.lensorastudio.repository;

import com.lensora.lensorastudio.backup.model.ProjectBackupData;
import com.lensora.lensorastudio.services.DatabaseManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.sql.ResultSetMetaData;

public class ProjectBackupRepository
{
    /** Exports a project and all its related rows (notes, reminders, payments, deliverables) as plain maps. */
    public static ProjectBackupData exportProject(int projectId) throws SQLException
    {
        ProjectBackupData data = new ProjectBackupData();

        try (Connection conn = DatabaseManager.connect())
        {
            data.project = queryOne(conn, "SELECT * FROM project WHERE project_id=?", projectId);
            data.notes = queryMany(conn, "SELECT * FROM project_note WHERE project_id=?", projectId);
            data.reminders = queryMany(conn, "SELECT * FROM reminder WHERE project_id=?", projectId);
            data.payments = queryMany(conn, "SELECT * FROM payment WHERE project_id=?", projectId);
            data.deliverables = queryMany(conn, "SELECT * FROM deliverable WHERE project_id=?", projectId);
        }

        return data;
    }

    /**
     * Inserts a project's data as a NEW project (fresh project_id), remapping all
     * child-table foreign keys to the new id. If the original project_number already
     * exists, a numeric suffix is appended to keep the UNIQUE constraint satisfied.
     *
     * @return the newly created project_id
     */
    public static int importProject(ProjectBackupData data, String newProjectPath) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect())
        {
            conn.setAutoCommit(false);
            try
            {
                int newProjectId = insertProject(conn, data.project, newProjectPath);

                if (data.notes != null)
                {
                    for (Map<String, Object> row : data.notes) insertChildRow(conn, "project_note",
                            "note_id", newProjectId, row);
                }
                if (data.reminders != null)
                {
                    for (Map<String, Object> row : data.reminders) insertChildRow(conn, "reminder",
                            "reminder_id", newProjectId, row);
                }
                if (data.payments != null)
                {
                    for (Map<String, Object> row : data.payments) insertChildRow(conn, "payment",
                            "payment_id", newProjectId, row);
                }
                if (data.deliverables != null)
                {
                    for (Map<String, Object> row : data.deliverables) insertChildRow(conn, "deliverable",
                            "deliverable_id", newProjectId, row);
                }

                conn.commit();
                return newProjectId;
            }
            catch (SQLException e)
            {
                conn.rollback();
                throw e;
            }
        }
    }

    // ─── Export helpers ─────────────────────────────────────────────────────

    private static Map<String, Object> queryOne(Connection conn, String sql, int id) throws SQLException
    {
        try (PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next()) return rowToMap(rs);
            }
        }
        return null;
    }

    private static List<Map<String, Object>> queryMany(Connection conn, String sql, int id) throws SQLException
    {
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next()) list.add(rowToMap(rs));
            }
        }
        return list;
    }

    private static Map<String, Object> rowToMap(ResultSet rs) throws SQLException
    {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++)
        {
            row.put(meta.getColumnName(i), rs.getObject(i));
        }
        return row;
    }

    // ─── Import helpers ─────────────────────────────────────────────────────

    private static int insertProject(Connection conn, Map<String, Object> row, String newProjectPath) throws SQLException
    {
        String baseNumber = String.valueOf(row.get("project_number"));
        String uniqueNumber = ensureUniqueProjectNumber(conn, baseNumber);

        String sql = """
            INSERT INTO project (
                project_number, client_name, client_phone, client_email,
                event_type, event_date, due_date, project_status, project_path,
                package_name, total_amount, advance_amount, balance_amount,
                remarks, created_at, updated_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            ps.setString(1, uniqueNumber);
            ps.setString(2, str(row, "client_name"));
            ps.setString(3, str(row, "client_phone"));
            ps.setString(4, str(row, "client_email"));
            ps.setString(5, str(row, "event_type"));
            ps.setString(6, str(row, "event_date"));
            ps.setString(7, str(row, "due_date"));
            ps.setString(8, str(row, "project_status"));
            ps.setString(9, newProjectPath);
            ps.setString(10, str(row, "package_name"));
            ps.setObject(11, row.get("total_amount"));
            ps.setObject(12, row.get("advance_amount"));
            ps.setObject(13, row.get("balance_amount"));
            ps.setString(14, str(row, "remarks"));
            ps.setString(15, LocalDateTime.now().toString());
            ps.setString(16, LocalDateTime.now().toString());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys())
            {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to insert restored project");
    }

    private static String ensureUniqueProjectNumber(Connection conn, String baseNumber) throws SQLException
    {
        String candidate = baseNumber;
        int suffix = 1;
        while (projectNumberExists(conn, candidate))
        {
            suffix++;
            candidate = baseNumber + "-RESTORED" + (suffix > 2 ? suffix : "");
        }
        return candidate;
    }

    private static boolean projectNumberExists(Connection conn, String number) throws SQLException
    {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM project WHERE project_number=?"))
        {
            ps.setString(1, number);
            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next();
            }
        }
    }

    private static void insertChildRow(Connection conn, String table, String idColumn, int newProjectId,
                                        Map<String, Object> row) throws SQLException
    {
        List<String> columns = new ArrayList<>();
        List<Object> values = new ArrayList<>();

        for (Map.Entry<String, Object> entry : row.entrySet())
        {
            String col = entry.getKey();
            if (col.equals(idColumn)) continue; // let AUTOINCREMENT assign a fresh id
            columns.add(col);
            values.add(col.equals("project_id") ? newProjectId : entry.getValue());
        }

        String placeholders = String.join(",", columns.stream().map(c -> "?").toList());
        String sql = "INSERT INTO " + table + " (" + String.join(",", columns) + ") VALUES (" + placeholders + ")";

        try (PreparedStatement ps = conn.prepareStatement(sql))
        {
            for (int i = 0; i < values.size(); i++)
            {
                ps.setObject(i + 1, values.get(i));
            }
            ps.executeUpdate();
        }
    }

    private static String str(Map<String, Object> row, String key)
    {
        Object v = row.get(key);
        return v != null ? v.toString() : null;
    }
}