package org.dataone.notifications.api.auth;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.api.resource.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

class NsAuthProviderTest {

    private static final String EXPECTED_SUBJECT = "https://orcid.org/0000-1234-5678-999X";
    private NsAuthProvider authProvider;
    private D1CnAuthUtil util;

    @BeforeEach
    void setUp() throws MalformedURLException {
        URL mockUrl = URI.create("http://localhost/authenticate").toURL();
        util = new D1CnAuthUtil(mockUrl);
        authProvider = new NsAuthProvider(util);
    }

    @Test
    void authenticateValidToken() {
        String authHeader = "Bearer validToken";
        D1CnAuthUtil utilSpy = spy(util);
        doReturn(EXPECTED_SUBJECT).when(utilSpy).getSubject("validToken");
        authProvider = new NsAuthProvider(utilSpy);

        assertEquals(EXPECTED_SUBJECT, authProvider.authenticate(authHeader));
    }

    @Test
    void authenticateInvalidToken() {
        String authHeader = "InvalidToken";
        assertThrows(NotAuthorizedException.class, () -> authProvider.authenticate(authHeader));
    }

    @Test
    void authorizeValidSubjectAndPids() {
        List<String> requested_pids = List.of("pid1", "pid2", "pid1", "pid2", "pid3");
        Set<String> expectedPids = Set.of("pid1", "pid2", "pid3");

        Set<String> actualPids =
            authProvider.authorize(EXPECTED_SUBJECT, ResourceType.datasetChanges, requested_pids);
        assertEquals(expectedPids, actualPids);
    }

    @Test
    void authorizeBlankSubject() {
        String subject = "";
        List<String> requested_pids = List.of("pid1", "pid2");
        assertThrows(
            NotAuthorizedException.class,
            () -> authProvider.authorize(subject, ResourceType.datasetChanges, requested_pids));
    }

    @Test
    void authorizeNullPids() {
        assertThrows(
            NotFoundException.class,
            () -> authProvider.authorize(EXPECTED_SUBJECT, ResourceType.datasetChanges, null));
    }

    @Test
    void authorizeEmptyPids() {
        List<String> empty_pids_list = List.of();
        assertThrows(
            NotFoundException.class,
            () -> authProvider.authorize(EXPECTED_SUBJECT, ResourceType.datasetChanges, empty_pids_list));
    }
}
