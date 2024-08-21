package org.dataone.notifications.api.data;

import org.glassfish.jersey.spi.Contract;

@Contract
public interface DBMigrator {
    void migrate();
}
