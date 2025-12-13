package org.dataone.notifications.messaging.consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMqConsumerMainTest {

    private AtomicBoolean probeState;
    private Path tempDir;
    private Path probeFile;

    @BeforeEach
    void setup() throws Exception {
        tempDir = Files.createTempDirectory("rmq-health-test");
        probeFile = tempDir.resolve("readiness-probe");
        probeState = new AtomicBoolean(false);
        // start health probe using the inlined API
        RabbitMqConsumerMain.startHealthProbe(probeFile.toString(), () -> probeState.get(), 1);
    }

    @AfterEach
    void teardown() {
        try {
            RabbitMqConsumerMain.stopHealthProbe();
        } catch (Exception ignored) {}
        try { Files.deleteIfExists(probeFile); } catch (Exception ignored) {}
        try { Files.deleteIfExists(tempDir); } catch (Exception ignored) {}
    }

    @Test
    void probeFileWrittenWhenHealthyAndNotUpdatedWhenUnhealthy() throws Exception {
        // initially unhealthy -> file should not exist
        assertFalse(Files.exists(probeFile));

        // set healthy and refresh -> file must be created
        probeState.set(true);
        RabbitMqConsumerMain.refreshHealthNow();
        assertTrue(Files.exists(probeFile));
        String contents = Files.readString(probeFile).trim();
        long ts = Long.parseLong(contents);
        long now = Instant.now().getEpochSecond();
        assertTrue(Math.abs(now - ts) < 10, "timestamp should be recent");

        // record mtime, then set unhealthy and refresh; mtime should not advance
        long mtimeBefore = Files.getLastModifiedTime(probeFile).toMillis();
        probeState.set(false);
        RabbitMqConsumerMain.refreshHealthNow();
        long mtimeAfter = Files.getLastModifiedTime(probeFile).toMillis();
        assertEquals(mtimeBefore, mtimeAfter, "probe file mtime should not change when unhealthy");
    }
}
