package org.dataone.notifications.api.auth;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.api.resource.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class NsAuthProviderTest {

    private static final String EXPECTED_SUBJECT = "https://orcid.org/0000-2222-4444-999X";
    private NsAuthProvider authProvider;

    @BeforeEach
    void setUp() {
        authProvider = new NsAuthProvider();
    }

    @Test
    void authenticateValidToken() {
        String authHeader = "Bearer validToken";
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
            authProvider.authorize(EXPECTED_SUBJECT, ResourceType.DATASETS, requested_pids);
        assertEquals(expectedPids, actualPids);
    }

    @Test
    void authorizeBlankSubject() {
        String subject = "";
        List<String> requested_pids = List.of("pid1", "pid2");
        assertThrows(
            NotAuthorizedException.class,
            () -> authProvider.authorize(subject, ResourceType.DATASETS, requested_pids));
    }

    @Test
    void authorizeNullPids() {
        assertThrows(
            NotFoundException.class,
            () -> authProvider.authorize(EXPECTED_SUBJECT, ResourceType.DATASETS, null));
    }

    @Test
    void authorizeEmptyPids() {
        List<String> empty_pids_list = List.of();
        assertThrows(
            NotFoundException.class,
            () -> authProvider.authorize(EXPECTED_SUBJECT, ResourceType.DATASETS, empty_pids_list));
    }
}
