package org.dataone.notifications.api.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A class that provides access to a test data source with connection pooling managed by HikariCP.
 */
public class NsTestDataSource implements DataSource {

    private static DataSource instance;
    private final javax.sql.DataSource hikariDataSource;

    private NsTestDataSource(PostgreSQLContainer<?> container) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(container.getDriverClassName());
        hikariConfig.setJdbcUrl(container.getJdbcUrl());
        hikariConfig.setUsername(container.getUsername());
        hikariConfig.setPassword(container.getPassword());
        hikariDataSource = new HikariDataSource(hikariConfig);
    }

    public synchronized static DataSource getInstance(PostgreSQLContainer<?> container) {
        if (instance == null) {
            instance = new NsTestDataSource(container);
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return hikariDataSource.getConnection();
    }
}
