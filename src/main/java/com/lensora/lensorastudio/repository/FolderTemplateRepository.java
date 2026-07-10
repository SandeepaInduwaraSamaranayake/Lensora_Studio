package com.lensora.lensorastudio.repository;

import com.lensora.lensorastudio.services.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DB access for folder_template + folder_template_item.
 */
public class FolderTemplateRepository
{
    
    public record FolderTemplate(int id, String name, String description) {}

    /** Returns all templates. */
    public static List<FolderTemplate> findAll() throws SQLException
    {
        String sql = "SELECT * FROM folder_template ORDER BY template_name";
        List<FolderTemplate> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery())
        {
            while (rs.next())
                list.add(new FolderTemplate(
                    rs.getInt("template_id"),
                    rs.getString("template_name"),
                    rs.getString("description")));
        }
        return list;
    }

    /**
     * Returns the ordered folder names for a given template.
     * Key = sequence_no, Value = folder_name.
     */
    public static List<String> getFolderNames(int templateId) throws SQLException
    {
        String sql = "SELECT folder_name FROM folder_template_item " +
                    "WHERE template_id=? ORDER BY sequence_no";
        List<String> names = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect();
            PreparedStatement ps = conn.prepareStatement(sql))
        {
            ps.setInt(1, templateId);
            try (ResultSet rs = ps.executeQuery())
            {
                while (rs.next()) names.add(rs.getString("folder_name"));
            }
        }
        return names;
    }

    /** Inserts a template + its folder items in a single transaction. */
    public static int insert(String name, String description, List<String> folders) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect())
        {
            conn.setAutoCommit(false);
            int templateId;
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO folder_template(template_name,description) VALUES(?,?)",
                    Statement.RETURN_GENERATED_KEYS))
            {
                ps.setString(1, name);
                ps.setString(2, description);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) { templateId = keys.getInt(1); }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO folder_template_item(template_id,folder_name,sequence_no) VALUES(?,?,?)"))
            {
                for (int i = 0; i < folders.size(); i++)
                {
                    ps.setInt(1, templateId);
                    ps.setString(2, folders.get(i).trim());
                    ps.setInt(3, i + 1);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            conn.commit();
            return templateId;
        }
    }
}