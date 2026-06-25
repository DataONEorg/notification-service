package org.dataone.notifications;

import org.apache.commons.configuration2.CompositeConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class NsConfig {

    private static final String EXT_CFG_FILE_ENV_VAR = "NS_PROPERTIES_YAML_PATH";
    private static final String DEFAULT_CONFIG_FILE = "properties.yaml";
    private static final String EXTERNAL_CONFIG_FILE =
        "/etc/dataone/notification-service/properties.yaml";
    private static final Logger log = LoggerFactory.getLogger(NsConfig.class);
    private static Configuration config;

    static {
        reload();
    }

    /**
     * Reloads the configuration properties from the YAML file and environment variables.
     * This method should be called to refresh the configuration if it has changed.
     */
    public static synchronized void reload() {
        CompositeConfiguration composite = new CompositeConfiguration();
        YAMLConfiguration externalYamlConfig = null;
        String extConfigFilePath = System.getenv(EXT_CFG_FILE_ENV_VAR);
        if (extConfigFilePath == null || extConfigFilePath.trim().isEmpty()) {
            log.info(
                "No external config file path set in env variable {}. Using defaults from {}",
                EXT_CFG_FILE_ENV_VAR, DEFAULT_CONFIG_FILE);
        } else if (!new java.io.File(extConfigFilePath).exists()) {
            log.warn(
                "Could not load external config file from path {} found in env variable {}. Using"
                    + " defaults from {} instead",
                extConfigFilePath, EXT_CFG_FILE_ENV_VAR, DEFAULT_CONFIG_FILE);
        } else {
            log.info(
                "Loading external config file from path {} found in env variable {}",
                extConfigFilePath, EXT_CFG_FILE_ENV_VAR);
            try {
                externalYamlConfig = new FileBasedConfigurationBuilder<>(YAMLConfiguration.class)
                    .configure(new Parameters().fileBased().setFileName(extConfigFilePath))
                    .getConfiguration();
            } catch (ConfigurationException e) {
                log.info("Failed to load external config file from path {}: {}; Using defaults from {} instead",
                    extConfigFilePath, e.getMessage(), DEFAULT_CONFIG_FILE);
                throw new RuntimeException(
                    "Can't load config properties from external config file: "
                        + EXTERNAL_CONFIG_FILE + "; Error: " + e.getMessage(), e);
            }
        }

        YAMLConfiguration defaultYamlConfig;
        try {
            // First try to load default YAML from classpath (ns-common resources). If present,
            // use its URL; otherwise fall back to a file-based lookup of DEFAULT_CONFIG_FILE.
            java.net.URL resourceUrl = NsConfig.class.getClassLoader().getResource(DEFAULT_CONFIG_FILE);
            if (resourceUrl != null) {
                defaultYamlConfig = new FileBasedConfigurationBuilder<>(YAMLConfiguration.class)
                    .configure(new Parameters().fileBased().setURL(resourceUrl))
                    .getConfiguration();
            } else {
                defaultYamlConfig = new FileBasedConfigurationBuilder<>(YAMLConfiguration.class)
                    .configure(new Parameters().fileBased().setFileName(DEFAULT_CONFIG_FILE))
                    .getConfiguration();
            }
        } catch (ConfigurationException e) {
            throw new RuntimeException(
                "Can't load config properties from default config file: " + DEFAULT_CONFIG_FILE
                    + "; Error: " + e.getMessage(), e);
        }

        // First add any environment variables to config, as highest precedence
        composite.addConfiguration(new MapConfiguration(getEnvOverrides(defaultYamlConfig)));

        // Then add external YAML overrides, if they exist
        log.info("External config file path: {}", extConfigFilePath);
        if (externalYamlConfig != null) {
            log.info("External config file loaded successfully from " + 
            "path {}. Adding to configuration with precedence over defaults.",
                extConfigFilePath);
            log.info("EXTERNAL CONFIGURATION VALUES: \n{}", 
                getAsString(externalYamlConfig));
            composite.addConfiguration(externalYamlConfig);
        }else{
            log.info("No external config file loaded. Skipping addition to configuration.");
        }

        // Finally add default YAML config (lowest precedence)
        composite.addConfiguration(defaultYamlConfig);
        config = composite;
        log.debug("CONFIGURATION AT STARTUP: \n{}", getAsString(config));
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
     * Retrieve environment variable overrides, mapping them to their original YAML keys.
     * Environment variables use uppercase and replace dots with underscores.
     * Example: NS_DATABASE_DRIVERCLASSNAME overrides ns.database.driverClassName.
     *
     * @param yamlConfig the Configuration object containing the original YAML properties
     * @return a Map of overridden values, keyed by their corresponding YAML property names.
     */
    private static Map<String, Object> getEnvOverrides(Configuration yamlConfig) {
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

    private static String getAsString(Configuration config) {
        StringBuilder sb = new StringBuilder();
        String value;
        Set<String> redactedKeyEndings = Set.of("password", "passwd", "pwd", "secret", "token");
        for (Iterator<String> it = config.getKeys("ns."); it.hasNext(); ) {
            String key = it.next();
            String keyLower = key.toLowerCase();
            boolean isSensitive = redactedKeyEndings.stream().anyMatch(keyLower::endsWith);
            value = isSensitive? "(redacted)" : config.getString(key);
            sb.append(key).append(": ").append(value).append("\n");
        }
        return sb.toString();
    }
}
