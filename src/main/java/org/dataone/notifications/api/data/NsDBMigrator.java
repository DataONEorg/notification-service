package org.dataone.notifications.api.data;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.flywaydb.core.Flyway;
import org.jvnet.hk2.annotations.Service;

import javax.sql.DataSource;

/**
 * A class that encapsulates the database migration process. Implemented using FlywayDB.
 */
@Singleton
@Service
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
