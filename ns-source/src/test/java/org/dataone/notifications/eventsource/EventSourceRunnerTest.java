package org.dataone.notifications.eventsource;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.dataone.notifications.event.RabbitMqConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

class EventSourceRunnerTest {
    private static final List<String> calls = new ArrayList<>();
    private static SubscriptionProcessor shared;

    @BeforeEach
    void reset() {
        calls.clear();
        shared = null;
    }

    public static class FirstSource implements EventSource {
        public FirstSource() {}

        @Override
        public void poll() throws Exception {
            calls.add("first");
            shared = SubscriptionProcessor.getInstance();
        }
    }

    public static class FailingSource implements EventSource {
        public FailingSource() {}

        @Override
        public void poll() throws IOException {
            calls.add("failure");
            throw new IOException("source failed");
        }
    }

    public static class LastSource implements EventSource {
        public LastSource() {}

        @Override
        public void poll() throws Exception {
            calls.add("last");
            assertSame(shared, SubscriptionProcessor.getInstance());
        }
    }

    public static class ConstructorFailure implements EventSource {
        public ConstructorFailure() {
            throw new IllegalStateException("constructor failed");
        }

        @Override
        public void poll() {}
    }

    @Test
    void successfulSourcesShareAndCloseOneConnection() throws Exception {
        Configuration config = new MapConfiguration(Map.of(
            "ns.source.eventsources.a.className", FirstSource.class.getName(),
            "ns.source.eventsources.b.className", LastSource.class.getName()));
        try (MockedConstruction<RabbitMqConnection> connections =
                 mockConstruction(RabbitMqConnection.class)) {
            EventSourceRunner.run(config);
            assertEquals(List.of("first", "last"), calls);
            assertEquals(1, connections.constructed().size());
            verify(connections.constructed().getFirst()).close();
        }
    }

    @Test
    void runsInOrderContinuesAfterFailuresAndClosesSharedConnection() throws Exception {
        Configuration config = new MapConfiguration(Map.of(
            "ns.source.eventsources.a.className", FirstSource.class.getName(),
            "ns.source.eventsources.b.className", FailingSource.class.getName(),
            "ns.source.eventsources.c.className", LastSource.class.getName(),
            "ns.source.eventsources.c.someSetting", "value"));
        try (MockedConstruction<RabbitMqConnection> connections =
                 mockConstruction(RabbitMqConnection.class)) {
            Exception failure = assertThrows(Exception.class, () -> EventSourceRunner.run(config));
            assertEquals(List.of("first", "failure", "last"), calls);
            assertEquals("source failed", failure.getSuppressed()[0].getMessage());
            assertEquals(1, connections.constructed().size());
            verify(connections.constructed().getFirst()).close();
        }
    }

    @Test
    void invalidSourcesDoNotPreventOtherSourcesFromRunning() throws Exception {
        Configuration config = new MapConfiguration(Map.of(
            "ns.source.eventsources.a.otherSetting", "value",
            "ns.source.eventsources.b.className", String.class.getName(),
            "ns.source.eventsources.c.className", "no.such.Source",
            "ns.source.eventsources.d.className", ConstructorFailure.class.getName(),
            "ns.source.eventsources.e.className", FirstSource.class.getName()));
        try (MockedConstruction<RabbitMqConnection> connections =
                 mockConstruction(RabbitMqConnection.class)) {
            Exception failure = assertThrows(Exception.class, () -> EventSourceRunner.run(config));
            assertEquals(4, failure.getSuppressed().length);
            assertEquals(List.of("first"), calls);
            verify(connections.constructed().getFirst()).close();
        }
    }

    @Test
    void discoversSourcesInYamlSubtrees() throws Exception {
        YAMLConfiguration config = new YAMLConfiguration();
        config.read(new StringReader("""
            ns:
              source:
                eventsources:
                  first:
                    className: %s
                  last:
                    className: %s
                    custom:
                      setting: value
            """.formatted(FirstSource.class.getName(), LastSource.class.getName())));
        try (MockedConstruction<RabbitMqConnection> connections =
                 mockConstruction(RabbitMqConnection.class)) {
            EventSourceRunner.run(config);
            assertEquals(List.of("first", "last"), calls);
            verify(connections.constructed().getFirst()).close();
        }
    }

    @Test
    void noSourcesFails() {
        Exception failure = assertThrows(IllegalArgumentException.class,
            () -> EventSourceRunner.run(new MapConfiguration(Map.of())));
        assertTrue(failure.getMessage().contains("At least one"));
    }

    @Test
    void closeFailureIsSuppressedWhenSourceFails() throws Exception {
        Configuration config = new MapConfiguration(Map.of(
            "ns.source.eventsources.a.className", FirstSource.class.getName(),
            "ns.source.eventsources.b.className", FailingSource.class.getName()));
        IOException closeFailure = new IOException("close failed");
        try (MockedConstruction<RabbitMqConnection> connections =
                 mockConstruction(RabbitMqConnection.class,
                     (connection, context) -> doThrow(closeFailure).when(connection).close())) {
            Exception failure = assertThrows(Exception.class, () -> EventSourceRunner.run(config));
            assertEquals("source failed", failure.getSuppressed()[0].getMessage());
            assertSame(closeFailure, failure.getSuppressed()[1]);
            verify(connections.constructed().getFirst()).close();
        }
    }
}
