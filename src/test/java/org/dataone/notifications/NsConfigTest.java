package org.dataone.notifications;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.extension.ExtendWith;

import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class NsConfigTest {

    @Test
    void testGetConfig() throws Exception {

        final String key = "database.name";
        final String expectedYaml = "notifications_test_db";

        // Test that the configuration is loaded correctly
        assertNotNull(NsConfig.getConfig(), "Configuration should not be null");

        // Test that specific properties are present
        assertTrue(NsConfig.getConfig().containsKey(key),
                   "Database name should be defined in the configuration");
        assertEquals(expectedYaml, NsConfig.getConfig().getString(key));

        final String expectedEnv = "test-env-override";

        EnvironmentVariables env = null;
        try {
            env = new EnvironmentVariables(key, expectedEnv);
            env.setup();
            NsConfig.reload();
            assertEquals(expectedEnv, NsConfig.getConfig().getString(key));
        } finally {
            env.teardown();
        }

        try {
            env = new EnvironmentVariables(key, null);
            env.setup();
            NsConfig.reload();
            assertEquals(expectedYaml, NsConfig.getConfig().getString(key));
        } finally {
            env.teardown();
        }
    }
}
