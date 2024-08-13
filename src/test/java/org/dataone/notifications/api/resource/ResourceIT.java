package org.dataone.notifications.api.resource;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.api.auth.AuthProvider;
import org.dataone.notifications.api.data.NsDataProvider;
import org.dataone.notifications.api.data.NsTestDataSource;
import org.dataone.notifications.api.data.Subscription;
import org.dataone.notifications.util.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Resource class, to exercise the CRUD API and database operations. Note
 * that AuthProvider is mocked.
 */
class ResourceIT {

    private static final String VALID_AUTH_HEADER_1 = "Bearer my-totally-valid-token";
    private static final String EXPECTED_SUBJECT_1 = "https://orcid.org/0000-1234-5678-999X";

    private static final String VALID_AUTH_HEADER_2 = "Bearer my-other-totally-valid-token";
    private static final String EXPECTED_SUBJECT_2 = "https://orcid.org/0000-1111-2222-3333";

    private static final String VALID_AUTH_HEADER_3 = "Bearer my-valid-crud-token";
    private static final String EXPECTED_SUBJECT_3 = "dn=\"uid=test,o=NCEAS,dc=dataone,dc=org\"";

    private static final String VALID_AUTH_HEADER_4 = "Bearer my-valid-unsubscription-token";
    private static final String EXPECTED_SUBJECT_4 = "https://orcid.org/0000-7777-8888-9999";

    private static final String INVALID_AUTH_HEADER = "Bearer my-naughty-non-valid-token";

    private static final ResourceType EXPECTED_RESOURCE_TYPE = ResourceType.datasets;
    public static final String EXPECTED_PID = "urn:pid:0000-1234-5678-999X";
    private static final String EXPECTED_PID_1A = EXPECTED_PID;
    public static final String EXPECTED_PID_1B = "urn:pid:0000-1111-2222-3333";
    public static final String EXPECTED_PID_1C = "urn:pid:0000-4444-5555-6666";
    private static final String EXPECTED_PID_4 = "urn:pid:0000-7777-8888-9999";
    private static final List<String> REQUESTED_PID_LIST = new ArrayList<>();
    private static final List<String> EXPECTED_PID_LIST = new ArrayList<>();
//    private static final Subscription EXPECTED_PARAMS =
//        new Subscription(EXPECTED_SUBJECT_1, EXPECTED_RESOURCE_TYPE, EXPECTED_PID_LIST);

    public static Resource resource;
    private static PostgreSQLContainer<?> pg;

    @BeforeAll
    static void oneTimeSetUp() {

        EXPECTED_PID_LIST.add(EXPECTED_PID);
        EXPECTED_PID_LIST.add(EXPECTED_PID_1B);
        EXPECTED_PID_LIST.add(EXPECTED_PID_1C);

        REQUESTED_PID_LIST.addAll(EXPECTED_PID_LIST);
        REQUESTED_PID_LIST.add(EXPECTED_PID); //purposely add duplicates
        REQUESTED_PID_LIST.add(EXPECTED_PID); //purposely add duplicates

        AuthProvider mockAuthProvider = getAuthProvider();
        pg = TestUtils.getTestDb();
        resource =
            new Resource(mockAuthProvider, new NsDataProvider(NsTestDataSource.getInstance(pg)));
    }

    @AfterAll
    static void oneTimeTearDown() {
        if (pg != null) {
            pg.stop();
        }
    }

    @Test
    void getSubscriptions() {
        // HAPPY PATH
        Subscription result = (Subscription) resource.getSubscriptions(VALID_AUTH_HEADER_1,
                                                                       ResourceType.datasets.toString());
        assertNotNull(result);
        assertEquals(EXPECTED_SUBJECT_1, result.subject());
        assertEquals(EXPECTED_RESOURCE_TYPE, result.resourceType());
        assertEquals(3, result.resourceIds().size());
        assertTrue(result.resourceIds().contains(EXPECTED_PID_1A));
        assertTrue(result.resourceIds().contains(EXPECTED_PID_1B));
        assertTrue(result.resourceIds().contains(EXPECTED_PID_1C));
    }

    @Test
    void validSubscribe() {
        Subscription result =
            (Subscription) resource.subscribe(VALID_AUTH_HEADER_2, ResourceType.datasets.toString(),
                                              EXPECTED_PID);
        assertNotNull(result);
        assertEquals(EXPECTED_SUBJECT_2, result.subject());
        assertEquals(EXPECTED_RESOURCE_TYPE, result.resourceType());
        assertEquals(1, result.resourceIds().size());
        assertTrue(result.resourceIds().contains(EXPECTED_PID));
    }

    @Test
    void subscribe_missingPid() {
        try {
            resource.subscribe(VALID_AUTH_HEADER_1, ResourceType.datasets.toString(), null);
            fail("Expected NotFoundException");
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("pid"));
        }
    }

    @Test
    void subscribe_missingResourceType() {

        Exception thrown = assertThrows(NotFoundException.class,
                                        () -> resource.subscribe(VALID_AUTH_HEADER_1, null,
                                                                 EXPECTED_PID),
                                        "Expected subscribe() to throw NotFoundException");

        assertTrue(
            thrown.getMessage().contains("resource"),
            "Expected message to contain 'resource', but was: " + thrown.getMessage());
    }

    @Test
    void subscribe_missingAuthHeader() {

        Exception thrown = assertThrows(NotAuthorizedException.class, () -> resource.subscribe(null,
                                                                                               ResourceType.datasets.toString(),
                                                                                               EXPECTED_PID),
                                        "Expected subscribe() to throw NotAuthorizedException");
        assertTrue(thrown.getMessage().contains("401"),
                   "Expected message to contain '401', but was: " + thrown.getMessage());
    }

    @Test
    void subscribe_unauthorized() {
        Exception thrown = assertThrows(NotAuthorizedException.class,
                                        () -> resource.subscribe(INVALID_AUTH_HEADER,
                                                                 ResourceType.datasets.toString(),
                                                                 EXPECTED_PID),
                                        "Expected subscribe() to throw NotAuthorizedException");
        assertTrue(
            thrown.getMessage().contains("Unauthorized"),
            "Expected message to contain 'Unauthorized', but was: " + thrown.getMessage());
    }

    @Test
    void unsubscribe_notAlreadySubscribed() {
        Subscription result = (Subscription) resource.unsubscribe(VALID_AUTH_HEADER_4,
                                                                  ResourceType.datasets.toString(),
                                                                  EXPECTED_PID);
        assertNotNull(result);
        assertEquals(EXPECTED_SUBJECT_4, result.subject());
        assertEquals(EXPECTED_RESOURCE_TYPE, result.resourceType());
        assertTrue(result.resourceIds().isEmpty());
    }

    @Test
    void validUnsubscribe() {
        Subscription result = (Subscription) resource.unsubscribe(VALID_AUTH_HEADER_4,
                                                                  ResourceType.datasets.toString(),
                                                                  EXPECTED_PID_4);
        assertNotNull(result);
        assertEquals(EXPECTED_SUBJECT_4, result.subject());
        assertEquals(EXPECTED_RESOURCE_TYPE, result.resourceType());
        assertEquals(1, result.resourceIds().size());
        assertTrue(result.resourceIds().contains(EXPECTED_PID_4));
    }

    @Test
    void testSubscriptionCRUD() {
        // actually CRD - currently no need for an update operation
        final String testPid1 = "urn:node:1_my_test_pid_1";
        final String testPid2 = "urn:node:2_my_test_pid_2";

        // Add a subscription
        resource.subscribe(VALID_AUTH_HEADER_3, ResourceType.datasets.toString(), testPid1);
        resource.subscribe(VALID_AUTH_HEADER_3, ResourceType.datasets.toString(), testPid2);

        // Retrieve the subscription
        Subscription result =
            (Subscription) resource.getSubscriptions(VALID_AUTH_HEADER_3, ResourceType.datasets.toString());
        assertNotNull(result);
        assertEquals(2, result.resourceIds().size());
        assertTrue(
            result.resourceIds().contains(testPid1),
            "Expected " + testPid1 + " to be in: " + result.resourceIds());
        assertTrue(
            result.resourceIds().contains(testPid2),
            "Expected " + testPid2 + " to be in: " + result.resourceIds());

        // Delete pid1 subscription
        Subscription confirm1 =
            (Subscription) resource.unsubscribe(VALID_AUTH_HEADER_3, ResourceType.datasets.toString(),
                                                testPid1);
        assertNotNull(confirm1);
        assertEquals(EXPECTED_SUBJECT_3, confirm1.subject());
        assertEquals(ResourceType.datasets, confirm1.resourceType());
        assertEquals(1, confirm1.resourceIds().size());
        assertTrue(confirm1.resourceIds().contains(testPid1));
        assertFalse(confirm1.resourceIds().contains(testPid2));

        // Delete pid2 subscription
        Subscription confirm2 =
            (Subscription) resource.unsubscribe(VALID_AUTH_HEADER_3, ResourceType.datasets.toString(),
                                                testPid2);
        assertNotNull(confirm2);
        assertEquals(EXPECTED_SUBJECT_3, confirm2.subject());
        assertEquals(ResourceType.datasets, confirm2.resourceType());
        assertEquals(1, confirm2.resourceIds().size());
        assertFalse(confirm2.resourceIds().contains(testPid1));
        assertTrue(confirm2.resourceIds().contains(testPid2));

        // Verify that the subscription was deleted
        Subscription finalResult =
            (Subscription) resource.getSubscriptions(VALID_AUTH_HEADER_3, ResourceType.datasets.toString());
        assertNotNull(finalResult);
        assertTrue(finalResult.resourceIds().isEmpty());
    }


    private static @NotNull AuthProvider getAuthProvider() {
        AuthProvider mockAuthProvider = mock(AuthProvider.class);
        when(mockAuthProvider.authenticate(VALID_AUTH_HEADER_1)).thenReturn(EXPECTED_SUBJECT_1);
        when(mockAuthProvider.authenticate(VALID_AUTH_HEADER_2)).thenReturn(EXPECTED_SUBJECT_2);
        when(mockAuthProvider.authenticate(VALID_AUTH_HEADER_3)).thenReturn(EXPECTED_SUBJECT_3);
        when(mockAuthProvider.authenticate(VALID_AUTH_HEADER_4)).thenReturn(EXPECTED_SUBJECT_4);

        when(mockAuthProvider.authenticate(INVALID_AUTH_HEADER)).thenThrow(
            new NotAuthorizedException("Unauthorized"));

        when(mockAuthProvider.authorize(EXPECTED_SUBJECT_1, EXPECTED_RESOURCE_TYPE,
                                        REQUESTED_PID_LIST)).thenReturn(
            new HashSet<>(EXPECTED_PID_LIST));
        when(mockAuthProvider.authorize(VALID_AUTH_HEADER_3, EXPECTED_RESOURCE_TYPE,
                                        REQUESTED_PID_LIST)).thenReturn(
            new HashSet<>(EXPECTED_PID_LIST));

        return mockAuthProvider;
    }
}
