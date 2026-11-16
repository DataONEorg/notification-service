package org.dataone.notifications.storage;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.event.LastSuccessfulRuntimes;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.dataone.notifications.util.DbTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRepositoryIT {

    public static final String EXPECTED_SUBJECT = "https://orcid.org/0000-1234-5678-999X";
    public static final String EXPECTED_PID = "pid1";
    private static final Subscription EXPECTED_RECORD =
        new Subscription(
            EXPECTED_SUBJECT, SubscriptionResourceType.datasetChanges,
                         List.of(EXPECTED_PID));
    private static PostgreSQLContainer<?> pg;
    private static DataRepository dataRepo;

    @BeforeAll
    static void oneTimeSetUp() throws Exception {
        pg = DbTestUtils.getTestDb();
        // one pool for the whole class: a pool per test exhausts Postgres max_connections
        dataRepo = DbTestUtils.getTestDataRepository(pg);
    }

    @AfterAll
    static void oneTimeTearDown() {
        assertNotNull(pg, "Postgres Instance is null - cannot shut down cleanly!");
        pg.stop();
    }

    @Test
    void addSubscriptionValidData() {
        assertEquals(
            EXPECTED_RECORD, dataRepo.addSubscription(
                EXPECTED_SUBJECT,
                SubscriptionResourceType.datasetChanges,
                EXPECTED_PID));
    }

    @Test
    void addSubscriptionInvalidSubject() {
        String blankSubject = "";
        assertThrows(
            NotAuthorizedException.class, () -> dataRepo.addSubscription(
                blankSubject,
                SubscriptionResourceType.datasetChanges,
                EXPECTED_PID));
    }

    @Test
    void addSubscriptionInvalidData() {
        String blankPid = "";
        assertThrows(
            NotFoundException.class, () -> dataRepo.addSubscription(
                EXPECTED_SUBJECT,
                SubscriptionResourceType.datasetChanges,
                blankPid));
    }

    @Test
    void getSubscriptionsValidSubject() {
        List<String> pids =
            dataRepo.getSubscriptions(EXPECTED_SUBJECT, SubscriptionResourceType.datasetChanges);
        assertNotNull(pids);
        assertFalse(pids.isEmpty());
        assertEquals(3, pids.size());
    }

    @Test
    void getSubscriptionsInvalidSubject() {
        String subject = "";
        assertThrows(
            NotAuthorizedException.class,
            () -> dataRepo.getSubscriptions(subject, SubscriptionResourceType.datasetChanges));
    }

    @Test
    void getSubscriptionsInvalidResourceType() {
        assertThrows(
            NotFoundException.class, () -> dataRepo.getSubscriptions(EXPECTED_SUBJECT, null));
    }

    @Test
    void deleteSubscriptionsValidData() {
        final String testSubject = "https://orcid.org/0000-4444-5555-6666";
        final String testPid = "urn:pid:0000-4444-5555-6666";

        assertEquals(
            new Subscription(
                testSubject, SubscriptionResourceType.datasetChanges, List.of(testPid)),
            dataRepo.deleteSubscriptions(
                testSubject, SubscriptionResourceType.datasetChanges,
                List.of(testPid, "nonexistent_pid")));
    }

    @Test
    void testSubscriptionCRUD() {
        // actually CRD - currently no need for an update operation
        final String testSubject = "dn=\"uid=test,o=NCEAS,dc=ecoinformatics,dc=org\"";
        final String testPid1 = "urn:node:1_my_test_pid_1";
        final String testPid2 = "urn:node:2_my_test_pid_2";

        // Add a subscription
        dataRepo.addSubscription(testSubject, SubscriptionResourceType.datasetChanges, testPid1);
        dataRepo.addSubscription(testSubject, SubscriptionResourceType.datasetChanges, testPid2);

        // Retrieve the subscription
        List<String> pids =
            dataRepo.getSubscriptions(testSubject, SubscriptionResourceType.datasetChanges);
        assertNotNull(pids);
        assertEquals(2, pids.size());
        assertTrue(pids.contains(testPid1), "Expected " + testPid1 + " to be in: " + pids);
        assertTrue(pids.contains(testPid2), "Expected " + testPid2 + " to be in: " + pids);

        // Delete the subscription
        Subscription confirmation =
            dataRepo.deleteSubscriptions(
                testSubject, SubscriptionResourceType.datasetChanges,
                List.of(testPid1, testPid2, "nonexistent_pid"));
        assertNotNull(confirmation);
        assertEquals(testSubject, confirmation.subject());
        assertEquals(SubscriptionResourceType.datasetChanges, confirmation.resourceType());
        assertEquals(2, confirmation.resourceIds().size());
        assertTrue(confirmation.resourceIds().contains(testPid1));
        assertTrue(confirmation.resourceIds().contains(testPid2));

        // Verify that the subscription was deleted
        pids = dataRepo.getSubscriptions(testSubject, SubscriptionResourceType.datasetChanges);
        assertNotNull(pids);
        assertTrue(pids.isEmpty());
    }

    @Test
    void getLastSuccessfulRuntimesNoData() {
        assertThrows(
            NotFoundException.class,
            () -> dataRepo.getLastSuccessfulRuntimes(SubscriptionResourceType.citations));
    }

    @Test
    void getLastSuccessfulRuntimesNullResourceType() {
        assertThrows(NotFoundException.class, () -> dataRepo.getLastSuccessfulRuntimes(null));
    }

    @Test
    void setLastSuccessfulRuntimesNullArguments() {
        LastSuccessfulRuntimes runtimes = new LastSuccessfulRuntimes(
            List.of(new LastSuccessfulRuntimes.Entry(System.currentTimeMillis(), "urn:node:NULL")));
        assertThrows(
            NotFoundException.class, () -> dataRepo.setLastSuccessfulRuntimes(null, runtimes));
        assertThrows(
            IllegalArgumentException.class,
            () -> dataRepo.setLastSuccessfulRuntimes(
                SubscriptionResourceType.portalMetaChanges,
                null));
    }

    @Test
    void setLastSuccessfulRuntimesEmptyListIsNoOp() {
        assertDoesNotThrow(
            () -> dataRepo.setLastSuccessfulRuntimes(
                SubscriptionResourceType.citations,
                new LastSuccessfulRuntimes(List.of())));
        assertThrows(
            NotFoundException.class,
            () -> dataRepo.getLastSuccessfulRuntimes(SubscriptionResourceType.citations));
    }

    @Test
    void testLastSuccessfulRuntimesInsertAndUpdate() {
        final SubscriptionResourceType type = SubscriptionResourceType.datasetChanges;
        final long now = System.currentTimeMillis();
        final LastSuccessfulRuntimes.Entry node1 =
            new LastSuccessfulRuntimes.Entry(now - 300_000, "urn:node:INS1");
        final LastSuccessfulRuntimes.Entry node2 =
            new LastSuccessfulRuntimes.Entry(now - 200_000, "urn:node:INS2");

        // Insert several rows in one call
        dataRepo.setLastSuccessfulRuntimes(type, new LastSuccessfulRuntimes(List.of(node1, node2)));
        List<LastSuccessfulRuntimes.Entry> stored =
            dataRepo.getLastSuccessfulRuntimes(type).runtimes();
        assertEquals(2, stored.size());
        assertTrue(stored.contains(node1), "Expected " + node1 + " to be in: " + stored);
        assertTrue(stored.contains(node2), "Expected " + node2 + " to be in: " + stored);

        // Update one existing node & insert a new one: existing node updated, others untouched
        final LastSuccessfulRuntimes.Entry node1Updated =
            new LastSuccessfulRuntimes.Entry(now, node1.nodeId());
        final LastSuccessfulRuntimes.Entry node3 =
            new LastSuccessfulRuntimes.Entry(now - 100_000, "urn:node:INS3");
        dataRepo.setLastSuccessfulRuntimes(
            type, new LastSuccessfulRuntimes(List.of(node1Updated, node3)));
        stored = dataRepo.getLastSuccessfulRuntimes(type).runtimes();
        assertEquals(3, stored.size());
        assertTrue(stored.contains(node1Updated), "Expected " + node1Updated + " in: " + stored);
        assertFalse(stored.contains(node1), "Old entry should have been replaced: " + stored);
        assertTrue(stored.contains(node2), "Expected " + node2 + " to be in: " + stored);
        assertTrue(stored.contains(node3), "Expected " + node3 + " to be in: " + stored);
    }

    @Test
    void setLastSuccessfulRuntimesDuplicateNodeKeepsLatestTimestamp() {
        final SubscriptionResourceType type = SubscriptionResourceType.portalMetaChanges;
        final String nodeId = "urn:node:DUP";
        final long now = System.currentTimeMillis();
        final long latest = now - 10_000;

        dataRepo.setLastSuccessfulRuntimes(
            type, new LastSuccessfulRuntimes(List.of(
                new LastSuccessfulRuntimes.Entry(latest, nodeId),
                new LastSuccessfulRuntimes.Entry(now - 500_000, nodeId))));

        List<LastSuccessfulRuntimes.Entry> stored =
            dataRepo.getLastSuccessfulRuntimes(type).runtimes();
        assertEquals(List.of(new LastSuccessfulRuntimes.Entry(latest, nodeId)), stored);
    }
}
