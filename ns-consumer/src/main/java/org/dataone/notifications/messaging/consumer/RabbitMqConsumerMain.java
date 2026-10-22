package org.dataone.notifications.messaging.consumer;

import org.dataone.notifications.NsConfig;
import org.dataone.notifications.messaging.config.RabbitMqProperties;
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
import java.util.function.BooleanSupplier;

/**
 * Entry point that starts the consumer and a file-based health probe.
 */
public class RabbitMqConsumerMain {
    private static final Logger log = LoggerFactory.getLogger(RabbitMqConsumerMain.class);

    private static SubscriptionEventConsumer consumer;

    private static final Path healthProbeFile = Paths.get("/tmp/rmq-consumer-readiness");
    private static final long healthPollIntervalSeconds = 15L;
    private static ScheduledExecutorService healthScheduler;

    static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received, stopping consumer");
            stopHealthProbe();
            log.info("Stopped Health Probe");
            if (consumer != null) {
                consumer.stop();
            }
        }));

        // read configuration and create runtime objects
        final RabbitMqProperties props = RabbitMqProperties.from(NsConfig.getConfig());
        final SubscriptionMessageProcessor processor = new SubscriptionMessageProcessor();
        consumer = new SubscriptionEventConsumer(props, processor);

        try {
            consumer.start();
            log.info("Consumer started");
            startHealthProbe();
        } catch (Exception e) {
            log.error("Failed to start RabbitMQ consumer. Stopping health probe.", e);
            stopHealthProbe();
            if (consumer != null) {
                consumer.stop();
            }
            System.exit(1);
        }
        log.info(
            "RabbitMQ consumer main thread exiting, consumer and health probe should keep running");
    }

    private static boolean isConnected() {
        return consumer != null && consumer.isConnected();
    }

    /**
     * Start the health probe.
     */
    private static synchronized void startHealthProbe() {
        if (healthScheduler != null) {
            return;
        }
        healthScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rmq-health-poller");
            t.setDaemon(true);
            return t;
        });
        healthScheduler.scheduleAtFixedRate(
            () -> refreshProbeIfConnected(RabbitMqConsumerMain::isConnected, healthProbeFile),
            0, healthPollIntervalSeconds, TimeUnit.SECONDS);

        log.info(
            "Health file probe started for {} with interval {} sec", healthProbeFile,
            healthPollIntervalSeconds);
    }

    /**
     * Stop the health probe.
     */
    protected static synchronized void stopHealthProbe() {
        if (healthScheduler != null) {
            healthScheduler.shutdownNow();
            healthScheduler = null;
        }
    }

    static void refreshProbeIfConnected(BooleanSupplier connectionStatus, Path probeFile) {
        try {
            if (connectionStatus.getAsBoolean()) {
                writeProbeFile(probeFile);
            }
        } catch (RuntimeException e) {
            log.warn("Health probe failed: {}", e.getMessage());
        }
    }

    private static void writeProbeFile(Path probeFile) {
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
