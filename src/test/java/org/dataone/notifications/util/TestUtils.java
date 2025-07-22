package org.dataone.notifications.util;

import org.apache.commons.configuration2.YAMLConfiguration;
import org.dataone.notifications.NsConfig;
import org.dataone.notifications.storage.DBConnectionParams;
import org.dataone.notifications.storage.DataRepository;
import org.dataone.notifications.storage.NsDBMigrator;
import org.dataone.notifications.storage.NsDataRepository;
import org.dataone.notifications.storage.NsDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

public class TestUtils {

    public static PostgreSQLContainer<?> getTestDb() {

        YAMLConfiguration nsConfig = NsConfig.getConfig();
        PostgreSQLContainer<?> pg =
            new PostgreSQLContainer<>("postgres:" + nsConfig.getString("database.version"))
                .withDatabaseName(nsConfig.getString("database.name"))
                .withUsername(nsConfig.getString("database.username"))
                .withPassword(nsConfig.getString("database.password"))
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(1)))
                .waitingFor(
                    Wait.forLogMessage(".*database system is ready to accept connections.*", 1));
        pg.start();
        verifyPostgresPortAvailable(pg);
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

    private static void verifyPostgresPortAvailable(PostgreSQLContainer<?> pg) {
        // retries here
        int retries = 30;
        int retryCount = retries;
        int delayMs = 200;
        int mappedPort = pg.getMappedPort(5432);
        while (retryCount-- > 0) {
            try (Socket socket = new Socket()) {
                socket.connect(
                    new InetSocketAddress(pg.getHost(), mappedPort), delayMs);
                break; // success
            } catch (IOException e) {
                if (retryCount == 0) {
                    throw new RuntimeException("PostgreSQL port " + mappedPort
                                                   + " not available on container startup after "
                                                   + (retries * delayMs) + " ms.");
                }
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for PostgreSQL port", ie);
                }
            }
        }
    }

}
