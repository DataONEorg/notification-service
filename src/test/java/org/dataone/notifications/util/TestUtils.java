package org.dataone.notifications.util;

import org.apache.commons.configuration2.YAMLConfiguration;
import org.dataone.notifications.NsConfig;
import org.dataone.notifications.storage.DBConnectionParams;
import org.dataone.notifications.storage.DataRepository;
import org.dataone.notifications.storage.NsDBMigrator;
import org.dataone.notifications.storage.NsDataRepository;
import org.dataone.notifications.storage.NsDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

public class TestUtils {

    public static PostgreSQLContainer<?> getTestDb() {

        YAMLConfiguration nsConfig = NsConfig.getConfig();
        PostgreSQLContainer<?> pg =
            new PostgreSQLContainer<>("postgres:" + nsConfig.getString("database.version"));

        pg.withExposedPorts(5432).withDatabaseName(nsConfig.getString("database.name"))
            .withUsername(nsConfig.getString("database.username"))
            .withPassword(nsConfig.getString("database.password"));
        pg.start();

        return pg;
    }

    public static synchronized DataRepository getTestDataRepository(PostgreSQLContainer<?> pg) {

        final DBConnectionParams dbConnectionParams =
            new DBConnectionParams(pg.getJdbcUrl(), pg.getDriverClassName(), pg.getUsername(),
                                   pg.getPassword());
        final NsDataSource dataSource = new NsDataSource(dbConnectionParams);
        final NsDBMigrator migrator = new NsDBMigrator(dataSource);
        return new NsDataRepository(dataSource, migrator);
    }
}
