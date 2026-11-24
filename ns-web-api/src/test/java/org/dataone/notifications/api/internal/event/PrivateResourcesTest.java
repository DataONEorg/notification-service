package org.dataone.notifications.api.internal.event;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.event.LastModificationTimes;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.dataone.notifications.storage.DataRepository;
import org.dataone.notifications.storage.NsDataRepository;
import org.dataone.notifications.storage.Subscription;
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

        assertEquals(expectedTs, result.entries().getFirst().timestamp());
        assertEquals(NODE_ID, result.entries().getFirst().nodeId());
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

    @Test
    void getSubscriptionSubjects_returnsSubjects() {
        String pid = "urn:uuid:1234";
        when(mockDataRepo.getSubscriptionsByPid(pid, RESOURCE_TYPE)).thenReturn(List.of(
            new Subscription("http://orcid.org/0000-0001-2345-6789", RESOURCE_TYPE, List.of(pid)),
            new Subscription("dn:uid=alice,o=org", RESOURCE_TYPE, List.of(pid))));

        List<String> subjects = privateResources.getSubscriptionSubjects(RESOURCE_TYPE, pid);

        assertEquals(List.of("http://orcid.org/0000-0001-2345-6789", "dn:uid=alice,o=org"),
                     subjects);
        verify(mockDataRepo).getSubscriptionsByPid(pid, RESOURCE_TYPE);
    }

    @Test
    void getSubscriptionSubjects_noSubscriptions_returnsEmptyList() {
        String pid = "urn:uuid:nope";
        when(mockDataRepo.getSubscriptionsByPid(pid, RESOURCE_TYPE)).thenReturn(List.of());

        assertEquals(List.of(), privateResources.getSubscriptionSubjects(RESOURCE_TYPE, pid));
        verify(mockDataRepo).getSubscriptionsByPid(pid, RESOURCE_TYPE);
    }

    @Test
    void getSubscriptionSubjects_repositoryFailure_propagates() {
        when(mockDataRepo.getSubscriptionsByPid(any(), any())).thenThrow(
            new IllegalStateException("db down"));

        assertThrows(
            IllegalStateException.class,
            () -> privateResources.getSubscriptionSubjects(RESOURCE_TYPE, "urn:uuid:1234"),
            "Expected repository exception to propagate");
    }

    @Test
    void updateSubscriptionPid_valid_returnsUpdatedCount() {
        String oldPid = "urn:uuid:old";
        String newPid = "urn:uuid:new";
        String subject = "http://orcid.org/0000-0001-2345-6789";
        when(mockDataRepo.updateSubscriptionPid(oldPid, newPid, subject)).thenReturn(3);

        PrivateResources.UpdateResult result =
            (PrivateResources.UpdateResult) privateResources.updateSubscriptionPid(
                oldPid, newPid, subject);

        assertEquals(3, result.updated());
        verify(mockDataRepo).updateSubscriptionPid(oldPid, newPid, subject);
    }

    @Test
    void updateSubscriptionPid_nullOldPid_throwsBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> privateResources.updateSubscriptionPid(
                null, "urn:uuid:new", "http://orcid.org/0000-0001-2345-6789"),
            "Expected updateSubscriptionPid() to throw BadRequestException");
        verify(mockDataRepo, never()).updateSubscriptionPid(any(), any(), any());
    }

    @Test
    void updateSubscriptionPid_blankOldPid_throwsBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> privateResources.updateSubscriptionPid(
                "  ", "urn:uuid:new", "http://orcid.org/0000-0001-2345-6789"),
            "Expected updateSubscriptionPid() to throw BadRequestException");
        verify(mockDataRepo, never()).updateSubscriptionPid(any(), any(), any());
    }

    @Test
    void updateSubscriptionPid_nullNewPid_throwsBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> privateResources.updateSubscriptionPid(
                "urn:uuid:old", null, "http://orcid.org/0000-0001-2345-6789"),
            "Expected updateSubscriptionPid() to throw BadRequestException");
        verify(mockDataRepo, never()).updateSubscriptionPid(any(), any(), any());
    }

    @Test
    void updateSubscriptionPid_blankNewPid_throwsBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> privateResources.updateSubscriptionPid(
                "urn:uuid:old", "  ", "http://orcid.org/0000-0001-2345-6789"),
            "Expected updateSubscriptionPid() to throw BadRequestException");
        verify(mockDataRepo, never()).updateSubscriptionPid(any(), any(), any());
    }

    @Test
    void updateSubscriptionPid_nullSubject_throwsBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> privateResources.updateSubscriptionPid(
                "urn:uuid:old", "urn:uuid:new", null),
            "Expected updateSubscriptionPid() to throw BadRequestException");
        verify(mockDataRepo, never()).updateSubscriptionPid(any(), any(), any());
    }

    @Test
    void updateSubscriptionPid_blankSubject_throwsBadRequest() {
        assertThrows(
            BadRequestException.class,
            () -> privateResources.updateSubscriptionPid(
                "urn:uuid:old", "urn:uuid:new", "  "),
            "Expected updateSubscriptionPid() to throw BadRequestException");
        verify(mockDataRepo, never()).updateSubscriptionPid(any(), any(), any());
    }

    @Test
    void updateSubscriptionPid_repositoryFailure_propagates() {
        when(mockDataRepo.updateSubscriptionPid(any(), any(), any())).thenThrow(
            new IllegalStateException("db down"));

        assertThrows(
            IllegalStateException.class,
            () -> privateResources.updateSubscriptionPid(
                "urn:uuid:old", "urn:uuid:new", "http://orcid.org/0000-0001-2345-6789"),
            "Expected repository exception to propagate");
    }
}
