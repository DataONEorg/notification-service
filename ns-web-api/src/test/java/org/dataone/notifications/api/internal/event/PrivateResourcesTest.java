package org.dataone.notifications.api.internal.event;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.event.LastSuccessfulRuntimes;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.dataone.notifications.storage.DataRepository;
import org.dataone.notifications.storage.NsDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

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
    private static final long EIGHT_DAYS_SEC = 8L * 24 * 60 * 60;
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

    private static long msFromNow(long seconds) {
        return System.currentTimeMillis() + seconds * 1000;
    }

    private static LastSuccessfulRuntimes runtimesOf(Long... timestamps) {
        return new LastSuccessfulRuntimes(Arrays.stream(timestamps)
            .map(ts -> new LastSuccessfulRuntimes.Entry(ts, NODE_ID)).toList());
    }

    @Test
    void validTimestamp_persists() {
        LastSuccessfulRuntimes body = runtimesOf(msFromNow(-60));
        privateResources.setLastRuntime(body, RESOURCE_TYPE);
        verify(mockDataRepo).setLastSuccessfulRuntimes(RESOURCE_TYPE, body);
    }

    @Test
    void timestamp_slightlyInFuture_withinClockSkew_accepted() {
        LastSuccessfulRuntimes body = runtimesOf(msFromNow(30));

        privateResources.setLastRuntime(body, RESOURCE_TYPE);

        verify(mockDataRepo).setLastSuccessfulRuntimes(RESOURCE_TYPE, body);
    }

    @Test
    void timestamp_tooFarInFuture_throwsBadRequest() {
        LastSuccessfulRuntimes body = runtimesOf(msFromNow(300));

        Exception thrown = assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastRuntime(body, RESOURCE_TYPE),
            "Expected setLastRuntime() to throw BadRequestException");
        assertTrue(
            thrown.getMessage().contains("timestamp out of range"),
            "Expected message to contain 'timestamp out of range', but was: "
                + thrown.getMessage());
        verify(mockDataRepo, never()).setLastSuccessfulRuntimes(any(), any());
    }

    @Test
    void timestamp_olderThanSevenDays_throwsBadRequest() {
        LastSuccessfulRuntimes body = runtimesOf(msFromNow(-EIGHT_DAYS_SEC));

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastRuntime(body, RESOURCE_TYPE),
            "Expected setLastRuntime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastSuccessfulRuntimes(any(), any());
    }

    @Test
    void repositoryFailure_propagates() {
        doThrow(new IllegalStateException("db down")).when(mockDataRepo)
            .setLastSuccessfulRuntimes(any(), any());

        assertThrows(
            IllegalStateException.class,
            () -> privateResources.setLastRuntime(runtimesOf(msFromNow(0)), RESOURCE_TYPE),
            "Expected repository exception to propagate");
    }

    @Test
    void multipleEntries_persistsAllEntriesInOrder() {
        LastSuccessfulRuntimes body = new LastSuccessfulRuntimes(List.of(
            new LastSuccessfulRuntimes.Entry(msFromNow(-60), NODE_ID),
            new LastSuccessfulRuntimes.Entry(msFromNow(-120), "urn:node:ARCTIC")));

        privateResources.setLastRuntime(body, RESOURCE_TYPE);
        verify(mockDataRepo).setLastSuccessfulRuntimes(RESOURCE_TYPE, body);
    }

    @Test
    void noEntries_forwardsEmptyListToRepository() {
        privateResources.setLastRuntime(runtimesOf(), RESOURCE_TYPE);
        verify(mockDataRepo).setLastSuccessfulRuntimes(RESOURCE_TYPE, runtimesOf());
    }

    @Test
    void nullBody_throwsBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastRuntime(null, RESOURCE_TYPE),
            "Expected setLastRuntime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastSuccessfulRuntimes(any(), any());
    }

    @Test
    void entryMissingNodeId_throwsBadRequest() {
        LastSuccessfulRuntimes body = new LastSuccessfulRuntimes(
            List.of(new LastSuccessfulRuntimes.Entry(msFromNow(0), null)));

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastRuntime(body, RESOURCE_TYPE),
            "Expected setLastRuntime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastSuccessfulRuntimes(any(), any());
    }

    @Test
    void entryMissingTimestamp_throwsBadRequest() {
        LastSuccessfulRuntimes body = new LastSuccessfulRuntimes(
            List.of(new LastSuccessfulRuntimes.Entry(null, NODE_ID)));

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastRuntime(body, RESOURCE_TYPE),
            "Expected setLastRuntime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastSuccessfulRuntimes(any(), any());
    }

    @Test
    void oneInvalidTimestampAmongMany_throwsBadRequest_persistsNothing() {
        LastSuccessfulRuntimes body =
            runtimesOf(msFromNow(0), msFromNow(-EIGHT_DAYS_SEC));

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastRuntime(body, RESOURCE_TYPE),
            "Expected setLastRuntime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastSuccessfulRuntimes(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void entryBlankNodeId_throwsBadRequest(String blankNodeId) {
        LastSuccessfulRuntimes body = new LastSuccessfulRuntimes(
            List.of(new LastSuccessfulRuntimes.Entry(msFromNow(0), blankNodeId)));

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastRuntime(body, RESOURCE_TYPE),
            "Expected setLastRuntime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastSuccessfulRuntimes(any(), any());
    }

    @Test
    void invalidNodeIdAmongMany_throwsBadRequest_persistsNothing() {
        LastSuccessfulRuntimes body = new LastSuccessfulRuntimes(List.of(
            new LastSuccessfulRuntimes.Entry(msFromNow(0), NODE_ID),
            new LastSuccessfulRuntimes.Entry(msFromNow(0), " ")));

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastRuntime(body, RESOURCE_TYPE),
            "Expected setLastRuntime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastSuccessfulRuntimes(any(), any());
    }

    @Test
    void getLastRuntime_returnsStoredTimestamp() {
        long expectedTs = msFromNow(-60);
        LastSuccessfulRuntimes expected = runtimesOf(expectedTs);
        when(mockDataRepo.getLastSuccessfulRuntimes(RESOURCE_TYPE)).thenReturn(expected);

        LastSuccessfulRuntimes result =
            (LastSuccessfulRuntimes) privateResources.getLastRuntime(RESOURCE_TYPE);

        assertEquals(expectedTs, result.runtimes().getFirst().timestamp());
        assertEquals(NODE_ID, result.runtimes().getFirst().nodeId());
        verify(mockDataRepo).getLastSuccessfulRuntimes(RESOURCE_TYPE);
    }

    @Test
    void getLastRuntime_multipleEntries_returnsAllEntries() {
        LastSuccessfulRuntimes expected = new LastSuccessfulRuntimes(List.of(
            new LastSuccessfulRuntimes.Entry(msFromNow(-60), NODE_ID),
            new LastSuccessfulRuntimes.Entry(msFromNow(-120), "urn:node:ARCTIC")));
        when(mockDataRepo.getLastSuccessfulRuntimes(RESOURCE_TYPE)).thenReturn(expected);

        assertEquals(expected, privateResources.getLastRuntime(RESOURCE_TYPE));
    }

    @Test
    void getLastRuntime_emptyEntries_throwsNotFound() {
        when(mockDataRepo.getLastSuccessfulRuntimes(RESOURCE_TYPE)).thenReturn(runtimesOf());

        assertThrows(
            NotFoundException.class, () -> privateResources.getLastRuntime(RESOURCE_TYPE),
            "Expected getLastRuntime() to throw NotFoundException");
    }

    @Test
    void getLastRuntime_nullResult_throwsIllegalArgument() {
        when(mockDataRepo.getLastSuccessfulRuntimes(RESOURCE_TYPE)).thenReturn(null);

        assertThrows(
            IllegalArgumentException.class, () -> privateResources.getLastRuntime(RESOURCE_TYPE),
            "Expected getLastRuntime() to throw IllegalArgumentException");
    }

    @Test
    void getLastRuntime_noStoredValue_throwsNotFound() {
        when(mockDataRepo.getLastSuccessfulRuntimes(RESOURCE_TYPE)).thenThrow(
            new NotFoundException("no last successful runtimes"));

        assertThrows(
            NotFoundException.class, () -> privateResources.getLastRuntime(RESOURCE_TYPE),
            "Expected getLastRuntime() to throw NotFoundException");
    }

    @Test
    void getLastRuntime_repositoryFailure_propagates() {
        when(mockDataRepo.getLastSuccessfulRuntimes(any())).thenThrow(
            new IllegalStateException("db down"));

        assertThrows(
            IllegalStateException.class, () -> privateResources.getLastRuntime(RESOURCE_TYPE),
            "Expected repository exception to propagate");
    }
}
