package com.lensora.lensorastudio.feature.project.repository;

import com.lensora.lensorastudio.core.db.DatabaseManager;
import com.lensora.lensorastudio.feature.explorer.repository.FileTagRepository;
import com.lensora.lensorastudio.feature.project.model.Collection;
import com.lensora.lensorastudio.feature.project.model.FileRating;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CollectionRepository
{
    private static final Gson GSON = new Gson();

    public static List<Collection> findAll() throws SQLException
    {
        List<Collection> list = new ArrayList<>();
        try (Connection conn = DatabaseManager.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM collection ORDER BY is_builtin DESC, name");
                ResultSet rs = ps.executeQuery())
        {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    public static int insertManual(String name, String icon) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO collection (name, icon, type, is_builtin, created_at) VALUES (?,?,?,0,?)",
                        Statement.RETURN_GENERATED_KEYS))
        {
            ps.setString(1, name);
            ps.setString(2, icon);
            ps.setString(3, Collection.Type.MANUAL.name());
            ps.setString(4, LocalDateTime.now().toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys())
            {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public static void delete(int collectionId) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect())
        {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM collection_item WHERE collection_id=?");
                    PreparedStatement ps2 = conn.prepareStatement("DELETE FROM collection WHERE collection_id=? AND is_builtin=0"))
            {
                ps1.setInt(1, collectionId);
                ps1.executeUpdate();
                ps2.setInt(1, collectionId);
                ps2.executeUpdate();
            }
            conn.commit();
        }
    }

    public static void addItem(int collectionId, String filePath) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT OR IGNORE INTO collection_item (collection_id, file_path, added_at) VALUES (?,?,?)"))
        {
            ps.setInt(1, collectionId);
            ps.setString(2, filePath);
            ps.setString(3, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    public static void removeItem(int collectionId, String filePath) throws SQLException
    {
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM collection_item WHERE collection_id=? AND file_path=?"))
        {
            ps.setInt(1, collectionId);
            ps.setString(2, filePath);
            ps.executeUpdate();
        }
    }

    /** Resolves the file paths belonging to a collection — manual (stored) or smart (computed). */
    public static List<String> resolveFilePaths(Collection collection) throws SQLException
    {
        if (collection.getType() == Collection.Type.MANUAL)
        {
            List<String> paths = new ArrayList<>();
            try (Connection conn = DatabaseManager.connect();
                    PreparedStatement ps = conn.prepareStatement(
                            "SELECT file_path FROM collection_item WHERE collection_id=?"))
            {
                ps.setInt(1, collection.getCollectionId());
                ps.setInt(1, collection.getCollectionId());
                try (ResultSet rs = ps.executeQuery())
                {
                    while (rs.next()) paths.add(rs.getString("file_path"));
                }
            }
            return paths;
        }

        // SMART — computed live from criteria JSON.
        JsonObject criteria = GSON.fromJson(collection.getSmartCriteria(), JsonObject.class);
        if (criteria.has("rating"))
        {
            return FileRatingRepository.findPathsByRating(criteria.get("rating").getAsInt());
        }
        if (criteria.has("flag"))
        {
            return FileRatingRepository.findPathsByFlag(FileRating.Flag.valueOf(criteria.get("flag").getAsString()));
        }
        if (criteria.has("tag"))
        {
            return FileTagRepository.findPathsByTag(criteria.get("tag").getAsString());
        }
        return List.of();
    }

    private static Collection map(ResultSet rs) throws SQLException
    {
        Collection c = new Collection();
        c.setCollectionId(rs.getInt("collection_id"));
        c.setName(rs.getString("name"));
        c.setIcon(rs.getString("icon"));
        c.setType(Collection.Type.valueOf(rs.getString("type")));
        c.setSmartCriteria(rs.getString("smart_criteria"));
        c.setBuiltin(rs.getBoolean("is_builtin"));
        return c;
    }
}