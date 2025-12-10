package org.dataone.notifications.storage;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * A class that encapsulates the database migration process. Implemented using FlywayDB.
 */
@Singleton
@Default
public class NsDBMigrator implements DBMigrator {

    private final Flyway flyway;

    @Inject
    public NsDBMigrator(DataSource source) {
        flyway = Flyway.configure().dataSource(source).cleanDisabled(false).load();
    }

    public void migrate() {
        flyway.migrate();
    }
}
