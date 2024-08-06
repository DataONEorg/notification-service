package org.dataone.notifications.util;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

public class DatabaseUtils {
    private static final String CONFIG_FILE = "/properties.yaml";
    private static String URL;
    private static String USER;
    private static String PASSWORD;

    static {
        try (InputStream inputStream = DatabaseUtils.class.getResourceAsStream(CONFIG_FILE)) {
            YAMLMapper yaml = new YAMLMapper();
            Map<String, Object> config = yaml.readValue(inputStream, Map.class);
            Map<String, String> databaseConfig = (Map<String, String>) config.get("database");
            URL = databaseConfig.get("url");
            USER = databaseConfig.get("user");
            PASSWORD = databaseConfig.get("password");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load database configuration", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
