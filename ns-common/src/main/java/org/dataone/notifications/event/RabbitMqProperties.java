package org.dataone.notifications.event;

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
    int prefetchCount,
    String exchangeName,
    String routingKey) {

    public RabbitMqProperties {
        Objects.requireNonNull(host, "host cannot be null");
        Objects.requireNonNull(username, "username cannot be null");
        Objects.requireNonNull(password, "password cannot be null");
        Objects.requireNonNull(virtualHost, "virtualHost cannot be null");
        Objects.requireNonNull(queueName, "queueName cannot be null");
    }

    public static RabbitMqProperties from(Configuration config) {
        Objects.requireNonNull(config, "config cannot be null");
        String queueName = requireNonBlank(config, "ns.messaging.rabbitmq.queueName");
        return new RabbitMqProperties(
            requireNonBlank(config, "ns.messaging.rabbitmq.host"),
            config.getInt("ns.messaging.rabbitmq.port", 5672),
            requireNonBlank(config, "ns.messaging.rabbitmq.username"),
            requireNonBlank(config, "ns.messaging.rabbitmq.password"),
            config.getString("ns.messaging.rabbitmq.virtualHost", "/"),
            queueName,
            Math.max(1, config.getInt("ns.messaging.rabbitmq.prefetchCount", 25)),
            // the exchangeName will be blank if there is no configuration. This means it uses the
            // default exchange
            config.getString("ns.messaging.rabbitmq.exchangeName", ""),
            // If there is no configuration, it will use queue name as the routing key
            config.getString("ns.messaging.rabbitmq.routingKey", queueName)
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
