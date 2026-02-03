package org.dataone.notifications;

import org.apache.commons.configuration2.Configuration;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

class NsConfigSmokeTest {

    @Test
    void defaultsAvailableOnClasspathAndLoadable() {
        URL props = Thread.currentThread().getContextClassLoader().getResource("properties.yaml");
        assertNotNull(props, "properties.yaml must be on classpath");

        URL log4j = Thread.currentThread().getContextClassLoader().getResource("log4j2.yaml");
        assertNotNull(log4j, "log4j2.yaml must be on classpath");

        Configuration cfg = NsConfig.getConfig();
        assertNotNull(cfg, "NsConfig must return a non-null Configuration");
        // check a known key from defaults
        assertEquals("notifications", cfg.getString("ns.database.name"));
    }
}
