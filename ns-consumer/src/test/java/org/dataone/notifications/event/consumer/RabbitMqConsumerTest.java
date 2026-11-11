package org.dataone.notifications.event.consumer;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Envelope;
import org.dataone.notifications.event.RabbitMqConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitMqConsumerTest {

    private static final String QUEUE_NAME = "notifications";
    private static final long DELIVERY_TAG = 42L;

    @Mock
    private RabbitMqConnection connection;

    @Mock
    private Channel channel;

    @Mock
    private NsEventProcessor processor;

    private RabbitMqConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new RabbitMqConsumer(QUEUE_NAME, processor, connection);
    }

    @Test
    void startRegistersConsumerAndAcknowledgesProcessedMessage() throws Exception {
        when(connection.newChannel()).thenReturn(channel);
        consumer.start();

        ArgumentCaptor<DeliverCallback> callback = ArgumentCaptor.forClass(DeliverCallback.class);
        verify(channel).basicConsume(eq(QUEUE_NAME), eq(false), callback.capture(),
            any(CancelCallback.class));

        Delivery delivery = delivery();
        callback.getValue().handle("consumer-tag", delivery);

        verify(processor).process(delivery);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void startAcknowledgesMessageWhenProcessingFails() throws Exception {
        when(connection.newChannel()).thenReturn(channel);
        doThrow(new IllegalStateException("processing failed"))
            .when(processor).process(any(Delivery.class));
        consumer.start();

        ArgumentCaptor<DeliverCallback> callback = ArgumentCaptor.forClass(DeliverCallback.class);
        verify(channel).basicConsume(eq(QUEUE_NAME), eq(false), callback.capture(),
            any(CancelCallback.class));

        Delivery delivery = delivery();
        callback.getValue().handle("consumer-tag", delivery);

        verify(processor).process(delivery);
        verify(channel).basicAck(DELIVERY_TAG, false);
    }

    @Test
    void startDoesNotRegisterAnotherConsumerWhenAlreadyRunning() throws Exception {
        when(connection.newChannel()).thenReturn(channel);

        consumer.start();
        consumer.start();

        verify(connection).newChannel();
        verify(channel).basicConsume(eq(QUEUE_NAME), eq(false), any(DeliverCallback.class),
            any(CancelCallback.class));
        verifyNoMoreInteractions(connection, channel);
    }

    @Test
    void stopClosesChannelAndConnection() throws Exception {
        when(connection.newChannel()).thenReturn(channel);
        consumer.start();

        consumer.stop();
        consumer.stop();

        verify(channel).close();
        verify(connection).close();
        verify(connection).newChannel();
    }

    @Test
    void startFailureClosesConnectionAndReportsFailure() throws Exception {
        IOException cause = new IOException("broker unavailable");
        when(connection.newChannel()).thenThrow(cause);

        IllegalStateException error =
            assertThrows(IllegalStateException.class, () -> consumer.start());

        assertEquals("Failed to start consumer", error.getMessage());
        assertEquals(cause, error.getCause());
        verify(connection).close();
    }

    @Test
    void startFailureWhenQueueNameNullOrBlank() {
        consumer = new RabbitMqConsumer(null, processor, connection);
        IllegalStateException error1 =
            assertThrows(IllegalStateException.class, () -> consumer.start());
        assertEquals("Failed to start consumer", error1.getMessage());
        assertInstanceOf(IllegalStateException.class, error1.getCause());
        assertEquals("RabbitMqConsumer queue name not provided", error1.getCause().getMessage());

        consumer = new RabbitMqConsumer(" ", processor, connection);
        IllegalStateException error2 =
            assertThrows(IllegalStateException.class, () -> consumer.start());
        assertEquals("Failed to start consumer", error2.getMessage());
        assertInstanceOf(IllegalStateException.class, error2.getCause());
        assertEquals("RabbitMqConsumer queue name not provided", error2.getCause().getMessage());
    }

    @Test
    void startFailureWhenProcessorNull() {
        consumer = new RabbitMqConsumer(QUEUE_NAME, null, connection);
        IllegalStateException error =
            assertThrows(IllegalStateException.class, () -> consumer.start());
        assertEquals("Failed to start consumer", error.getMessage());
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals("RabbitMqConsumer processor is null", error.getCause().getMessage());
    }

    @Test
    void startFailureWhenConnectionNull() {
        consumer = new RabbitMqConsumer(QUEUE_NAME, processor, null);
        IllegalStateException error =
            assertThrows(IllegalStateException.class, () -> consumer.start());
        assertEquals("Failed to start consumer", error.getMessage());
        assertInstanceOf(IllegalStateException.class, error.getCause());
        assertEquals("RabbitMqConnection is null", error.getCause().getMessage());
    }

    private static Delivery delivery() {
        return new Delivery(
            new Envelope(DELIVERY_TAG, false, "", QUEUE_NAME),
            new AMQP.BasicProperties(),
            new byte[0]);
    }
}
