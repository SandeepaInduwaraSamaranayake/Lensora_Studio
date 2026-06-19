package com.lensora.lensorastudio.services;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseManager
{
    private static final String DB_URL;
    // Initialize the logger instance for this specific class
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);

//    logger.debug("User typed: 'text'") — Use this for temporary developer checks. (Hidden in production with our INFO configuration).
//    logger.info("Application started successfully") — Use this for major milestones or successful background milestones.
//    logger.warn("Database connection took longer than expected") — Use this when something unexpected occurs but the application doesn't break.
//    logger.error("Failed to load FXML layout file", exception) — Use this inside catch blocks to track bugs and crashes.

    static
    {
        // Get the system-independent user home directory (e.g., C:\Users\Hp)
        String userHome = System.getProperty("user.home");

        // Define app's dedicated data folder name
        String folderName = ".lensorastudio"; // Using a dot makes it a hidden folder on Linux/macOS

        // Combine them to get the target directory path
        File databaseFolder = new File(userHome, folderName);

        // Create the folder automatically if it doesn't exist yet
        if (!databaseFolder.exists()) 
        {
            boolean created = databaseFolder.mkdirs();
            if (created)
            {
                logger.info("Created data directory at: " + databaseFolder.getAbsolutePath());
            }
        }

        // Formulate the final SQLite JDBC URL pointing db location
        File databaseFile = new File(databaseFolder, "lensora_studio.db");
        DB_URL = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

        logger.info("Database path configured to: " + DB_URL);
    }

    /**
     * Establishes a connection to the SQLite database.
     */
    public static Connection connect() throws SQLException
    {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Initializes the database by creating necessary tables.
     */
    public static void initializeDatabase()
    {
        try (Connection conn = connect();
            Statement stmt = conn.createStatement())
        {
            //Enable WAL mode BEFORE any transaction starts
            stmt.execute("PRAGMA journal_mode=WAL;");

            // Turn off auto-commit to run everything inside a single database transaction
            conn.setAutoCommit(false);

            InputStream is = DatabaseManager.class.getResourceAsStream("/com/lensora/lensorastudio/database/schema.sql");
            if (is == null) throw new RuntimeException("Error: Database Schema file is missing.");

            String schemaScript = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            String[] statements = schemaScript.split(";");

            for (String sql : statements)
            {
                String trimmedSql = sql.trim();
                if (!trimmedSql.isEmpty())
                {
                    // Add the statement to the batch pool instead of executing immediately
                    stmt.addBatch(trimmedSql);
                }
            }

            // Execute all batched statements inside the database at once
            stmt.executeBatch();

            // Commit the transaction to save changes permanently
            conn.commit();
            System.out.println("Whole schema script executed together successfully!");

        }
        catch (Exception e)
        {
            System.err.println("Failed to execute batch schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Example method to insert a data row using a PreparedStatement (prevents SQL injection).
     */
    public static void insertProject(String name, String client)
    {
        String insertSQL = "INSERT INTO projects(name, client) VALUES(?, ?)";

        try (Connection conn = connect();
            PreparedStatement pstmt = conn.prepareStatement(insertSQL))
        {

            pstmt.setString(1, name);
            pstmt.setString(2, client);
            pstmt.executeUpdate();
            System.out.println("Project inserted successfully!");

        }
        catch (SQLException e)
        {
            System.err.println("Insert failed: " + e.getMessage());
        }
    }
}
