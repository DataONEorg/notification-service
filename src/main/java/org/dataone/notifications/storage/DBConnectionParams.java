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
        this(String.format("jdbc:postgresql://%s:%s/%s",
                NsConfig.getConfig().getString("ns.database.jdbcHost"),
                NsConfig.getConfig().getString("ns.database.jdbcPort"),
                NsConfig.getConfig().getString("ns.database.name")),
            NsConfig.getConfig().getString("ns.database.driverClassName"),
            NsConfig.getConfig().getString("ns.database.username"),
            NsConfig.getConfig().getString("ns.database.password"));
    }

    public DBConnectionParams(
        String jdbcUrl, String driverClassName, String username, String password) {

        this.jdbcUrl = jdbcUrl;
        this.driverClassName = driverClassName;
        this.username = username;
        this.password = password;
        if (password== null || password.isEmpty()) {
            String msg = """
            Database password must not be blank!
            In K8s?   Set the 'NS_DATABASE_PASSWORD' property in the k8s Secret whose
                      name is set in '.Values.global.passwordsSecret'
            Non-K8s?  Set the 'ns.database.password' property in properties.yaml, or as
                      the 'NS_DATABASE_PASSWORD' environment variable.
            """;
            throw new IllegalArgumentException(msg);
        }
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
