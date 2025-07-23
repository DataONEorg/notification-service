package org.dataone.notifications;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.extension.ExtendWith;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
class NsConfigTest {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Test
    void testGetConfig() throws Exception {

        final String yamlKey = "ns.database.testOnlyCamelCaseKey";
        final String expectedYamlValue = "yaml-value-for-testing-only";

        // Test that the configuration is loaded correctly
        assertNotNull(NsConfig.getConfig(), "Configuration should not be null");

        // Test that specific properties are present
        assertTrue(NsConfig.getConfig().containsKey(yamlKey),
                   "Database name should be defined in the configuration");
        assertEquals(expectedYamlValue, NsConfig.getConfig().getString(yamlKey));

        String expectedEnvValue = "test-env-override";

        // Set an environment variable to override the YAML value
        String envKey = "NS_DATABASE_TESTONLYCAMELCASEKEY";
        testEnvOverride(envKey, expectedEnvValue, yamlKey, expectedEnvValue);

        // This one should also work - env vars should be case-agnostic
        envKey = "ns_database_testOnlyCamelCaseKey";
        testEnvOverride(envKey, expectedEnvValue, yamlKey, expectedEnvValue);

        // This one should not work - env vars should have underscores, not periods
        envKey = "ns.database.testOnlyCamelCaseKey";
        testEnvOverride(envKey, expectedEnvValue, yamlKey, expectedYamlValue);

        // This one should not work - underscores must match yaml periods exactly
        envKey = "NS_DATABASE_TEST_ONLY_CAMELCASE_KEY";
        testEnvOverride(envKey, expectedEnvValue, yamlKey, expectedYamlValue);

        // Now unset the env variable and check that the original YAML value is restored
        expectedEnvValue = null;
        testEnvOverride(envKey, expectedEnvValue, yamlKey, expectedYamlValue);
    }

    private void testEnvOverride(
        String envKey, String envValue, String yamlKey, String expectedValue)
        throws Exception {
        EnvironmentVariables env = null;
        try {
            log.debug("Setting environment variable: {}={}", envKey, envValue);
            env = new EnvironmentVariables(envKey, envValue);
            env.setup();
            NsConfig.reload();
            log.debug("Expected result: {}={}", yamlKey, expectedValue);
            assertEquals(expectedValue, NsConfig.getConfig().getString(yamlKey));
        } finally {
            assertNotNull(env);
            env.teardown();
        }
    }
}
