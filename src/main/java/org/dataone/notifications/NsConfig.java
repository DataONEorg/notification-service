package org.dataone.notifications;

import org.apache.commons.configuration2.CompositeConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class NsConfig {

    private static final String CONFIG_FILE = "properties.yaml";
    private static Configuration config;

    private static final Logger log = LoggerFactory.getLogger("org.dataone.notifications.NsConfig");

    static {
        reload();
    }

    /**
     * Reloads the configuration properties from the YAML file and environment variables.
     * This method should be called to refresh the configuration if it has changed.
     */
    public static synchronized void reload() {
        CompositeConfiguration composite = new CompositeConfiguration();
        try {
            // Get YAML config so we can get a list of all the expected ns.camelCase.keys...
            YAMLConfiguration yamlConfig =
                new FileBasedConfigurationBuilder<>(YAMLConfiguration.class).configure(
                    new Parameters().fileBased().setFileName(CONFIG_FILE)).getConfiguration();

            // First add environment variables to config, as highest precedence
            composite.addConfiguration(new MapConfiguration(getEnvOverridesFor(yamlConfig)));

            // Then add YAML file as fallback
            composite.addConfiguration(yamlConfig);

        } catch (ConfigurationException e) {
            throw new RuntimeException("Can't load config properties from: " + CONFIG_FILE
                                           + "; Error: " + e.getMessage(), e);
        }
        log.debug("CONFIGURATION AT STARTUP: \n{}", getAsString(composite));
        config = composite;
    }

    /**
     * Get the configuration properties, prioritizing environment variables.
     *
     * @return the configuration properties
     */
    public static Configuration getConfig() {
        return config;
    }

    /**
     * Get  the values that have been overridden by environment variables, each associated with its
     * original YAML key, since the environment variable names are generally uppercase versions
     * of the YAML keys, with periods replaced by underscores (for example: you'd use the
     * environment variable NS_DATABASE_DRIVERCLASSNAME to override the yaml property with key
     * ns.database.driverClassName)
     *
     * @param yamlConfig the Configuration containing the YAML properties
     * @return a Map containing the values that have been overridden by environment variables,
     *                   each associated with its original YAML key.
     */
    private static Map<String, Object> getEnvOverridesFor(Configuration yamlConfig) {
        Map<String, String> yamlKeys = new HashMap<>();
        yamlConfig.getKeys().forEachRemaining(key -> {
            String keyLower = key.toLowerCase();
            yamlKeys.put(keyLower, key);
        });
        Map<String, Object> envOverrides = new HashMap<>();
        for (Map.Entry<String, String> entry : System.getenv().entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getKey().isEmpty()
                || entry.getValue().isEmpty()) {
                continue;
            }
            String envVar = entry.getKey();
            String keyLower = envVar.toLowerCase().replace('_', '.');
            if (!envVar.contains(".") && yamlKeys.containsKey(keyLower)) {
                log.debug(
                    "Overriding YAML config {} with environment variable: {}",
                    yamlKeys.get(keyLower), envVar);
                envOverrides.put(yamlKeys.get(keyLower), entry.getValue());
            }
        }
        return envOverrides;
    }

    private static String getAsString(CompositeConfiguration config) {
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = config.getKeys("ns."); it.hasNext(); ) {
            String key = it.next();
            sb.append(key).append(": ").append(config.getString(key)).append("\n");
        }
        return sb.toString();
    }
}
