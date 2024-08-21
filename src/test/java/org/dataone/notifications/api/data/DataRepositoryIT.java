package org.dataone.notifications.api.data;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.api.resource.ResourceType;
import org.dataone.notifications.util.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRepositoryIT {

    public static final String EXPECTED_SUBJECT = "https://orcid.org/0000-1234-5678-999X";
    public static final String EXPECTED_PID = "pid1";
    private static final Subscription EXPECTED_RECORD =
        new Subscription(EXPECTED_SUBJECT, ResourceType.datasets, List.of(EXPECTED_PID));
    private static PostgreSQLContainer<?> pg;
    private DataRepository dataRepo;

    @BeforeAll
    static void oneTimeSetUp() {
        pg = TestUtils.getTestDb();
    }

    @AfterAll
    static void oneTimeTearDown() throws InterruptedException {
        assertNotNull(pg, "Postgres Instance is null - cannot shut down cleanly!");
        pg.stop();
        Thread.sleep(1000);
        System.out.println("DataRepositoryIT: Postgres Instance stopped.");
    }

    @BeforeEach
    void perTestSetUp() {
        dataRepo = TestUtils.getTestDataRepository(pg).dataRepository();
    }

    @Test
    void addSubscriptionValidData() {
        assertEquals(EXPECTED_RECORD,
                     dataRepo.addSubscription(EXPECTED_SUBJECT, ResourceType.datasets,
                                              EXPECTED_PID));
    }

    @Test
    void addSubscriptionInvalidSubject() {
        String blankSubject = "";
        assertThrows(NotAuthorizedException.class,
                     () -> dataRepo.addSubscription(blankSubject, ResourceType.datasets,
                                                    EXPECTED_PID));
    }

    @Test
    void addSubscriptionInvalidData() {
        String blankPid = "";
        assertThrows(NotFoundException.class,
                     () -> dataRepo.addSubscription(EXPECTED_SUBJECT, ResourceType.datasets,
                                                    blankPid));
    }

    @Test
    void getSubscriptionsValidSubject() {
        List<String> pids = dataRepo.getSubscriptions(EXPECTED_SUBJECT, ResourceType.datasets);
        assertNotNull(pids);
        assertFalse(pids.isEmpty());
        assertEquals(3, pids.size());
    }

    @Test
    void getSubscriptionsInvalidSubject() {
        String subject = "";
        assertThrows(NotAuthorizedException.class,
                     () -> dataRepo.getSubscriptions(subject, ResourceType.datasets));
    }

    @Test
    void getSubscriptionsInvalidResourceType() {
        assertThrows(NotFoundException.class,
                     () -> dataRepo.getSubscriptions(EXPECTED_SUBJECT, null));
    }

    @Test
    void deleteSubscriptionsValidData() {
        final String testSubject = "https://orcid.org/0000-4444-5555-6666";
        final String testPid = "urn:pid:0000-4444-5555-6666";

        assertEquals(new Subscription(testSubject, ResourceType.datasets, List.of(testPid)),
                     dataRepo.deleteSubscriptions(testSubject, ResourceType.datasets,
                                                  List.of(testPid, "nonexistent_pid")));
    }

    @Test
    void testSubscriptionCRUD() {
        // actually CRD - currently no need for an update operation
        final String testSubject = "dn=\"uid=test,o=NCEAS,dc=ecoinformatics,dc=org\"";
        final String testPid1 = "urn:node:1_my_test_pid_1";
        final String testPid2 = "urn:node:2_my_test_pid_2";

        // Add a subscription
        dataRepo.addSubscription(testSubject, ResourceType.datasets, testPid1);
        dataRepo.addSubscription(testSubject, ResourceType.datasets, testPid2);

        // Retrieve the subscription
        List<String> pids = dataRepo.getSubscriptions(testSubject, ResourceType.datasets);
        assertNotNull(pids);
        assertEquals(2, pids.size());
        assertTrue(pids.contains(testPid1), "Expected " + testPid1 + " to be in: " + pids);
        assertTrue(pids.contains(testPid2), "Expected " + testPid2 + " to be in: " + pids);

        // Delete the subscription
        Subscription confirmation = dataRepo.deleteSubscriptions(testSubject, ResourceType.datasets,
                                                                 List.of(testPid1, testPid2,
                                                                         "nonexistent_pid"));
        assertNotNull(confirmation);
        assertEquals(testSubject, confirmation.subject());
        assertEquals(ResourceType.datasets, confirmation.resourceType());
        assertEquals(2, confirmation.resourceIds().size());
        assertTrue(confirmation.resourceIds().contains(testPid1));
        assertTrue(confirmation.resourceIds().contains(testPid2));

        // Verify that the subscription was deleted
        pids = dataRepo.getSubscriptions(testSubject, ResourceType.datasets);
        assertNotNull(pids);
        assertTrue(pids.isEmpty());
    }
}
