package org.dataone.notifications.api.auth;

import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.api.resource.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.logging.log4j.util.Strings.isBlank;

/**
 * An implementation of the AuthProvider interface for the notification service.
 */
@Singleton
@Default
public class NsAuthProvider implements AuthProvider {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());

    @Override
    public String authenticate(String authHeader) throws NotAuthorizedException {

        String token;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            log.debug("Authenticating token: {}[redacted]{}", token.substring(0, 3),
                      token.substring(token.length() - 3));
        } else {
            log.debug("No Auth token found - throwing NotAuthorizedException");
            throw new NotAuthorizedException("Bearer");
        }

        // TODO: HARD-CODED EXAMPLE! get subject from auth call to d1_portal ///////////////////////
        String authSubject = "https://orcid.org/0000-1234-5678-999X";
        // TODO: END OF HARD-CODED EXAMPLE /////////////////////////////////////////////////////////

        if (isBlank(authSubject)) {
            log.info("Subject not authenticated - throwing NotAuthorizedException");
            throw new NotAuthorizedException("Bearer");
        }
        return authSubject;
    }

     // TODO: DO WE EVEN NEED THIS? OK to subscribe to something you don't have access to? Actual
     //       viewing will be blocked if user tries to follow link
    @Override
    public Set<String> authorize(String subject, ResourceType resourceType, List<String> pids)
        throws NotAuthorizedException {

        if (isBlank(subject)) {
            throw new NotAuthorizedException("Missing Subject");
        }
        if (pids == null || pids.isEmpty()) {
            throw new NotFoundException("Missing pid(s)");
        }
        log.debug("Authorizing subject: {} for resource: {} with pid(s): {}", subject, resourceType,
                  pids);

        // Automatically de-duplicates the list of PIDs
        Set<String> authPidSet = new HashSet<>(pids);

        // TODO: HARD-CODED EXAMPLE! Assume the subject has access to all requested resources. /////
        //
        // TODO: Ask metacat API if subject has access to requested resources. bulk API call avail?

        return authPidSet;
        // TODO: END OF HARD-CODED EXAMPLE /////////////////////////////////////////////////////////
    }
}
