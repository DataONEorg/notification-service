package org.dataone.notifications.api.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.auth.message.AuthException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static org.apache.logging.log4j.util.Strings.isBlank;

/**
 * An implementation of the AuthProvider interface for the notification service.
 * <code>@ApplicationScoped</code> means this is a singleton bean.
 */
@SuppressWarnings("UnnecessaryLocalVariable")
@ApplicationScoped
public class NsAuthProvider implements AuthProvider {

    private final Logger log = LogManager.getLogger(this.getClass().getName());

    private NsAuthProvider() {}

    @Override
    public String authenticate(String token) {

        log.debug("Authenticating token: {}[redacted]{}", token.substring(0, 4),
                  token.substring(token.length() - 5));

        // TODO: HARD-CODED EXAMPLE! get subject from auth call to d1_portal
        String authedSubject = "https://orcid.org/0000-2222-4444-999X";

        return authedSubject;
    }

    @Override
    public void authorize(String subject, String pid) throws AuthException {

        log.debug("Authorizing subject: {} for pid: {}", subject, pid);

        if (isBlank(subject) || isBlank(pid)) {
            throw new AuthException("Missing data. Subject: " + subject + " pid: " + pid);
        }
        // TODO: HARD-CODED EXAMPLE! get subject from auth call to d1_portal
        if (!subject.equals("https://orcid.org/0000-2222-4444-999X")) {
            throw new AuthException("Unauthorized user: " + subject);
        }
    }
}
