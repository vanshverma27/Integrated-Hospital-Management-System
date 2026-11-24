import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * DBConnection - Database connection manager for Smart Hospital Management System.
 * 
 * Automatically loads database settings from 'db.properties' in the project directory,
 * or from environment variables (DB_URL, DB_USER, DB_PASSWORD).
 * 
 * If no password is provided, it tries 'root' (standard MySQL installer password)
 * followed by empty string '' (standard XAMPP password).
 */
public class DBConnection {

    private static final String DEFAULT_URL = 
        "jdbc:mysql://localhost:3306/model_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "root"; // Match user's MySQL password

    private static final Properties fileProps = new Properties();
    private static String lastErrorMessage = "";

    static {
        // 1. Load MySQL JDBC driver
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DBConnection] MySQL JDBC Driver not found in classpath.");
        }

        // 2. Load db.properties file if exists
        loadPropertiesFile();
    }

    private static void loadPropertiesFile() {
        File[] candidates = new File[] {
            new File("db.properties"),
            new File("../db.properties")
        };
        for (File f : candidates) {
            if (f.exists() && f.isFile()) {
                try (InputStream is = new FileInputStream(f)) {
                    fileProps.load(is);
                    System.out.println("[DBConnection] Loaded database settings from: " + f.getAbsolutePath());
                    return;
                } catch (Exception e) {
                    System.err.println("[DBConnection] Error reading " + f.getName() + ": " + e.getMessage());
                }
            }
        }
    }

    public static String getUrl() {
        String env = System.getenv("DB_URL");
        if (env != null && !env.trim().isEmpty()) return env;
        String prop = System.getProperty("db.url");
        if (prop != null && !prop.trim().isEmpty()) return prop;
        String fileVal = fileProps.getProperty("db.url");
        if (fileVal != null && !fileVal.trim().isEmpty()) return fileVal.trim();
        return DEFAULT_URL;
    }

    public static String getUser() {
        String env = System.getenv("DB_USER");
        if (env != null && !env.trim().isEmpty()) return env;
        String prop = System.getProperty("db.user");
        if (prop != null && !prop.trim().isEmpty()) return prop;
        String fileVal = fileProps.getProperty("db.user");
        if (fileVal != null && !fileVal.trim().isEmpty()) return fileVal.trim();
        return DEFAULT_USER;
    }

    public static String getPassword() {
        String env = System.getenv("DB_PASSWORD");
        if (env != null) return env;
        String prop = System.getProperty("db.password");
        if (prop != null) return prop;
        String fileVal = fileProps.getProperty("db.password");
        if (fileVal != null) return fileVal;
        return DEFAULT_PASSWORD;
    }

    public static String getLastErrorMessage() {
        return lastErrorMessage;
    }

    /**
     * Obtains an active connection to the MySQL database.
     */
    public static Connection getConnection() throws SQLException {
        String url = getUrl();
        String user = getUser();
        String password = getPassword();

        try {
            Connection conn = DriverManager.getConnection(url, user, password);
            lastErrorMessage = "";
            return conn;
        } catch (SQLException ex1) {
            // If default was "root" and failed, attempt empty password fallback (XAMPP compatibility)
            if ("root".equals(password)) {
                try {
                    Connection conn = DriverManager.getConnection(url, user, "");
                    lastErrorMessage = "";
                    return conn;
                } catch (SQLException ignored) {}
            }
            lastErrorMessage = ex1.getMessage();
            throw ex1;
        }
    }

    /**
     * Checks if the database is reachable.
     */
    public static boolean isAvailable() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Throwable t) {
            lastErrorMessage = t.getMessage();
            return false;
        }
    }
}
