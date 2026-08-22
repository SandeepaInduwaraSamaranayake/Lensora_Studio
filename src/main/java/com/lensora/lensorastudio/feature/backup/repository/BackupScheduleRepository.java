package com.lensora.lensorastudio.feature.backup.repository;

import com.lensora.lensorastudio.core.db.DatabaseManager;
import com.lensora.lensorastudio.feature.backup.model.BackupSchedule;
import com.google.gson.Gson;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BackupScheduleRepository
{
    private static final Gson GSON = new Gson();

    public static int insert(BackupSchedule s) throws SQLException
    {
        String sql = """
            INSERT INTO backup_schedule (
                name, scope, project_ids, destination_path, frequency,
                interval_value, time_of_day, day_of_week, enabled,
                last_run, next_run, created_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            bind(ps, s);
            ps.setString(12, LocalDateTime.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys())
            {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public static void update(BackupSchedule s) throws SQLException
    {
        String sql = """
            UPDATE backup_schedule SET
                name=?, scope=?, project_ids=?, destination_path=?, frequency=?,
                interval_value=?, time_of_day=?, day_of_week=?, enabled=?,
                last_run=?, next_run=?
            WHERE schedule_id=?
            """;
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(sql))
        {
            bind(ps, s);
            ps.setInt(12, s.getScheduleId());
            ps.executeUpdate();
        }
    }

    public static void delete(int scheduleId) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement("DELETE FROM backup_schedule WHERE schedule_id=?"))
        {
            ps.setInt(1, scheduleId);
            ps.executeUpdate();
        }
    }

    public static List<BackupSchedule> findAll() throws SQLException
    {
        List<BackupSchedule> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM backup_schedule ORDER BY created_at DESC");
                ResultSet rs = ps.executeQuery())
        {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public static List<BackupSchedule> findEnabled() throws SQLException
    {
        List<BackupSchedule> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement("SELECT * FROM backup_schedule WHERE enabled=1");
                ResultSet rs = ps.executeQuery())
        {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    private static void bind(PreparedStatement ps, BackupSchedule s) throws SQLException
    {
        ps.setString(1, s.getName());
        ps.setString(2, s.getScope().name());
        ps.setString(3, s.getProjectIds() != null ? GSON.toJson(s.getProjectIds()) : null);
        ps.setString(4, s.getDestinationPath());
        ps.setString(5, s.getFrequency().name());
        ps.setInt(6, s.getIntervalValue());
        ps.setString(7, s.getTimeOfDay());
        if (s.getDayOfWeek() != null) ps.setInt(8, s.getDayOfWeek()); else ps.setNull(8, Types.INTEGER);
        ps.setBoolean(9, s.isEnabled());
        ps.setString(10, s.getLastRun() != null ? s.getLastRun().toString() : null);
        ps.setString(11, s.getNextRun() != null ? s.getNextRun().toString() : null);
    }

    private static BackupSchedule map(ResultSet rs) throws SQLException
    {
        BackupSchedule s = new BackupSchedule();
        s.setScheduleId(rs.getInt("schedule_id"));
        s.setName(rs.getString("name"));
        s.setScope(BackupSchedule.Scope.valueOf(rs.getString("scope")));

        String projectIdsJson = rs.getString("project_ids");
        if (projectIdsJson != null)
        {
            Integer[] ids = GSON.fromJson(projectIdsJson, Integer[].class);
            s.setProjectIds(Arrays.asList(ids));
        }

        s.setDestinationPath(rs.getString("destination_path"));
        s.setFrequency(BackupSchedule.Frequency.valueOf(rs.getString("frequency")));
        s.setIntervalValue(rs.getInt("interval_value"));
        s.setTimeOfDay(rs.getString("time_of_day"));

        int dow = rs.getInt("day_of_week");
        s.setDayOfWeek(rs.wasNull() ? null : dow);

        s.setEnabled(rs.getBoolean("enabled"));

        String lastRun = rs.getString("last_run");
        if (lastRun != null) s.setLastRun(LocalDateTime.parse(lastRun));

        String nextRun = rs.getString("next_run");
        if (nextRun != null) s.setNextRun(LocalDateTime.parse(nextRun));

        String createdAt = rs.getString("created_at");
        if (createdAt != null) s.setCreatedAt(LocalDateTime.parse(createdAt));

        return s;
    }
}