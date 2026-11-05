package org.dataone.notifications.api.internal.event;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.api.internal.event.PrivateResources.NotificationResult;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.dataone.notifications.storage.DataRepository;
import org.dataone.notifications.storage.NsDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrivateResourcesTest {

    private static final String NODE_ID = "urn:node:KNB";
    private static final SubscriptionResourceType RESOURCE_TYPE =
        SubscriptionResourceType.datasetChanges;

    private DataRepository mockDataRepo;
    private PrivateResources privateResources;

    @BeforeEach
    void setUp() {
        mockDataRepo = mock(NsDataRepository.class);
        privateResources = new PrivateResources(mockDataRepo);
    }

    @Test
    void noArgConstructor_throws() {
        assertThrows(IllegalStateException.class, PrivateResources::new);
    }

    @Test
    void validTimestamp_persistsAndReturnsResult() {
        Instant timestamp = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS);

        NotificationResult result = (NotificationResult) privateResources.setLastRuntime(
            timestamp.toEpochMilli(), NODE_ID, RESOURCE_TYPE);

        assertEquals(timestamp, result.timestampEpochMillis());
        assertEquals(RESOURCE_TYPE, result.resourceType());
        assertEquals(NODE_ID, result.nodeId());
        verify(mockDataRepo).setLastSuccessfulRuntime(timestamp, NODE_ID, RESOURCE_TYPE);
    }

    @Test
    void timestamp_slightlyInFuture_withinClockSkew_accepted() {
        Instant timestamp = Instant.now().plusSeconds(30);

        privateResources.setLastRuntime(timestamp.toEpochMilli(), NODE_ID, RESOURCE_TYPE);

        verify(mockDataRepo).setLastSuccessfulRuntime(
            Instant.ofEpochMilli(timestamp.toEpochMilli()), NODE_ID, RESOURCE_TYPE);
    }

    @Test
    void timestamp_tooFarInFuture_throwsBadRequest() {
        long tooFarAhead = Instant.now().plus(Duration.ofMinutes(5)).toEpochMilli();

        Exception thrown = assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastRuntime(tooFarAhead, NODE_ID, RESOURCE_TYPE),
            "Expected setLastRuntime() to throw BadRequestException");
        assertTrue(
            thrown.getMessage().contains("timestamp out of range"),
            "Expected message to contain 'timestamp out of range', but was: "
                + thrown.getMessage());
        verify(mockDataRepo, never()).setLastSuccessfulRuntime(any(), any(), any());
    }

    @Test
    void timestamp_olderThanSevenDays_throwsBadRequest() {
        long tooOld = Instant.now().minus(Duration.ofDays(8)).toEpochMilli();

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastRuntime(tooOld, NODE_ID, RESOURCE_TYPE),
            "Expected setLastRuntime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastSuccessfulRuntime(any(), any(), any());
    }

    @Test
    void timestamp_inSecondsInsteadOfMillis_throwsBadRequest() {
        long epochSeconds = Instant.now().getEpochSecond();

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastRuntime(epochSeconds, NODE_ID, RESOURCE_TYPE),
            "Expected setLastRuntime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastSuccessfulRuntime(any(), any(), any());
    }

    @Test
    void repositoryFailure_propagates() {
        long now = Instant.now().toEpochMilli();
        doThrow(new IllegalStateException("db down"))
            .when(mockDataRepo).setLastSuccessfulRuntime(any(), any(), any());

        assertThrows(
            IllegalStateException.class,
            () -> privateResources.setLastRuntime(now, NODE_ID, RESOURCE_TYPE),
            "Expected repository exception to propagate");
    }

    @Test
    void getLastRuntime_returnsStoredTimestamp() {
        Instant timestamp = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MILLIS);
        when(mockDataRepo.getLastSuccessfulRuntime(NODE_ID, RESOURCE_TYPE)).thenReturn(timestamp);

        NotificationResult result =
            (NotificationResult) privateResources.getLastRuntime(NODE_ID, RESOURCE_TYPE);

        assertEquals(timestamp, result.timestampEpochMillis());
        assertEquals(RESOURCE_TYPE, result.resourceType());
        assertEquals(NODE_ID, result.nodeId());
        verify(mockDataRepo).getLastSuccessfulRuntime(NODE_ID, RESOURCE_TYPE);
    }

    @Test
    void getLastRuntime_noStoredValue_throwsNotFound() {
        when(mockDataRepo.getLastSuccessfulRuntime(NODE_ID, RESOURCE_TYPE)).thenReturn(null);

        assertThrows(
            NotFoundException.class,
            () -> privateResources.getLastRuntime(NODE_ID, RESOURCE_TYPE),
            "Expected getLastRuntime() to throw NotFoundException");
    }

    @Test
    void getLastRuntime_repositoryFailure_propagates() {
        when(mockDataRepo.getLastSuccessfulRuntime(any(), any()))
            .thenThrow(new IllegalStateException("db down"));

        assertThrows(
            IllegalStateException.class,
            () -> privateResources.getLastRuntime(NODE_ID, RESOURCE_TYPE),
            "Expected repository exception to propagate");
    }
}
