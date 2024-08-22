package org.dataone.notifications.api.resource;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.dataone.notifications.api.auth.AuthProvider;
import org.dataone.notifications.api.data.DataRepository;
import org.dataone.notifications.util.TestUtils;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the Resource class, to exercise the CRUD API and database operations. Note
 * that AuthProvider is mocked.
 */
class ApiClientIT extends JerseyTest {

    private static final String EXPECTED_PID = "urn:pid:0000-1234-5678-999X";
    private static final String EXPECTED_PID_1A = EXPECTED_PID;
    private static final String EXPECTED_PID_1B = "urn:pid:0000-1111-2222-3333";
    private static final String EXPECTED_PID_1C = "urn:pid:0000-4444-5555-6666";
    private static final String EXPECTED_PID_5 = "urn:pid:0000-1111-3333-5555";
    private static final String VALID_AUTH_HEADER_1 = "Bearer my-totally-valid-token";
    private static final String EXPECTED_SUBJECT_1 = "https://orcid.org/0000-1234-5678-999X";
    private static final String VALID_AUTH_HEADER_2 = "Bearer my-other-totally-valid-token";
    private static final String EXPECTED_SUBJECT_2 = "https://orcid.org/0000-1111-2222-3333";
    private static final String VALID_AUTH_HEADER_3 = "Bearer my-valid-crud-token";
    private static final String EXPECTED_SUBJECT_3 = "dn=\"uid=test,o=NCEAS,dc=dataone,dc=org\"";
    private static final String VALID_AUTH_HEADER_4 = "Bearer my-valid-unsubscription-token";
    private static final String EXPECTED_SUBJECT_4 = "https://orcid.org/0000-7777-8888-9999";
    private static final String VALID_AUTH_HEADER_5 = "Bearer my-other-valid-crud-token";
    private static final String EXPECTED_SUBJECT_5 = "https://orcid.org/0000-1111-3333-5555";

    private static final String INVALID_AUTH_HEADER = "Bearer my-naughty-non-valid-token";
    private static final ResourceType EXPECTED_RESOURCE_TYPE = ResourceType.datasets;
    private static final List<String> REQUESTED_PID_LIST = new ArrayList<>();
    private static final List<String> EXPECTED_PID_LIST = new ArrayList<>();
    private static final String DATASETS = "/" + ResourceType.datasets + "/";

    private static Resource resource;
    private static PostgreSQLContainer<?> pg;

    @BeforeAll
    static void oneTimeSetUp() {
        pg = TestUtils.getTestDb();
        DataRepository dataRepository = TestUtils.getTestDataRepository(pg);
        AuthProvider mockAuthProvider = getAuthProvider();
        resource = new Resource(mockAuthProvider, dataRepository);
    }

    @AfterAll
    static void oneTimeTearDown() {
        if (pg != null) {
            pg.stop();
        }
    }

    private static AuthProvider getAuthProvider() {
        AuthProvider mockAuthProvider = mock(AuthProvider.class);
        when(mockAuthProvider.authenticate(VALID_AUTH_HEADER_1)).thenReturn(EXPECTED_SUBJECT_1);
        when(mockAuthProvider.authenticate(VALID_AUTH_HEADER_2)).thenReturn(EXPECTED_SUBJECT_2);
        when(mockAuthProvider.authenticate(VALID_AUTH_HEADER_3)).thenReturn(EXPECTED_SUBJECT_3);
        when(mockAuthProvider.authenticate(VALID_AUTH_HEADER_4)).thenReturn(EXPECTED_SUBJECT_4);
        when(mockAuthProvider.authenticate(VALID_AUTH_HEADER_5)).thenReturn(EXPECTED_SUBJECT_5);

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

    @Override
    protected Application configure() {
        ResourceConfig config = new ResourceConfig();
        return config.registerInstances(Resource.class, resource);
    }

    @ParameterizedTest
    @ValueSource(strings = {"datasets"})
    void post(String resourceType) {
        Response response = doPost(VALID_AUTH_HEADER_1, "/" + resourceType + "/" + EXPECTED_PID,
                                   Response.Status.OK);
        assertJsonContentType(response);
        String body = getBody(response);
        assertTrue(body.contains(EXPECTED_PID), "body didn't contain expected PID: " + body);
    }

    @Test
    void post_invalidAuth() {
        Response response =
            doPost(INVALID_AUTH_HEADER, DATASETS + EXPECTED_PID, Response.Status.UNAUTHORIZED);
        String body = getBody(response);
        assertFalse(body.contains(EXPECTED_PID), "body didn't contain expected PID: " + body);
    }

    @Test
    void get() {
        Response response = doGet(VALID_AUTH_HEADER_1, DATASETS, Response.Status.OK);
        assertJsonContentType(response);
        String body = getBody(response);
        List<String> pids = getPids(body);
        assertEquals(3, pids.size());
        assertTrue(pids.contains(EXPECTED_PID_1A), "body didn't contain expected PID: " + body);
        assertTrue(pids.contains(EXPECTED_PID_1B), "body didn't contain expected PID: " + body);
        assertTrue(pids.contains(EXPECTED_PID_1C), "body didn't contain expected PID: " + body);
    }

    @Test
    void get_invalidAuth() {
        Response response = doGet(INVALID_AUTH_HEADER, DATASETS, Response.Status.UNAUTHORIZED);
        assertEquals(getBody(response), "");
    }

    @ParameterizedTest
    @ValueSource(strings = {"datasets"})
    void delete(String resourceType) {
        Response response = doDelete(VALID_AUTH_HEADER_5, "/" + resourceType + "/" + EXPECTED_PID_5,
                                     Response.Status.OK);
        assertJsonContentType(response);
        assertEquals(
            MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
        String body = getBody(response);
        assertTrue(body.contains(EXPECTED_PID_5), "body didn't contain expected PID: " + body);
    }

    @Test
    void delete_invalidAuth() {
        Response response =
            doDelete(INVALID_AUTH_HEADER, DATASETS + EXPECTED_PID, Response.Status.UNAUTHORIZED);
        String body = getBody(response);
        assertFalse(body.contains(EXPECTED_PID), "body didn't contain expected PID: " + body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"datasets"})
    void testSubscriptionCRUD(String resourceType) {

        // actually CRD - currently no need for an update operation
        final String testSubject = "dn=\"uid=test,o=NCEAS,dc=ecoinformatics,dc=org\"";
        final String testPid1 = "urn:node:1_my_api_client_test_pid_1";
        final String testPid2 = "urn:node:2_my_api_client_test_pid_2";

        // Get count of existing subscriptions for this test user
        String body = getBody(doGet(VALID_AUTH_HEADER_3, "/" + resourceType, Response.Status.OK));
        int startingPids = getPids(body).size();
        System.out.println("Starting pids: " + startingPids + "; body: " + body);

        // Add a subscription
        doPost(VALID_AUTH_HEADER_3, "/" + resourceType + "/" + testPid1, Response.Status.OK);
        doPost(VALID_AUTH_HEADER_3, "/" + resourceType + "/" + testPid2, Response.Status.OK);

        // Retrieve the subscription
        body = getBody(doGet(VALID_AUTH_HEADER_3, "/" + resourceType, Response.Status.OK));
        List<String> pids = getPids(body);
        assertEquals(startingPids + 2, pids.size());
        assertTrue(pids.contains(testPid1), "body didn't contain testPid1: " + body);
        assertTrue(pids.contains(testPid2), "body didn't contain testPid2: " + body);

        // Delete one subscription
        doDelete(VALID_AUTH_HEADER_3, "/" + resourceType + "/" + testPid1, Response.Status.OK);
        // then check again
        body = getBody(doGet(VALID_AUTH_HEADER_3, "/" + resourceType, Response.Status.OK));
        pids = getPids(body);
        assertEquals(startingPids + 1, pids.size());
        assertFalse(pids.contains(testPid1));
        assertTrue(pids.contains(testPid2));

        // Delete the other subscription
        doDelete(VALID_AUTH_HEADER_3, "/" + resourceType + "/" + testPid2, Response.Status.OK);
        // then check again
        body = getBody(doGet(VALID_AUTH_HEADER_3, "/" + resourceType, Response.Status.OK));
        pids = getPids(body);
        assertEquals(startingPids, pids.size());
        assertFalse(pids.contains(testPid1));
        assertFalse(pids.contains(testPid2));
    }

    private Response doPost(String authHeader, String targetURI, Response.Status expectedStatus) {
        Response response = target(targetURI).request().accept(MediaType.APPLICATION_JSON)
            .header("Authorization", authHeader).post(null);
        assertEquals(expectedStatus.getStatusCode(), response.getStatus());
        return response;
    }

    private Response doGet(String authHeader, String targetURI, Response.Status expectedStatus) {
        Response response = target(targetURI).request().accept(MediaType.APPLICATION_JSON)
            .header("Authorization", authHeader).get();
        assertEquals(expectedStatus.getStatusCode(), response.getStatus());

        return response;
    }

    private Response doDelete(String authHeader, String targetURI, Response.Status expectedStatus) {
        Response response = target(targetURI).request().accept(MediaType.APPLICATION_JSON)
            .header("Authorization", authHeader).delete();
        assertEquals(expectedStatus.getStatusCode(), response.getStatus());
        return response;
    }

    private String getBody(Response response) {
        String body = response.readEntity(String.class);
        assertNotNull(body);
        return body;
    }

    private void assertJsonContentType(Response response) {
        assertEquals(MediaType.APPLICATION_JSON,
                     response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    }

    private List<String> getPids(String body) {
        String pidsListString = body.substring(body.indexOf('[') + 1, body.indexOf(']'));
        pidsListString = pidsListString
            .replaceAll("^\"", "").replaceAll("\"$", "");
        pidsListString = pidsListString.replace("\",\"", "|").replace("\\", "\"");
        String[] pidsArray = pidsListString.split("\\|");

        return (pidsArray[0].isBlank()) ? new ArrayList<>() : List.of(pidsArray);
    }
}
