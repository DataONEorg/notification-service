package org.dataone.notifications.event;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import org.dataone.notifications.NsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

/**
 * A RabbitMQ connection class can be shared by both submitters and consumers
 */
public class RabbitMqConnection {
    private static final Logger log = LoggerFactory.getLogger(RabbitMqConnection.class);
    private static final Path healthProbeFile = Paths.get("/tmp/rmq-consumer-readiness");
    private static final long healthPollIntervalSeconds = 15L;
    private final RabbitMqProperties properties = RabbitMqProperties.from(NsConfig.getConfig());
    // inlined connection management that was previously in RabbitMqConnectionManager
    private Connection connection;

    /**
     * Constructor of the class
     * @throws IOException
     * @throws TimeoutException
     */
    public RabbitMqConnection() throws IOException, TimeoutException {
        connection = createConnection();
    }

    /**
     * Create and return a new channel, lazily creating the connection if needed.
     */
    public synchronized Channel newChannel() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            connection = createConnection();
        }
        Channel ch = connection.createChannel();
        ch.queueDeclare(properties.queueName(), true, false, false, null);
        ch.basicQos(properties.prefetchCount());
        return ch;
    }

    private Connection createConnection() throws IOException, TimeoutException {
        try{
            log.info("Creating connection factory");
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(properties.host());
            factory.setPort(properties.port());
            factory.setUsername(System.getenv("RABBITMQ_USERNAME") != null ? System.getenv("RABBITMQ_USERNAME") : properties.username());
            factory.setPassword(System.getenv("RABBITMQ_PASSWORD") != null ? System.getenv("RABBITMQ_PASSWORD") : properties.password());
            factory.setVirtualHost(properties.virtualHost());
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5000);
            factory.setRequestedHeartbeat(30);
            log.info("Attempting to create RabbitMQ connection to " +
                         " {}:{} with virtual host '{}'",
                     properties.host(), properties.port(), properties.virtualHost());
            return factory.newConnection();
        }catch(IOException e){
            log.info("IOException in createConnection: {}", e.getMessage());
            throw e;
        }catch(TimeoutException e){
            log.info("TimeoutException in createConnection: {}", e.getMessage());
            throw e;
        }catch(Exception e){
            log.info("Unexpected Exception in createConnection: {}", e.getMessage());
            throw new RuntimeException("Failed to create RabbitMQ connection", e);
        }
    }

    /**
     * Quick check whether an active connection exists.
     */
    public synchronized boolean isConnected() {
        boolean isOpen = connection != null && connection.isOpen();
        log.trace("connection={}, connection.isOpen={}", connection, isOpen);
        return isOpen;
    }

    /**
     * Close the connection
     * @throws IOException
     */
    public void close() throws IOException {
        if (connection != null) {
            connection.close();
        }
    }
}
