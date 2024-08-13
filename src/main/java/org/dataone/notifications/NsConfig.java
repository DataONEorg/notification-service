package org.dataone.notifications;

import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.builder.BasicConfigurationBuilder;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;

public class NsConfig {

    private static final String CONFIG_FILE = "properties.yaml";
    private static final YAMLConfiguration config;

    static {
        BasicConfigurationBuilder<YAMLConfiguration> builder =
            new FileBasedConfigurationBuilder<>(YAMLConfiguration.class).configure(
                new Parameters().fileBased().setFileName(CONFIG_FILE));
        try {
            config = builder.getConfiguration();
        } catch (ConfigurationException e) {
            throw new RuntimeException(
                "Can't load config properties from: " + CONFIG_FILE + "; Error: " + e.getMessage(),
                e);
        }
    }

    /**
     * Get the configuration properties from the YAML file.
     *
     * @return the configuration properties
     */
    public static YAMLConfiguration getConfig() {
        return config;
    }
}
