package org.dataone.notifications.messaging.config;

import java.util.Objects;
import org.apache.commons.configuration2.Configuration;

/**
 * Immutable view of the queue configuration required by the notification consumer.
 */
public record RabbitMqProperties(
    String host,
    int port,
    String username,
    String password,
    String virtualHost,
    String queueName,
    int prefetchCount) {

    public RabbitMqProperties {
        Objects.requireNonNull(host, "host cannot be null");
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(password, "password cannot be null");
        Objects.requireNonNull(virtualHost, "virtualHost cannot be null");
        Objects.requireNonNull(queueName, "queueName cannot be null");
    }

    public static RabbitMqProperties from(Configuration config) {
        Objects.requireNonNull(config, "config cannot be null");
        return new RabbitMqProperties(
            requireNonBlank(config, "rmqConnection.host"),
            config.getInt("rmqConnection.port", 5672),
            requireNonBlank(config, "rmqConnection.username"),
            requireNonBlank(config, "rmqConnection.password"),
            config.getString("rmqConnection.virtualHost", "/"),
            requireNonBlank(config, "rmqConnection.queueName"),
            Math.max(1, config.getInt("rmqConnection.prefetchCount", 25))
        );
    }

    private static String requireNonBlank(Configuration config, String key) {
        String value = config.getString(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required configuration key: " + key);
        }
        return value;
    }
}
