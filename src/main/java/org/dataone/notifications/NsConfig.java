package org.dataone.notifications;

import org.apache.commons.configuration2.CompositeConfiguration;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.EnvironmentConfiguration;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

public class NsConfig {

    private static final String CONFIG_FILE = "properties.yaml";
    private static CompositeConfiguration config;

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
            // Add environment variables with highest precedence
            composite.addConfiguration(new EnvironmentConfiguration());

            // Then add YAML file as fallback
            YAMLConfiguration yamlConfig =
                new FileBasedConfigurationBuilder<>(YAMLConfiguration.class).configure(
                        new Parameters().fileBased().setFileName(CONFIG_FILE)).getConfiguration();
            composite.addConfiguration(yamlConfig);
        } catch (ConfigurationException e) {
            throw new RuntimeException("Can't load config properties from: " + CONFIG_FILE
                                           + "; Error: " + e.getMessage(), e);
        }
        log.debug("CONFIGURATION AT STARTUP: \n" + getAsString(composite));
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

    private static String getAsString(CompositeConfiguration config) {
        StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = config.getKeys("ns."); it.hasNext(); ) {
            String key = it.next();
            sb.append(key).append(": ").append(config.getString(key)).append("\n");
        }
        return sb.toString();
    }
}
