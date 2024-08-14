package org.dataone.notifications.api.resource;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.api.auth.AuthProvider;
import org.dataone.notifications.api.data.NsDataProvider;
import org.dataone.notifications.api.data.Subscription;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResourceTest {

    private static final String EXPECTED_SUBJECT = "https://orcid.org/0000-1234-5678-999X";
    private static final ResourceType EXPECTED_RESOURCE_TYPE = ResourceType.datasets;
    private static final String EXPECTED_PID = "urn:mypid:12345-67890";
    private static final List<String> REQUESTED_PID_LIST = new ArrayList<>();
    private static final List<String> EXPECTED_PID_LIST = new ArrayList<>();
    private static final Subscription EXPECTED_PARAMS_ONEPID =
        new Subscription(EXPECTED_SUBJECT, EXPECTED_RESOURCE_TYPE, List.of(EXPECTED_PID));
    private static final Subscription EXPECTED_PARAMS_MULTIPID =
        new Subscription(EXPECTED_SUBJECT, EXPECTED_RESOURCE_TYPE, EXPECTED_PID_LIST);
    private static final String VALID_AUTH_HEADER = "Bearer my-totally-valid-token";
    private static final String INVALID_AUTH_HEADER = "Bearer my-naughty-non-valid-token";
    private static Resource resource;


    @BeforeAll
    static void setUp() {

        EXPECTED_PID_LIST.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50251");
        EXPECTED_PID_LIST.add("urn:uuid:1add8838-861b-4afb-af00-7b2ecca585bf");
        EXPECTED_PID_LIST.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50233");
        EXPECTED_PID_LIST.add(EXPECTED_PID);

        REQUESTED_PID_LIST.addAll(EXPECTED_PID_LIST);
        REQUESTED_PID_LIST.add(EXPECTED_PID); //purposely add duplicates
        REQUESTED_PID_LIST.add(EXPECTED_PID); //purposely add duplicates

        AuthProvider mockAuthProvider = mock(AuthProvider.class);
        when(mockAuthProvider.authenticate(VALID_AUTH_HEADER)).thenReturn(EXPECTED_SUBJECT);
        when(mockAuthProvider.authenticate(INVALID_AUTH_HEADER)).thenThrow(
            new NotAuthorizedException("Unauthorized"));

        when(mockAuthProvider.authorize(EXPECTED_SUBJECT, EXPECTED_RESOURCE_TYPE,
                                        REQUESTED_PID_LIST)).thenReturn(
            new HashSet<>(EXPECTED_PID_LIST));

        NsDataProvider mockDataProvider = mock(NsDataProvider.class);
        when(
            mockDataProvider.getSubscriptions(EXPECTED_SUBJECT, EXPECTED_RESOURCE_TYPE)).thenReturn(
            EXPECTED_PID_LIST);
        doThrow(new NotAuthorizedException("Access Denied")).when(mockDataProvider)
            .addSubscription(null, ResourceType.datasets, EXPECTED_PID);
        when(mockDataProvider.addSubscription(EXPECTED_SUBJECT, EXPECTED_RESOURCE_TYPE,
                                              EXPECTED_PID)).thenReturn(EXPECTED_PARAMS_ONEPID);

        when(mockDataProvider.deleteSubscriptions(EXPECTED_SUBJECT, EXPECTED_RESOURCE_TYPE,
                                                  List.of(EXPECTED_PID))).thenReturn(
            EXPECTED_PARAMS_ONEPID);
        resource = new Resource(mockAuthProvider, mockDataProvider);
    }

    @Test
    void getSubscriptions() {
        // HAPPY PATH
        Subscription result = (Subscription) resource.getSubscriptions(VALID_AUTH_HEADER,
                                                                       ResourceType.datasets);
        assertNotNull(result);
        assertEquals(EXPECTED_PARAMS_MULTIPID.subject(), result.subject());
        assertEquals(EXPECTED_PARAMS_MULTIPID.resourceType(), result.resourceType());
        assertEquals(4, result.resourceIds().size());
        assertTrue(result.resourceIds().get(0).contains("urn:uuid:0"));
        assertTrue(result.resourceIds().get(1).contains("urn:uuid:1"));
    }

    @Test
    void validSubscribe() {
        Subscription result =
            (Subscription) resource.subscribe(VALID_AUTH_HEADER, ResourceType.datasets.toString(),
                                              EXPECTED_PID);
        assertNotNull(result);
        assertEquals(EXPECTED_PARAMS_ONEPID.subject(), result.subject());
        assertEquals(EXPECTED_PARAMS_ONEPID.resourceType(), result.resourceType());
        assertEquals(1, result.resourceIds().size());
        assertTrue(result.resourceIds().contains(EXPECTED_PID));
    }

    @Test
    void subscribe_missingPid() {
        try {
            resource.subscribe(VALID_AUTH_HEADER, ResourceType.datasets.toString(), null);
            fail("Expected NotFoundException");
        } catch (NotFoundException e) {
            assertTrue(e.getMessage().contains("pid"));
        }
    }

    @Test
    void subscribe_missingResourceType() {

        Exception thrown = assertThrows(NotFoundException.class,
                                        () -> resource.subscribe(VALID_AUTH_HEADER, null,
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
    void validUnsubscribe() {
        Subscription result =
            (Subscription) resource.unsubscribe(VALID_AUTH_HEADER, ResourceType.datasets.toString(),
                                                EXPECTED_PID);
        assertNotNull(result);
        assertEquals(EXPECTED_PARAMS_ONEPID, result);
    }
}
