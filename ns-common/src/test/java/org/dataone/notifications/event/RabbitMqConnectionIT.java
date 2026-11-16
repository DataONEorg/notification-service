package org.dataone.notifications.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class RabbitMqConnectionIT {

    @Container
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:latest");

    @TempDir
    Path tempDir;

    @Test
    void connectsToRunningBrokerAndWritesProbe() throws Exception {
        RabbitMqProperties props = new RabbitMqProperties(
            rabbit.getHost(), rabbit.getAmqpPort(), rabbit.getAdminUsername(),
            rabbit.getAdminPassword(), "/", "test-queue", 10);

        // Fails immediately if RabbitMQ is unreachable or channel/queue setup fails
        RabbitMqConnection rmqConnection = new RabbitMqConnection(props);

        Path probeFile = tempDir.resolve("readiness-probe");
        rmqConnection.refreshProbeIfConnected(rmqConnection::isConnected, probeFile);

        assertTrue(Files.exists(probeFile), "probe file not written");
        long ts = Long.parseLong(Files.readString(probeFile).trim());
        long now = Instant.now().getEpochSecond();
        assertTrue(
            Math.abs(now - ts) < rmqConnection.healthPollIntervalSeconds + 1,
            "timestamp should be recent");
    }
}
