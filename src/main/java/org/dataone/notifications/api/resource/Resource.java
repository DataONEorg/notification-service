package org.dataone.notifications.api.resource;

import jakarta.inject.Inject;
import jakarta.security.auth.message.AuthException;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dataone.notifications.api.auth.AuthProvider;
import org.dataone.notifications.api.data.DataProvider;

import java.util.List;

import static org.apache.logging.log4j.util.Strings.isBlank;

/**
 * A class that provides CRUD operations for notification subscriptions for a given subject (user).
 */
@SuppressWarnings("UnnecessaryLocalVariable")
@Path("/{resource}")
public class Resource {

    private final Logger log = LogManager.getLogger(this.getClass().getName());
    private final AuthProvider authProvider;
    private final DataProvider dataProvider;

    @Inject
    public Resource(AuthProvider authProvider, DataProvider dataProvider) {
        log.debug("@Injected AuthService & DataProvider into Resource");
        this.authProvider = authProvider;
        this.dataProvider = dataProvider;
    }

    /**
     * GET pids of all existing notification subscriptions for this subject (user).
     * Example:
     * {@code
     * $ curl -X GET http://localhost:8080/notifications/datasets \
     *        -H "Authorization: Bearer $TOKEN" \
     *        -H "Content-Type: application/json"
     * }
     * @param resource the resource being queried (eg "datasets"). (Auto-populated)
     * @return Record containing name-value pairs that will be automatically converted to the type
     *     defined in <code>@Produces</code>
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record getSubscriptions(
        @HeaderParam("Authorization") String authHeader,
        @PathParam("resource") String resource)
        throws NotAuthorizedException {

        log.debug("GET /{}", resource);

        String subject = getSubject(authHeader);

        ResourceType resourceType = ResourceType.valueOf(resource.toUpperCase());

        List<String> pids = dataProvider.getSubscriptions(subject, resourceType);

        NsRecord response = new NsRecord(subject, resourceType, pids);

        return response;
    }

    /**
     * Subscribe the authenticated subject (user) to the given resource (identified by its pid).
     * Example:
     * $ curl -X POST "http://localhost:8080/notifications/datasets/urn:uuid:3f930da-c3ad325c10e9" \
     *        -H "Authorization: Bearer $TOKEN" \
     *        -H "Content-Type: application/json"
     *
     * @param resource the resource type (eg "datasets"). Automatically populated
     * @return Record containing name-value pairs that will be automatically converted to the type
     *              defined in <code>@Produces</code>
     */
    @POST
    @Path("/{pid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record subscribe(
        @HeaderParam("Authorization") String authHeader,
        @NotNull @PathParam("resource") String resource,
        @NotNull @PathParam("pid") String pid) throws NotAuthorizedException, AuthException {

        log.debug("POST /{}/{}", resource, pid);

        String subject = getSubject(authHeader);

        ResourceType resourceType = ResourceType.valueOf(resource.toUpperCase());

        authProvider.authorize(subject, pid);

        dataProvider.addSubscription(subject, resourceType, pid);

        NsRecord response = new NsRecord(subject, resourceType, List.of(pid));

        return response;
    }

    private String getSubject(String authHeader) throws NotAuthorizedException {

        String token;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            log.debug("No Auth token found - throwing NotAuthorizedException");
            throw new NotAuthorizedException("Bearer");
        }
        String subject = authProvider.authenticate(token);

        if (isBlank(subject)) {
            log.info("Subject not authenticated - throwing NotAuthorizedException");
            throw new NotAuthorizedException("Bearer");
        }
        return subject;
    }
}
