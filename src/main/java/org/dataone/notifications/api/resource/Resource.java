package org.dataone.notifications.api.resource;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dataone.notifications.api.auth.AuthProvider;
import org.dataone.notifications.api.data.DataProvider;
import org.dataone.notifications.api.data.Subscription;

import java.util.List;

/**
 * A class that provides CRUD operations for notification subscriptions for a given subject (user).
 */
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
     * Subscribe the authenticated subject (user) to the given resource (identified by its pid).
     * Example:
     * <pre>
     * $ curl -X POST "http://localhost:8080/notifications/datasets/urn:uuid:3f930da-c3ac10e9" \
     * -H "Authorization: Bearer $TOKEN" \
     * -H "Content-Type: application/json"
     * </pre>
     *
     * @param resourceType the resource type (eg "datasets"). Automatically populated
     * @return Record containing name-value pairs that will be automatically converted to the type
     *     defined in {@code @Produces}
     */
    @POST
    @Path("/{pid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record subscribe(
        @HeaderParam("Authorization") String authHeader,
        @PathParam("resource") ResourceType resourceType,
        @NotNull @PathParam("pid") String pid)
        throws NotAuthorizedException, NotFoundException {

        log.debug("POST /{}/{}", resourceType, pid);

        validatePid(pid);
        validateResourceType(resourceType);
        String subject = authProvider.authenticate(authHeader);
        authProvider.authorize(subject, resourceType, List.of(pid));
        return dataProvider.addSubscription(subject, resourceType, pid);
    }

    /**
     * GET pids of all existing notification subscriptions for this subject (user). Example:
     * <pre>
     * $ curl -X GET http://localhost:8080/notifications/datasets \
     * -H "Authorization: Bearer $TOKEN" \
     * -H "Content-Type: application/json"
     * </pre>
     *
     * @param resourceType the resource being queried (eg "datasets"). (Auto-populated)
     * @return Record containing name-value pairs that will be automatically converted to the type
     *     defined in {@code @Produces}
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record getSubscriptions(
        @HeaderParam("Authorization") String authHeader,
        @PathParam("resource") ResourceType resourceType)
        throws NotAuthorizedException {

        log.debug("GET /{}", resourceType);

        validateResourceType(resourceType);
        String subject = authProvider.authenticate(authHeader);

        List<String> pids = dataProvider.getSubscriptions(subject, resourceType);
        // TODO: do we need to verify that subject still has access to all subscribed resources?

        return new Subscription(subject, resourceType, pids);
    }

    /**
     * Unsubscribe the authenticated subject (user) from the given resource (identified by its pid).
     * Example:
     * <pre>
     * $ curl -X DELETE "http://localhost:8080/notifications/datasets/urn:uuid:3f930da-c3ad3e9" \
     * -H "Authorization: Bearer $TOKEN" \
     * -H "Content-Type: application/json"
     * </pre>
     *
     * @param resourceType the resource type (eg "datasets"). Automatically populated
     * @return Record containing name-value pairs that will be automatically converted to the type
     *     defined in {@code @Produces}
     */
    @DELETE
    @Path("/{pid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record unsubscribe(
        @HeaderParam("Authorization") String authHeader,
        @NotNull @PathParam("resource") ResourceType resourceType,
        @NotNull @PathParam("pid") String pid)
        throws NotAuthorizedException, NotFoundException {

        log.debug("DELETE /{}/{}", resourceType, pid);

        validatePid(pid);
        validateResourceType(resourceType);
        String subject = authProvider.authenticate(authHeader);
        authProvider.authorize(subject, resourceType, List.of(pid));
        return dataProvider.deleteSubscriptions(subject, resourceType, List.of(pid));
    }


    private void validateResourceType(ResourceType resourceType) {
        if (resourceType == null) {
            log.error("Missing resource type");
            throw new NotFoundException("Missing resource type");
        }
    }

    private void validatePid(String pid) {
        if (pid == null) {
            log.error("Missing pid");
            throw new NotFoundException("Missing pid");
        }
    }
}
