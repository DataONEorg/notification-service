package org.dataone.notifications.event;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

/**
 * A RabbitMQ connection class can be shared by both submitters and consumers
 */
public class RabbitMqConnection {
<<<<<<< Updated upstream
    private static final Logger log = LoggerFactory.getLogger(RabbitMqConnection.class);
    private static final Path healthProbeFile = Paths.get("/tmp/rmq-consumer-readiness");
    private static final long healthPollIntervalSeconds = 15L;
    private RabbitMqProperties properties = RabbitMqProperties.from(NsConfig.getConfig());
    private static ScheduledExecutorService healthScheduler;
||||||| Stash base
    private static final Logger log = LoggerFactory.getLogger(RabbitMqConnection.class);
    private static final Path healthProbeFile = Paths.get("/tmp/rmq-consumer-readiness");
    private static final long healthPollIntervalSeconds = 15L;
    private final RabbitMqProperties properties = RabbitMqProperties.from(NsConfig.getConfig());
    private static ScheduledExecutorService healthScheduler;
=======
    private final Logger log = LoggerFactory.getLogger(RabbitMqConnection.class);
    private final Path healthProbeFile = Paths.get("/tmp/rmq-consumer-readiness");
    private final long healthPollIntervalSeconds = 15L;
    private final RabbitMqProperties properties;
    private ScheduledExecutorService healthScheduler;
>>>>>>> Stashed changes
    // inlined connection management that was previously in RabbitMqConnectionManager
    private Connection connection;

    /**
     * Constructor of the class
     * @throws IOException
     * @throws TimeoutException
     */
    public RabbitMqConnection(RabbitMqProperties properties) throws IOException, TimeoutException {
        connection = createConnection();
        this.properties = properties;
<<<<<<< Updated upstream
||||||| Stash base
=======
        if (properties == null) {
            throw new IllegalArgumentException("RabbitMqProperties cannot be null");
        }
>>>>>>> Stashed changes
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
            factory.setUsername(
                System.getenv("RABBITMQ_USERNAME") != null ? System.getenv("RABBITMQ_USERNAME")
                                                           : properties.username());
            factory.setPassword(
                System.getenv("RABBITMQ_PASSWORD") != null ? System.getenv("RABBITMQ_PASSWORD")
                                                           : properties.password());
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

    /**
     * Start the health probe.
     */
    public synchronized void startHealthProbe() {
        if (healthScheduler != null) {
            return;
        }
        healthScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rmq-health-poller");
            t.setDaemon(true);
            return t;
        });
        healthScheduler.scheduleAtFixedRate(
            () -> refreshProbeIfConnected(this::isConnected, healthProbeFile), 0,
            healthPollIntervalSeconds, TimeUnit.SECONDS);

        log.info(
            "Health file probe started for {} with interval {} sec", healthProbeFile,
            healthPollIntervalSeconds);
    }

    /**
     * Stop the health probe.
     */
    public synchronized void stopHealthProbe() {
        if (healthScheduler != null) {
            healthScheduler.shutdownNow();
            healthScheduler = null;
        }
    }

    void refreshProbeIfConnected(BooleanSupplier connectionStatus, Path probeFile) {
        try {
            if (connectionStatus.getAsBoolean()) {
                writeProbeFile(probeFile);
            }
        } catch (RuntimeException e) {
            log.warn("Health probe failed: {}", e.getMessage());
        }
    }

    public void writeProbeFile(Path probeFile) {
        try {
            String content = Long.toString(Instant.now().getEpochSecond());
            Path tmp =
                probeFile.resolveSibling(probeFile.getFileName().toString() + ".tmp");
            Files.writeString(tmp, content);
            Files.move(
                tmp, probeFile, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("writeProbeFile() falling  back to non atomic write");
            try {
                Files.writeString(probeFile, Long.toString(Instant.now().getEpochSecond()));
            } catch (IOException ex) {
                log.warn("Failed to write probe file {}: {}", probeFile, ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to update probe file {}: {}", probeFile, e.getMessage());
        }
    }
}
