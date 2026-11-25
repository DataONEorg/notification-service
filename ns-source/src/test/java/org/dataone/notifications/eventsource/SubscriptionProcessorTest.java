package org.dataone.notifications.eventsource;

import org.dataone.notifications.event.RabbitMqConnection;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionProcessorTest {

    @Test
    void close_closesConnectionAndAllowsReinitialization() throws Exception {
        try (MockedConstruction<RabbitMqConnection> connections =
                 mockConstruction(RabbitMqConnection.class)) {
            SubscriptionProcessor first = SubscriptionProcessor.getInstance();
            first.close();
            verify(connections.constructed().getFirst()).close();

            SubscriptionProcessor second = SubscriptionProcessor.getInstance();
            assertNotSame(first, second);
            second.close();
            verify(connections.constructed().get(1)).close();
        }
    }

    @Test
    void constructor_closesConnectionIfChannelCreationFails() throws Exception {
        IOException failure = new IOException("channel failed");
        try (MockedConstruction<RabbitMqConnection> connections =
                 mockConstruction(RabbitMqConnection.class,
                     (connection, context) -> when(connection.newChannel()).thenThrow(failure))) {
            assertSame(failure, assertThrows(IOException.class, SubscriptionProcessor::getInstance));
            verify(connections.constructed().getFirst()).close();
        }
    }
}
