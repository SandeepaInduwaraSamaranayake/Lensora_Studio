package com.lensora.lensorastudio.feature.project.repository;

import com.lensora.lensorastudio.core.db.DatabaseManager;
import com.lensora.lensorastudio.feature.project.model.Project;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * All database operations for the {@code project} table.
 * Every method opens its own connection and closes it - no connection leaking.
 */
public class ProjectRepository
{
    // ─── Insert ───────────────────────────────────────────────────────────────

    /**
     * Inserts a new project and returns the generated {@code project_id}.
     * Returns {@code -1} on failure.
     */
    public static int insert(Project p) throws SQLException
    {
        String sql = """
            INSERT INTO project (
                project_number, client_name, client_phone, client_email,
                event_type, event_date, due_date, project_status, project_path,
                package_name, total_amount, advance_amount, balance_amount,
                remarks, created_at, updated_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            ps.setString(1,  p.getProjectNumber());
            ps.setString(2,  p.getClientName());
            ps.setString(3,  p.getClientPhone());
            ps.setString(4,  p.getClientEmail());
            ps.setString(5,  p.getEventType());
            ps.setString(6,  p.getEventDate()   != null ? p.getEventDate().toString()   : null);
            ps.setString(7,  p.getDueDate()     != null ? p.getDueDate().toString()     : null);
            ps.setString(8,  p.getProjectStatus());
            ps.setString(9,  p.getProjectPath());
            ps.setString(10, p.getPackageName());
            setBigDecimal(ps, 11, p.getTotalAmount());
            setBigDecimal(ps, 12, p.getAdvanceAmount());
            setBigDecimal(ps, 13, p.getBalanceAmount());
            ps.setString(14, p.getRemarks());
            ps.setString(15, LocalDateTime.now().toString());
            ps.setString(16, LocalDateTime.now().toString());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys())
            {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    // ─── Update ───────────────────────────────────────────────────────────────

    public static void update(Project p) throws SQLException
    {
        String sql = """
            UPDATE project SET
                client_name=?, client_phone=?, client_email=?,
                event_type=?, event_date=?, due_date=?, project_status=?,
                project_path=?, package_name=?,
                total_amount=?, advance_amount=?, balance_amount=?,
                remarks=?, updated_at=?
            WHERE project_id=?
            """;

        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1,  p.getClientName());
            ps.setString(2,  p.getClientPhone());
            ps.setString(3,  p.getClientEmail());
            ps.setString(4,  p.getEventType());
            ps.setString(5,  p.getEventDate()  != null ? p.getEventDate().toString()  : null);
            ps.setString(6,  p.getDueDate()    != null ? p.getDueDate().toString()    : null);
            ps.setString(7,  p.getProjectStatus());
            ps.setString(8,  p.getProjectPath());
            ps.setString(9,  p.getPackageName());
            setBigDecimal(ps, 10, p.getTotalAmount());
            setBigDecimal(ps, 11, p.getAdvanceAmount());
            setBigDecimal(ps, 12, p.getBalanceAmount());
            ps.setString(13, p.getRemarks());
            ps.setString(14, LocalDateTime.now().toString());
            ps.setInt   (15, p.getProjectId());
            ps.executeUpdate();
        }
    }

    // ─── Delete / Archive ─────────────────────────────────────────────────────

    public static void setStatus(int projectId, String status) throws SQLException
    {
        String sql = "UPDATE project SET project_status=?, updated_at=? WHERE project_id=?";
        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, status);
            ps.setString(2, LocalDateTime.now().toString());
            ps.setInt(3, projectId);
            ps.executeUpdate();
        }
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    /** Returns all projects ordered by created_at DESC. */
    public static List<Project> findAll() throws SQLException
    {
        String sql = "SELECT * FROM project ORDER BY created_at DESC";
        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery())
        {
            return mapList(rs);
        }
    }

    /**
     * Searches projects by text query and optional status filter.
     * If query is null/empty, only status filter is applied.
     * If status is null/empty or "All Statuses", no status filter is applied.
     */
    public static List<Project> search(String query, String status) throws SQLException 
    {
        StringBuilder sql = new StringBuilder("SELECT * FROM project WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (query != null && !query.trim().isEmpty()) 
        {
            String like = "%" + query.toLowerCase() + "%";      // for text fields (case‑insensitive)
            String dateLike = "%" + query + "%";                // for date field (no lowercasing)

            sql.append(" AND (")
            .append(" lower(project_number) LIKE ?")
            .append(" OR lower(client_name)    LIKE ?")
            .append(" OR lower(client_phone)   LIKE ?")
            .append(" OR lower(project_status) LIKE ?")
            .append(" OR lower(event_type)     LIKE ?")
            .append(" OR lower(client_email)   LIKE ?")
            .append(" OR event_date            LIKE ?")
            .append(")");
            // Add 6 text‑field parameters + 1 date parameter
            for (int i = 0; i < 6; i++) params.add(like);
            params.add(dateLike);   // date uses the non‑lowercased pattern
        }

        if (status != null && !status.isEmpty() && !status.equals("All Statuses")) 
        {
            sql.append(" AND project_status = ?");
            params.add(status);
        }

        sql.append(" ORDER BY created_at DESC");

        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement(sql.toString())) 
            {
                for (int i = 0; i < params.size(); i++) 
                {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) 
                {
                    return mapList(rs);
                }
        }
    }

    /** Loads a single project by id. Returns null if not found. */
    public static Project findById(int id) throws SQLException
    {
        String sql = "SELECT * FROM project WHERE project_id=?";
        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery())
            {
                if (rs.next()) return map(rs);
            }
        }
        return null;
    }

    /**
     * Returns the next sequential number for the given event type prefix.
     * E.g. for "WED" scans project_number LIKE 'WED-%' and returns max+1.
     */
    public static int nextSequence(String prefix) throws SQLException
    {
        String sql = "SELECT project_number FROM project WHERE project_number LIKE ?";
        int max = 0;
        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setString(1, prefix + "%");
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next())
                {
                    String num = rs.getString("project_number");
                    // Extract trailing digits
                    String digits = num.replaceAll(".*?(\\d+)$", "$1");
                    try { int n = Integer.parseInt(digits); if (n > max) max = n; }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return max + 1;
    }

    // ─── Mapping ──────────────────────────────────────────────────────────────

    private static List<Project> mapList(ResultSet rs) throws SQLException
    {
        List<Project> list = new ArrayList<>();
        while (rs.next()) list.add(map(rs));
        return list;
    }

    private static Project map(ResultSet rs) throws SQLException
    {
        Project p = new Project();
        p.setProjectId    (rs.getInt   ("project_id"));
        p.setProjectNumber(rs.getString("project_number"));
        p.setClientName   (rs.getString("client_name"));
        p.setClientPhone  (rs.getString("client_phone"));
        p.setClientEmail  (rs.getString("client_email"));
        p.setEventType    (rs.getString("event_type"));
        p.setProjectStatus(rs.getString("project_status"));
        p.setProjectPath  (rs.getString("project_path"));
        p.setPackageName  (rs.getString("package_name"));
        p.setRemarks      (rs.getString("remarks"));

        String ed = rs.getString("event_date");
        if (ed != null) p.setEventDate(LocalDate.parse(ed.substring(0, 10)));

        String dd = rs.getString("due_date");
        if (dd != null) p.setDueDate(LocalDate.parse(dd.substring(0, 10)));

        String ta = rs.getString("total_amount");
        if (ta != null) p.setTotalAmount(new BigDecimal(ta));

        String aa = rs.getString("advance_amount");
        if (aa != null) p.setAdvanceAmount(new BigDecimal(aa));

        String ba = rs.getString("balance_amount");
        if (ba != null) p.setBalanceAmount(new BigDecimal(ba));

        String ca = rs.getString("created_at");
        if (ca != null) p.setCreatedAt(LocalDateTime.parse(ca.replace(" ", "T").substring(0, 19)));

        String ua = rs.getString("updated_at");
        if (ua != null) p.setUpdatedAt(LocalDateTime.parse(ua.replace(" ", "T").substring(0, 19)));

        return p;
    }

    private static void setBigDecimal(PreparedStatement ps, int idx, BigDecimal val) throws SQLException
    {
        if (val != null) ps.setBigDecimal(idx, val);
        else             ps.setNull(idx, Types.DECIMAL);
    }
}