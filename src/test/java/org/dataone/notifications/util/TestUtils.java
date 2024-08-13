package org.dataone.notifications.util;

import org.apache.commons.configuration2.YAMLConfiguration;
import org.dataone.notifications.NsConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestUtils {


    public static PostgreSQLContainer<?> getTestDb() {

        YAMLConfiguration nsConfig = NsConfig.getConfig();

        // Set up postgres TestContainer
        PostgreSQLContainer<?> pg =
            new PostgreSQLContainer<>("postgres:" + nsConfig.getString("database.version"));

        pg.withExposedPorts(5432).withDatabaseName(nsConfig.getString("database.name"))
            .withUsername(nsConfig.getString("database.username"))
            .withPassword(nsConfig.getString("database.password"));
        pg.start();

        //initialize with test data using FlyWay
        MigrateResult result = Flyway.configure()
            .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
            .cleanDisabled(false)
            .load()
            .migrate();

        assertTrue(result.success, "Flyway migration failed");

        return pg;
    }
}
