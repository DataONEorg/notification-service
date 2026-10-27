package org.dataone.notifications.messaging.consumer;

import tools.jackson.databind.JsonNode;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dataone.notifications.messaging.config.RabbitMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal RabbitMQ queue consumer that runs until the container shuts down.
 */
public class SubscriptionEventConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventConsumer.class);

    private final RabbitMqProperties properties;
    // inlined connection management that was previously in RabbitMqConnectionManager
    private Connection connection;
    private Channel channel;
    private final SubscriptionMessageProcessor processor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SubscriptionEventConsumer(
        RabbitMqProperties properties,
        SubscriptionMessageProcessor processor) {
        this.properties = properties;
        this.processor = processor;
    }

    /**
     * Registers a single consumer on the queue. Reconnects and consumer re-registration after
     * network failures are handled by the RabbitMQ client's automatic recovery. If the broker is
     * unreachable at startup, this throws so the container can fail and be restarted.
     */
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        log.info("Starting SubscriptionEventConsumer for queue {}",
            properties.queueName());
        try {
            final Channel ch = newChannel();
            channel = ch;
            DeliverCallback callback = (consumerTag, delivery) -> handleDelivery(ch, delivery);
            ch.basicConsume(properties.queueName(), false, callback,
                consumerTag -> log.warn("Consumer {} cancelled by broker", consumerTag));
            log.info("Consuming messages from queue {}", properties.queueName());
        } catch (Exception e) {
            running.set(false);
            shutdown();
            throw new IllegalStateException("Failed to start consumer", e);
        }
    }

    private void handleDelivery(Channel channel, Delivery delivery) throws IOException {
        long tag = delivery.getEnvelope().getDeliveryTag();
        try {
            processor.process(delivery);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Failed to process message. Failed messages are not re-queued. ", e);
            // Disabling the requeue here to avoid bad messages holding up the queue forever:
            //     channel.basicNack(tag, false, true);    // (deliveryTag, multiple?, requeue?)
            // TODO: could eventually add a dead letter queue? Or some other type of operator
            //  alerting. For now, not priority, since this is not mission critical.
            channel.basicAck(tag, false);
        }
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        close();
    }

    @Override
    public void close() {
        shutdown();
    }

    /**
     * Create and return a new channel, lazily creating the connection if needed.
     */
    private synchronized Channel newChannel() throws IOException, TimeoutException {
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

    /**
     * Quick check whether an active connection exists.
     */
    public synchronized boolean isConnected() {
        log.debug("Checking connection status: " +
            " connection={}, connection.isOpen={}",
            connection, connection != null ? connection.isOpen() : "n/a");
        return connection != null && connection.isOpen();
    }
}
