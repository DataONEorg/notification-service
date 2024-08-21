package org.dataone.notifications.api.data;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jvnet.hk2.annotations.Service;

import javax.sql.DataSource;

/**
 * Provides access to a data source, with connection pooling managed by HikariCP.
 * See HikariCP configuration options at:
 * https://github.com/brettwooldridge/HikariCP?tab=readme-ov-file#gear-configuration-knobs-baby
 */
@Singleton
@Service
@Default
public class NsDataSource extends HikariDataSource implements DataSource {

    @Inject
    public NsDataSource(DBConnectionParams params) {
        super();
        System.out.println("NsDataSource constructor called; JdbcUrl: " + params.getJdbcUrl());
        this.setJdbcUrl(params.getJdbcUrl());
        this.setDriverClassName(params.getDriverClassName());
        this.setUsername(params.getUsername());
        this.setPassword(params.getPassword());
    }
}
