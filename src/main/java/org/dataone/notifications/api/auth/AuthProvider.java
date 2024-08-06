package org.dataone.notifications.api.auth;

import jakarta.security.auth.message.AuthException;

/**
 * An interface for authenticating and authorizing users.
 */
public interface AuthProvider {

    /**
     * Authenticates a user based on a token.
     *
     * @param token the token to authenticate
     * @return the subject of the authenticated user
     */
    String authenticate(String token);

    /**
     * Verify that a user is Authorized to access a resource.
     *
     * @param subject the subject of the user
     * @param pid the pid of the resource
     * @throws AuthException if the user is not authorized
     */
    void authorize(String subject, String pid) throws AuthException;
}
