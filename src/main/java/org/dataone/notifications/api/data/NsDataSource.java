package org.dataone.notifications.api.data;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.dataone.notifications.NsConfig;
import org.flywaydb.core.Flyway;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A class that initializes and provides access to a data source, with connection pooling managed
 * by HikariCP.
 */
@Default
@ApplicationScoped
public class NsDataSource implements DataSource {

    private static final HikariDataSource dataSource;

    static {
        HikariConfig hikariConfig = new HikariConfig();
        YAMLConfiguration nsConfig = NsConfig.getConfig();
        hikariConfig.setDriverClassName(nsConfig.getString("database.driverClassName"));
        hikariConfig.setJdbcUrl(nsConfig.getString("database.jdbcUrl"));
        hikariConfig.setUsername(nsConfig.getString("database.username"));
        hikariConfig.setPassword(nsConfig.getString("database.password"));
        dataSource = new HikariDataSource(hikariConfig);

        Flyway flyway = Flyway.configure().dataSource(
            NsConfig.getConfig().getString("database.jdbcUrl"),
            NsConfig.getConfig().getString("database.username"),
            NsConfig.getConfig().getString("database.password")).cleanDisabled(false).load();
        flyway.migrate();
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
