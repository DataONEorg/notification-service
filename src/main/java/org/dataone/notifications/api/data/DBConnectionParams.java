package org.dataone.notifications.api.data;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.dataone.notifications.NsConfig;

/**
 * A class that encapsulates the database connection parameters.
 * {@code @ApplicationScoped} means this is a singleton bean.
 */
@ApplicationScoped
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
