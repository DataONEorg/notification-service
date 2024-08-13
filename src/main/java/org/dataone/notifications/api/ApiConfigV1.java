package org.dataone.notifications.api;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.builder.BasicConfigurationBuilder;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.dataone.notifications.NsConfig;
import org.flywaydb.core.Flyway;

/**
 * Defines the components of the JAX-RS application and supplies additional metadata. Given the URL
 * structure: {@code http://<host-name>:<port>/<context-root>/<REST-uri>/<resource-name>}, the
 * {@code <REST-uri>} element is defined by the <code>@ApplicationPath</code> annotation, below;
 * e.g. <code>@ApplicationPath("/api/v1")</code>. In our case, we set it to "/", since we don't
 * want to include the version in the URL.
 */
@ApplicationPath("/")
public class ApiConfigV1 extends Application {

    public ApiConfigV1() {
        super();
        init();
    }

    private void init() {
        Flyway flyway = Flyway.configure().dataSource(
            NsConfig.getConfig().getString("database.jdbcUrl"),
            NsConfig.getConfig().getString("database.username"),
            NsConfig.getConfig().getString("database.password")).cleanDisabled(false).load();
        flyway.migrate();
    }
}
