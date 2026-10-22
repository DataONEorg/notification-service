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
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/**
 * Entry point that starts the consumer and a file-based health probe.
 */
public class RabbitMqConsumerMain {
    private static final Logger log = LoggerFactory.getLogger(RabbitMqConsumerMain.class);

    private static SubscriptionEventConsumer consumer;

    // Health probe state (inlined from previous HealthServer)
    private static ScheduledExecutorService healthScheduler;
    private static ScheduledFuture<?> healthFuture;
    private static AtomicBoolean healthStatus = new AtomicBoolean(false);
    private static Path healthProbeFile;
    private static BooleanSupplier healthPoller;
    private static long healthPollIntervalSeconds = 5L;

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

        int pollInterval = 15;
        String probeFile = "/tmp/rmq-consumer-readiness";

        // start file-based health probe
        startHealthProbe(
            probeFile, () -> {
                try {
                    return isConnected();
                } catch (Throwable t) {
                    log.error("Health poller error: {}", t.getMessage());
                    return false;
                }
            }, pollInterval);

        try {
            consumer.start();
            log.info("Consumer started");
            refreshHealthNow();
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
    protected static synchronized void startHealthProbe(
        String probeFilePath, BooleanSupplier poller, long pollIntervalSeconds) {
        if (healthScheduler != null) {
            // already started
            return;
        }
        Objects.requireNonNull(probeFilePath, "probeFilePath");
        healthProbeFile = Paths.get(probeFilePath);
        healthPoller = Objects.requireNonNull(poller, "poller");
        healthPollIntervalSeconds = pollIntervalSeconds <= 0 ? 5 : pollIntervalSeconds;
        healthStatus = new AtomicBoolean(false);
        healthScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rmq-health-poller");
            t.setDaemon(true);
            return t;
        });

        // Ensure parent directory exists
        Path parent = healthProbeFile.getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (Exception e) {
                log.warn(
                    "Could not create probe file parent directories {}: {}", parent,
                    e.getMessage());
            }
        }

        refreshHealthNow();

        healthFuture = healthScheduler.scheduleAtFixedRate(
            () -> {
                try {
                    boolean newStatus = healthPoller.getAsBoolean();
                    healthStatus.set(newStatus);
                    if (newStatus) {
                        writeProbeFile();
                    }
                    log.debug("Health poller updated status={}", newStatus);
                } catch (Exception e) {
                    log.warn("Health poller threw exception: {}", e.getMessage());
                    healthStatus.set(false);
                }
            }, healthPollIntervalSeconds, healthPollIntervalSeconds, TimeUnit.SECONDS);

        log.info(
            "Health file probe started for {} with interval {} sec", healthProbeFile,
            healthPollIntervalSeconds);
    }

    /**
     * Stop the health probe.
     */
    protected static synchronized void stopHealthProbe() {
        if (healthFuture != null) {
            healthFuture.cancel(true);
            healthFuture = null;
        }
        if (healthScheduler != null) {
            try {
                healthScheduler.shutdownNow();
            } catch (Exception ignored) {
            }
            healthScheduler = null;
        }
        healthProbeFile = null;
        healthPoller = null;
        healthStatus = new AtomicBoolean(false);
    }

    /**
     * Force an immediate health refresh
     */
    protected static void refreshHealthNow() {
        log.info("Refreshing health status now");
        if (healthPoller == null) {
            log.info("Health poller not initialized, skipping refresh");
            return;
        }
        try {
            boolean newStatus = healthPoller.getAsBoolean();
            healthStatus.set(newStatus);
            if (newStatus) {
                log.info("Updating health file to {}", newStatus);
                writeProbeFile();
            }
            log.debug("Health refreshNow updated status={}", newStatus);
        } catch (Exception e) {
            log.error("Health refreshNow threw exception: {}", e.getMessage());
            healthStatus.set(false);
        }
    }

    private static void writeProbeFile() {
        if (healthProbeFile == null) {
            return;
        }
        try {
            String content = Long.toString(Instant.now().getEpochSecond());
            Path tmp =
                healthProbeFile.resolveSibling(healthProbeFile.getFileName().toString() + ".tmp");
            Files.writeString(tmp, content);
            Files.move(
                tmp, healthProbeFile, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("writeProbeFile() falling  back to non atomic write");
            try {
                Files.writeString(healthProbeFile, Long.toString(Instant.now().getEpochSecond()));
            } catch (IOException ex) {
                log.warn("Failed to write probe file {}: {}", healthProbeFile, ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("Failed to update probe file {}: {}", healthProbeFile, e.getMessage());
        }
    }
}
