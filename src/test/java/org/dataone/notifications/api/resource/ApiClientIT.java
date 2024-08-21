package org.dataone.notifications.api.resource;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.dataone.notifications.api.auth.AuthProvider;
import org.dataone.notifications.api.data.DBConnectionParams;
import org.dataone.notifications.api.data.DBMigrator;
import org.dataone.notifications.api.data.DataRepository;
import org.dataone.notifications.api.data.NsDBMigrator;
import org.dataone.notifications.api.data.NsDataRepository;
import org.dataone.notifications.util.TestUtils;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final String EXPECTED_PID_1B = "urn:pid:0000-1111-2222-3333";
    private static final String EXPECTED_PID_1C = "urn:pid:0000-4444-5555-6666";
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
    private static final String EXPECTED_PID_1A = EXPECTED_PID;
    private static final String EXPECTED_PID_4 = "urn:pid:0000-7777-8888-9999";
    private static final List<String> REQUESTED_PID_LIST = new ArrayList<>();
    private static final List<String> EXPECTED_PID_LIST = new ArrayList<>();

    private static final Resource resource;
    private static final PostgreSQLContainer<?> pg;
    private static final TestUtils.DataRepoObjects dataRepoObjects;
    private static final AuthProvider mockAuthProvider;


    static {
        pg = TestUtils.getTestDb();
        dataRepoObjects = TestUtils.getTestDataRepository(pg);
        mockAuthProvider = getAuthProvider();
        resource = new Resource(mockAuthProvider, dataRepoObjects.dataRepository());
    }

    @Override
    protected Application configure() {
        ResourceConfig config = new ResourceConfig();
        return config.registerInstances(Resource.class, resource);
//        ResourceConfig config = new ResourceConfig();
//        // Register the pre-instantiated classes, to disable injection
//        config.registerInstances(Resource.class, resource);
//        config.registerInstances(AuthProvider.class, mockAuthProvider);
//        config.registerInstances(DBConnectionParams.class, dataRepoObjects.dbConnectionParams());
//        config.registerInstances(DataSource.class, dataRepoObjects.dataSource());
//        config.registerInstances(NsDataRepository.class, dataRepoObjects.dataRepository());
//        config.registerInstances(NsDBMigrator.class, dataRepoObjects.migrator());
//        return config;
    }

    @AfterAll
    static void oneTimeTearDown() {
        if (pg != null) {
            pg.stop();
        }
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

    @ParameterizedTest
    @ValueSource(strings = {"datasets"})
    void validSubscribe(String resourceType) {

        Response response =
            target("/" + resourceType + "/" + EXPECTED_PID)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", VALID_AUTH_HEADER_1)
                .post(null);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(
            MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void validGetSubscriptions() {

        Response response = target("/datasets/")
            .request()
            .accept(MediaType.APPLICATION_JSON)
            .header("Authorization", VALID_AUTH_HEADER_1)
            .get();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(
            MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));
        String body = response.readEntity(String.class);
        assertNotNull(body);
        assertTrue(body.contains(EXPECTED_PID_1A));
        assertTrue(body.contains(EXPECTED_PID_1B));
        assertTrue(body.contains(EXPECTED_PID_1C));
    }
}
