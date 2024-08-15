package org.dataone.notifications.api.data;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

/**
 * A class that encapsulates the database migration process. Implemented using FlywayDB.
 * {@code @ApplicationScoped} means this is a singleton bean.
 */
@ApplicationScoped
public class DBMigrator {

    private final Flyway flyway;

    @Inject
    public DBMigrator(DataSource source) {
        flyway = Flyway.configure().dataSource(source).cleanDisabled(false).load();
    }

    public void migrate() {
        flyway.migrate();
    }
}
