package org.dataone.notifications.api.data;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.sql.DataSource;

/**
 * Provides access to a data source, with connection pooling managed by HikariCP.
 * See HikariCP configuration options at:
 * https://github.com/brettwooldridge/HikariCP?tab=readme-ov-file#gear-configuration-knobs-baby
 */
@Singleton
@Default
public class NsDataSource extends HikariDataSource implements DataSource {

    @Inject
    public NsDataSource(DBConnectionParams params) {
        super();
        this.setJdbcUrl(params.getJdbcUrl());
        this.setDriverClassName(params.getDriverClassName());
        this.setUsername(params.getUsername());
        this.setPassword(params.getPassword());
    }
}
