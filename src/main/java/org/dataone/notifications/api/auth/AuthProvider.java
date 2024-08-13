package org.dataone.notifications.api.auth;

import jakarta.ws.rs.NotAuthorizedException;
import org.dataone.notifications.api.resource.ResourceType;

import java.util.List;
import java.util.Set;

/**
 * An interface for authenticating and authorizing users.
 */
public interface AuthProvider {

    /**
     * Authenticates a user based on a token.
     *
     * @param authHeader the {@code "Authorization: Bearer $TOKEN"} header, containing the token to
     *                   be authenticated
     * @return the subject of the authenticated user
     * @throws NotAuthorizedException if the user cannot be authenticated
     */
    String authenticate(String authHeader) throws NotAuthorizedException;

    /**
     * Verify that a user is Authorized to access a resource. NOTE: Assumes the {@code subject} has
     * already been authenticated  you MUST call the {@code authenticate} method before calling this
     * method.
     *
     * @param subject the subject of the user
     * @param pids    a List of pids, identifying the resources to which the user wishes to
     *                subscribe
     * @throws NotAuthorizedException if the user is not authorized
     */
    Set<String> authorize(String subject, ResourceType resourceType, List<String> pids)
        throws NotAuthorizedException;
}
