package org.dataone.notifications.api.resource;

import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dataone.notifications.api.auth.AuthProvider;
import org.dataone.notifications.api.data.DataProvider;
import org.dataone.notifications.api.data.Subscription;

import java.util.List;

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
     * Subscribe the authenticated subject (user) to the given resource (identified by its pid).
     * Example:
     * <pre>
     * $ curl -X POST "http://localhost:8080/notifications/datasets/urn:uuid:3f930da-c3ac10e9" \
     * -H "Authorization: Bearer $TOKEN" \
     * -H "Content-Type: application/json"
     * </pre>
     *
     * @param resource the resource type (eg "datasets"). Automatically populated
     * @return Record containing name-value pairs that will be automatically converted to the type
     *     defined in <code>@Produces</code>
     */
    @POST
    @Path("/{pid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record subscribe(
        @HeaderParam("Authorization") String authHeader,
        @NotNull @PathParam("resource") String resource, @NotNull @PathParam("pid") String pid)
        throws NotAuthorizedException, NotFoundException {

        log.debug("POST /{}/{}", resource, pid);

        if (pid == null) {
            log.error("Missing pid");
            throw new NotFoundException("Missing pid");
        }
        String subject = authProvider.authenticate(authHeader);
        ResourceType resourceType = getResourceType(resource);
        authProvider.authorize(subject, resourceType, List.of(pid));

        Subscription response = dataProvider.addSubscription(subject, resourceType, pid);

        return response;
    }

    /**
     * GET pids of all existing notification subscriptions for this subject (user). Example:
     * <pre>
     * $ curl -X GET http://localhost:8080/notifications/datasets \
     * -H "Authorization: Bearer $TOKEN" \
     * -H "Content-Type: application/json"
     * </pre>
     *
     * @param resource the resource being queried (eg "datasets"). (Auto-populated)
     * @return Record containing name-value pairs that will be automatically converted to the type
     *     defined in <code>@Produces</code>
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record getSubscriptions(
        @HeaderParam("Authorization") String authHeader, @PathParam("resource") String resource)
        throws NotAuthorizedException {

        log.debug("GET /{}", resource);

        String subject = authProvider.authenticate(authHeader);
        ResourceType resourceType = getResourceType(resource);

        List<String> pids = dataProvider.getSubscriptions(subject, resourceType);
        // TODO: do we need to verify that subject still has access to all subscribed resources?

        Subscription response = new Subscription(subject, resourceType, pids);

        return response;
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
     * @param resource the resource type (eg "datasets"). Automatically populated
     * @return Record containing name-value pairs that will be automatically converted to the type
     *     defined in <code>@Produces</code>
     */
    @DELETE
    @Path("/{pid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record unsubscribe(
        @HeaderParam("Authorization") String authHeader,
        @NotNull @PathParam("resource") String resource, @NotNull @PathParam("pid") String pid)
        throws NotAuthorizedException, NotFoundException {

        log.debug("DELETE /{}/{}", resource, pid);

        if (pid == null) {
            log.error("Missing pid");
            throw new NotFoundException("Missing pid");
        }
        String subject = authProvider.authenticate(authHeader);
        ResourceType resourceType = getResourceType(resource);
        authProvider.authorize(subject, resourceType, List.of(pid));

        Subscription response =
            dataProvider.deleteSubscriptions(subject, resourceType, List.of(pid));

        return response;
    }


    private ResourceType getResourceType(String requestedResource) {

        if (requestedResource == null) {
            log.error("Missing resource type");
            throw new NotFoundException("Missing resource type");
        }

        try {
            return ResourceType.valueOf(requestedResource);
        } catch (IllegalArgumentException e) {
            log.error("Invalid resource type: {}", requestedResource);
            throw new NotFoundException("Invalid resource type: " + requestedResource);
        }
    }
}
