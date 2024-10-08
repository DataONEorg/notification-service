package org.dataone.notifications.storage;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.dataone.notifications.NsConfig;

/**
 * A class that encapsulates the database connection parameters.
 */
@Singleton
@Default
public class DBConnectionParams {

    private final String jdbcUrl;
    private final String driverClassName;
    private final String username;
    private final String password;

    @Inject
    public DBConnectionParams() {
        this(NsConfig.getConfig().getString("database.jdbcUrl"),
            NsConfig.getConfig().getString("database.driverClassName"),
            NsConfig.getConfig().getString("database.username"),
            NsConfig.getConfig().getString("database.password"));
    }

    public DBConnectionParams(
        String jdbcUrl, String driverClassName, String username, String password) {

        this.jdbcUrl = jdbcUrl;
        this.driverClassName = driverClassName;
        this.username = username;
        this.password = password;
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDriverClassName() {
        return driverClassName;
    }
}
