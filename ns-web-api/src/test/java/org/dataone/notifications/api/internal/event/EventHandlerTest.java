package org.dataone.notifications.api.internal.event;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.NotificationListener;
import org.dataone.notifications.api.internal.event.EventHandler.NotificationResult;
import org.dataone.notifications.api.internal.event.EventHandler.PingResponse;
import org.dataone.notifications.api.resource.SubscriptionResourceType;
import org.dataone.notifications.exception.NotificationDeliveryException;
import org.dataone.notifications.storage.DataRepository;
import org.dataone.notifications.storage.NsDataRepository;
import org.dataone.notifications.storage.Subscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventHandlerTest {

    private static final String PID = "urn:mypid:12345-67890";
    private static final SubscriptionResourceType RESOURCE_TYPE =
        SubscriptionResourceType.datasetChanges;
    private static final String SUBSCRIBER_1 = "https://orcid.org/0000-1234-5678-999X";
    private static final String SUBSCRIBER_2 = "https://orcid.org/0000-1111-2222-3333";

    private DataRepository mockDataRepo;
    private NotificationListener mockListener;
    private EventHandler eventHandler;

    @BeforeEach
    void setUp() {
        mockDataRepo = mock(NsDataRepository.class);
        mockListener = mock(NotificationListener.class);
        eventHandler = new EventHandler(mockDataRepo, mockListener);
    }

    private static Subscription subscriptionFor(String subject) {
        return new Subscription(subject, RESOURCE_TYPE, List.of(PID));
    }

    @Test
    void noArgConstructor_throws() {
        assertThrows(IllegalStateException.class, EventHandler::new);
    }

    @Test
    void validSend_allSucceed() throws Exception {
        when(mockDataRepo.getSubscriptionsByPid(PID, RESOURCE_TYPE)).thenReturn(
            List.of(subscriptionFor(SUBSCRIBER_1), subscriptionFor(SUBSCRIBER_2)));
        when(mockListener.handleNotification(SUBSCRIBER_1, RESOURCE_TYPE.toString(), PID))
            .thenReturn(SUBSCRIBER_1);
        when(mockListener.handleNotification(SUBSCRIBER_2, RESOURCE_TYPE.toString(), PID))
            .thenReturn(SUBSCRIBER_2);

        NotificationResult result =
            (NotificationResult) eventHandler.send(RESOURCE_TYPE.toString(), PID);

        assertEquals(List.of(SUBSCRIBER_1, SUBSCRIBER_2), result.successful());
        assertTrue(result.unsuccessful().isEmpty());
    }

    @Test
    void send_noSubscribers() throws Exception {
        when(mockDataRepo.getSubscriptionsByPid(PID, RESOURCE_TYPE)).thenReturn(List.of());

        NotificationResult result =
            (NotificationResult) eventHandler.send(RESOURCE_TYPE.toString(), PID);

        assertTrue(result.successful().isEmpty());
        assertTrue(result.unsuccessful().isEmpty());
        verify(mockListener, never()).handleNotification(any(), any(), any());
    }

    @Test
    void send_partialFailure() throws Exception {
        when(mockDataRepo.getSubscriptionsByPid(PID, RESOURCE_TYPE)).thenReturn(
            List.of(subscriptionFor(SUBSCRIBER_1), subscriptionFor(SUBSCRIBER_2)));
        when(mockListener.handleNotification(SUBSCRIBER_1, RESOURCE_TYPE.toString(), PID))
            .thenThrow(new NotificationDeliveryException("delivery failed", null));
        when(mockListener.handleNotification(SUBSCRIBER_2, RESOURCE_TYPE.toString(), PID))
            .thenReturn(SUBSCRIBER_2);

        NotificationResult result =
            (NotificationResult) eventHandler.send(RESOURCE_TYPE.toString(), PID);

        assertEquals(List.of(SUBSCRIBER_2), result.successful());
        assertEquals(Map.of(SUBSCRIBER_1, "delivery failed"), result.unsuccessful());
    }

    @Test
    void send_missingResourceType() {
        Exception thrown = assertThrows(
            NotFoundException.class, () -> eventHandler.send(null, PID),
            "Expected send() to throw NotFoundException");
        assertTrue(
            thrown.getMessage().contains("Missing resource type"),
            "Expected message to contain 'Missing resource type', but was: " + thrown.getMessage());
    }

    @Test
    void send_invalidResourceType() {
        assertThrows(
            BadRequestException.class, () -> eventHandler.send("notAResourceType", PID),
            "Expected send() to throw BadRequestException");
    }

    @Test
    void send_missingPid() {
        Exception thrown = assertThrows(
            NotFoundException.class, () -> eventHandler.send(RESOURCE_TYPE.toString(), null),
            "Expected send() to throw NotFoundException");
        assertTrue(
            thrown.getMessage().contains("pid"),
            "Expected message to contain 'pid', but was: " + thrown.getMessage());
    }

    @Test
    void ping() {
        PingResponse result = (PingResponse) eventHandler.ping();
        assertEquals("ok", result.status());
    }
}
