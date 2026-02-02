package org.dataone.notifications.messaging.consumer;

//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.dataone.notifications.messaging.SubscriptionEvent;
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
//    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "subscription-event-consumer");
        t.setDaemon(true);
        return t;
    });

    public SubscriptionEventConsumer(
        RabbitMqProperties properties,
        SubscriptionMessageProcessor processor) {
        this.properties = properties;
        this.processor = processor;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor.submit(this::consumeLoop);
    }

    private void consumeLoop() {
        log.info("Starting RabbitMQ consumer loop for queue {}", properties.queueName());
        while (running.get()) {
            try (Channel channel = newChannel()) {
                DeliverCallback callback = (consumerTag, delivery) -> handleDelivery(channel, delivery);
                channel.basicConsume(properties.queueName(), false, callback, consumerTag -> {});
                while (running.get() && channel.isOpen()) {
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Consumer thread interrupted");
                return;
            } catch (Exception e) {
                if (!running.get()) {
                    return;
                }
                log.error("Queue consumption failed, retrying in 5s", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handleDelivery(Channel channel, Delivery delivery) throws IOException {
        long tag = delivery.getEnvelope().getDeliveryTag();
        try {
            SubscriptionEvent event = deserialize(delivery);
            processor.process(event);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Failed to process message, requeueing", e);
            channel.basicNack(tag, false, true);    // (deliveryTag, multiple?, requeue?)
        }
    }

    private SubscriptionEvent deserialize(Delivery delivery) throws IOException {
        String body = new String(delivery.getBody(), StandardCharsets.UTF_8);
//        JsonNode node = mapper.readTree(body);
//        String resourceType = getRequiredText(node, "resourceType");
//        String pid = getRequiredText(node, "pid");
//        return SubscriptionEvent.from(resourceType, pid);
        throw new IOException();
    }

//    private String getRequiredText(JsonNode node, String field) {
//        JsonNode value = node.get(field);
//        if (value == null || value.asText().isBlank()) {
//            throw new IllegalArgumentException("Missing value for field: " + field);
//        }
//        return value.asText();
//    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        executor.shutdownNow();
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
        ch.basicQos(properties.prefetchCount());
        return ch;
    }

    private synchronized Channel getChannel() throws IOException, TimeoutException {
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

    /**
     * Quick check whether an active connection exists.
     */
    public synchronized boolean isConnected() {
        return connection != null && connection.isOpen();
    }

    /**
     * Attempt to open and immediately close a channel to verify connectivity.
     */
    public synchronized boolean checkConnection() {
        try {
            Channel ch = newChannel();
            try { ch.close(); } catch (Exception ignored) {}
            return true;
        } catch (Exception e) {
            log.warn("RabbitMQ connectivity check failed: {}", e.getMessage());
            return false;
        }
    }
}
