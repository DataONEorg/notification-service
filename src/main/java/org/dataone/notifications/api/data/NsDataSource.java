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
        System.out.println("\n\n************************************************************\n");
        System.out.println("\n\nNsDataSource constructor called; Params: ");
        System.out.println("params.getUsername(): " + params.getUsername());
        System.out.println("params.getPassword(): " + params.getPassword());
        System.out.println("params.getJdbcUrl(): " + params.getJdbcUrl());
        System.out.println("params.getDriverClassName(): " + params.getDriverClassName());
        System.out.println("\n\n************************************************************\n\n");

        this.setJdbcUrl(params.getJdbcUrl());
        this.setDriverClassName(params.getDriverClassName());
        this.setUsername(params.getUsername());
        this.setPassword(params.getPassword());
    }
}
