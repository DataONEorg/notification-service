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
            log.info("Consumer is already running, start() call ignored");
            return;
        }
        log.info("Starting SubscriptionEventConsumer for queue {}", properties.queueName());
        executor.submit(this::consumeLoop);
    }

    private void consumeLoop() {
        log.info("Starting RabbitMQ consumer loop for queue {}", properties.queueName());
        if(running != null) {
            log.info("running is not null");
            if(running.get()) {
                log.info("running is true");
            } else {
                log.info("running is false");
            }
        }else {
            log.info("running is null");
        }
        while (running.get()) {
            try (Channel channel = newChannel()) {
                log.info("Connected to RabbitMQ, consuming from queue {}", properties.queueName());
                DeliverCallback callback = (consumerTag, delivery) -> handleDelivery(channel, delivery);
                channel.basicConsume(properties.queueName(), false, callback, consumerTag -> {});
                if(channel.isOpen()){
                    log.info("Channel is open and consuming messages...");
                } else {
                    log.warn("Channel is not open after starting consumer");
                }
                while (running.get() && channel.isOpen()) {
                    log.info("Waiting for messages on queue {}...", properties.queueName());
                    Thread.sleep(1000);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.info("Consumer thread interrupted");
                return;
            } catch (Exception e) {
                if (!running.get()) {
                    log.info("Consumer stopped in generic catch, exiting consume loop");
                    return;
                }
                log.info("Queue consumption failed, retrying in 5s", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        log.info("running is false, exiting consume loop");
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
            log.info("Creating new RabbitMQ connection in newChannel");
            connection = createConnection();
        }
        log.info("About to create channel");
        Channel ch = connection.createChannel();
        ch.basicQos(properties.prefetchCount());
        return ch;
    }

    private synchronized Channel getChannel() throws IOException, TimeoutException {
        if (channel != null && channel.isOpen()) {
            return channel;
        }
        if (connection == null || !connection.isOpen()) {
            log.info("Creating new RabbitMQ connection in getChannel");
            connection = createConnection();
        }
        channel = connection.createChannel();
        channel.basicQos(properties.prefetchCount());
        return channel;
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
            log.info("Attempting to create RabbitMQ connection to {}:{} with virtual host '{}'", properties.host(), properties.port(), properties.virtualHost());
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
        log.info("Checking connection status: connection={}, connection.isOpen={}", connection, connection != null ? connection.isOpen() : "n/a");
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
