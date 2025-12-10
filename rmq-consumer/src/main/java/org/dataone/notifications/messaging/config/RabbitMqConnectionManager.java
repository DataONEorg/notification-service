package org.dataone.notifications.messaging.config;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lazily creates a RabbitMQ connection and channel using the provided properties.
 */
public class RabbitMqConnectionManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RabbitMqConnectionManager.class);

    private final RabbitMqProperties properties;
    private Connection connection;
    private Channel channel;

    public RabbitMqConnectionManager(RabbitMqProperties properties) {
        this.properties = properties;
    }

    public synchronized Channel getChannel() throws IOException, TimeoutException {
        if (channel != null && channel.isOpen()) {
            return channel;
        }
        if (connection == null || !connection.isOpen()) {
            connection = createConnection();
        }
        channel = connection.createChannel();
        channel.basicQos(properties.prefetchCount());
        return channel;
    }

    public synchronized Channel newChannel() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = createConnection();
        }
        Channel ch = connection.createChannel();
        ch.basicQos(properties.prefetchCount());
        return ch;
    }

    private Connection createConnection() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(properties.host());
        factory.setPort(properties.port());
        factory.setUsername(properties.username());
        factory.setPassword(properties.password());
        factory.setVirtualHost(properties.virtualHost());
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);
        factory.setRequestedHeartbeat(30);
        log.info("Connecting to RabbitMQ {}:{} vhost={} queue={}",
            properties.host(), properties.port(), properties.virtualHost(), properties.queueName());
        return factory.newConnection();
    }

    public synchronized void shutdown() {
        try {
            if (channel != null) {
                channel.close();
            }
        } catch (Exception e) {
            log.warn("Error closing RabbitMQ channel", e);
        } finally {
            channel = null;
        }
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (Exception e) {
            log.warn("Error closing RabbitMQ connection", e);
        } finally {
            connection = null;
        }
    }

    @Override
    public void close() {
        shutdown();
    }

    public RabbitMqProperties getProperties() {
        return properties;
    }
}
