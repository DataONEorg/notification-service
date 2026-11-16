package org.dataone.notifications.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/**
 * Junit tests for RabbitMqConnectionTest
 */
public class RabbitMqConnectionTest {
    @TempDir
    Path tempDir;
    private final RabbitMqConnection rmqConnection =
        mock(RabbitMqConnection.class, CALLS_REAL_METHODS);

    @Test
    void probeFileWrittenWhenHealthyAndNotUpdatedWhenUnhealthy() throws Exception {
        Path probeFile = tempDir.resolve("readiness-probe");
        AtomicBoolean probeState = new AtomicBoolean(false);

        // initially unhealthy -> file should not exist
        rmqConnection.refreshProbeIfConnected(probeState::get, probeFile);
        assertFalse(Files.exists(probeFile));

        // set healthy and refresh -> file must be created
        probeState.set(true);
        rmqConnection.refreshProbeIfConnected(probeState::get, probeFile);
        assertTrue(Files.exists(probeFile));
        String contents = Files.readString(probeFile).trim();
        long ts = Long.parseLong(contents);
        long now = Instant.now().getEpochSecond();
        assertTrue(Math.abs(now - ts) < 10, "timestamp should be recent");

        // record mtime, then set unhealthy and refresh; mtime should not advance
        long mtimeBefore = Files.getLastModifiedTime(probeFile).toMillis();
        probeState.set(false);
        rmqConnection.refreshProbeIfConnected(probeState::get, probeFile);
        long mtimeAfter = Files.getLastModifiedTime(probeFile).toMillis();
        assertEquals(mtimeBefore, mtimeAfter, "probe file mtime should not change when unhealthy");
    }
}
