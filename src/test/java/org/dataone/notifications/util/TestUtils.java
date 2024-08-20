package org.dataone.notifications.util;

import org.apache.commons.configuration2.YAMLConfiguration;
import org.dataone.notifications.NsConfig;
import org.dataone.notifications.api.data.DataRepository;
import org.dataone.notifications.api.data.DBConnectionParams;
import org.dataone.notifications.api.data.DBMigrator;
import org.dataone.notifications.api.data.NsDataRepository;
import org.dataone.notifications.api.data.NsDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;

public class TestUtils {

    public static PostgreSQLContainer<?> getTestDb() {

        YAMLConfiguration nsConfig = NsConfig.getConfig();
        PostgreSQLContainer<?> pg =
            new PostgreSQLContainer<>("postgres:" + nsConfig.getString("database.version"));

        pg.withExposedPorts(5432)
            .withDatabaseName(nsConfig.getString("database.name"))
            .withUsername(nsConfig.getString("database.username"))
            .withPassword(nsConfig.getString("database.password"));
        pg.start();

        return pg;
    }

    public static DataRepository getTestDataRepository(PostgreSQLContainer<?> pg) {

        DBConnectionParams dbConnectionParams =
            new DBConnectionParams(pg.getJdbcUrl(), pg.getDriverClassName(), pg.getUsername(),
                                   pg.getPassword());
        DataSource dataSource = new NsDataSource(dbConnectionParams);
        return new NsDataRepository(dataSource, new DBMigrator(dataSource));
    }
}
