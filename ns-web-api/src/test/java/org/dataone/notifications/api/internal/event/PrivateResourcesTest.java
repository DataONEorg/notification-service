package org.dataone.notifications.api.internal.event;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.event.LastModificationTimes;
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

    private static long msFromNow(long seconds) {
        return System.currentTimeMillis() + seconds * 1000;
    }

    private static LastModificationTimes runtimesOf(Long... timestamps) {
        return new LastModificationTimes(Arrays.stream(timestamps)
            .map(ts -> new LastModificationTimes.Entry(ts, NODE_ID)).toList());
    }

    @Test
    void validTimestamp_persists() {
        LastModificationTimes body = runtimesOf(msFromNow(-60));
        privateResources.setLastModificationTime(body, RESOURCE_TYPE);
        verify(mockDataRepo).setLastModificationTimes(RESOURCE_TYPE, body);
    }

    @Test
    void timestamp_slightlyInFuture_withinClockSkew_accepted() {
        LastModificationTimes body = runtimesOf(msFromNow(30));

        privateResources.setLastModificationTime(body, RESOURCE_TYPE);

        verify(mockDataRepo).setLastModificationTimes(RESOURCE_TYPE, body);
    }

    @Test
    void repositoryFailure_propagates() {
        doThrow(new IllegalStateException("db down")).when(mockDataRepo)
            .setLastModificationTimes(any(), any());

        assertThrows(
            IllegalStateException.class,
            () -> privateResources.setLastModificationTime(runtimesOf(msFromNow(0)), RESOURCE_TYPE),
            "Expected repository exception to propagate");
    }

    @Test
    void multipleEntries_persistsAllEntriesInOrder() {
        LastModificationTimes body = new LastModificationTimes(List.of(
            new LastModificationTimes.Entry(msFromNow(-60), NODE_ID),
            new LastModificationTimes.Entry(msFromNow(-120), "urn:node:ARCTIC")));

        privateResources.setLastModificationTime(body, RESOURCE_TYPE);
        verify(mockDataRepo).setLastModificationTimes(RESOURCE_TYPE, body);
    }

    @Test
    void noEntries_forwardsEmptyListToRepository() {
        privateResources.setLastModificationTime(runtimesOf(), RESOURCE_TYPE);
        verify(mockDataRepo).setLastModificationTimes(RESOURCE_TYPE, runtimesOf());
    }

    @Test
    void nullBody_throwsBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastModificationTime(null, RESOURCE_TYPE),
            "Expected setLastModificationTime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastModificationTimes(any(), any());
    }

    @Test
    void entryMissingNodeId_throwsBadRequest() {
        LastModificationTimes body = new LastModificationTimes(
            List.of(new LastModificationTimes.Entry(msFromNow(0), null)));

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastModificationTime(body, RESOURCE_TYPE),
            "Expected setLastModificationTime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastModificationTimes(any(), any());
    }

    @Test
    void entryMissingTimestamp_throwsBadRequest() {
        LastModificationTimes body = new LastModificationTimes(
            List.of(new LastModificationTimes.Entry(null, NODE_ID)));

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastModificationTime(body, RESOURCE_TYPE),
            "Expected setLastModificationTime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastModificationTimes(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void entryBlankNodeId_throwsBadRequest(String blankNodeId) {
        LastModificationTimes body = new LastModificationTimes(
            List.of(new LastModificationTimes.Entry(msFromNow(0), blankNodeId)));

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastModificationTime(body, RESOURCE_TYPE),
            "Expected setLastModificationTime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastModificationTimes(any(), any());
    }

    @Test
    void invalidNodeIdAmongMany_throwsBadRequest_persistsNothing() {
        LastModificationTimes body = new LastModificationTimes(List.of(
            new LastModificationTimes.Entry(msFromNow(0), NODE_ID),
            new LastModificationTimes.Entry(msFromNow(0), " ")));

        assertThrows(
            BadRequestException.class,
            () -> privateResources.setLastModificationTime(body, RESOURCE_TYPE),
            "Expected setLastModificationTime() to throw BadRequestException");
        verify(mockDataRepo, never()).setLastModificationTimes(any(), any());
    }

    @Test
    void getLastRuntime_returnsStoredTimestamp() {
        long expectedTs = msFromNow(-60);
        LastModificationTimes expected = runtimesOf(expectedTs);
        when(mockDataRepo.getLastModificationTimes(RESOURCE_TYPE)).thenReturn(expected);

        LastModificationTimes result =
            (LastModificationTimes) privateResources.getLastModificationTime(RESOURCE_TYPE);

        assertEquals(expectedTs, result.runtimes().getFirst().timestamp());
        assertEquals(NODE_ID, result.runtimes().getFirst().nodeId());
        verify(mockDataRepo).getLastModificationTimes(RESOURCE_TYPE);
    }

    @Test
    void getLastRuntime_multipleEntries_returnsAllEntries() {
        LastModificationTimes expected = new LastModificationTimes(List.of(
            new LastModificationTimes.Entry(msFromNow(-60), NODE_ID),
            new LastModificationTimes.Entry(msFromNow(-120), "urn:node:ARCTIC")));
        when(mockDataRepo.getLastModificationTimes(RESOURCE_TYPE)).thenReturn(expected);

        assertEquals(expected, privateResources.getLastModificationTime(RESOURCE_TYPE));
    }

    @Test
    void getLastRuntime_emptyEntries_throwsNotFound() {
        when(mockDataRepo.getLastModificationTimes(RESOURCE_TYPE)).thenReturn(runtimesOf());

        assertThrows(
            NotFoundException.class, () -> privateResources.getLastModificationTime(RESOURCE_TYPE),
            "Expected getLastModificationTime() to throw NotFoundException");
    }

    @Test
    void getLastRuntime_nullResult_throwsIllegalArgument() {
        when(mockDataRepo.getLastModificationTimes(RESOURCE_TYPE)).thenReturn(null);

        assertThrows(
            IllegalArgumentException.class, () -> privateResources.getLastModificationTime(RESOURCE_TYPE),
            "Expected getLastModificationTime() to throw IllegalArgumentException");
    }

    @Test
    void getLastRuntime_noStoredValue_throwsNotFound() {
        when(mockDataRepo.getLastModificationTimes(RESOURCE_TYPE)).thenThrow(
            new NotFoundException("no last modification times"));

        assertThrows(
            NotFoundException.class, () -> privateResources.getLastModificationTime(RESOURCE_TYPE),
            "Expected getLastModificationTime() to throw NotFoundException");
    }

    @Test
    void getLastRuntime_repositoryFailure_propagates() {
        when(mockDataRepo.getLastModificationTimes(any())).thenThrow(
            new IllegalStateException("db down"));

        assertThrows(
            IllegalStateException.class, () -> privateResources.getLastModificationTime(RESOURCE_TYPE),
            "Expected repository exception to propagate");
    }
}
