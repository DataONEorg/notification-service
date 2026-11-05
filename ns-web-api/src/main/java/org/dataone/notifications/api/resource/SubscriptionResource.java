package org.dataone.notifications.api.resource;


import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.slf4j.Logger;
import org.dataone.notifications.api.auth.AuthProvider;
import org.dataone.notifications.storage.DataRepository;
import org.dataone.notifications.storage.Subscription;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A class that provides CRUD operations for notification subscriptions for a given subject (user).
 */
@Default
@RequestScoped
@Path("/{resource}")
public class SubscriptionResource {
    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private final AuthProvider authProvider;
    private final DataRepository dataRepository;

    public SubscriptionResource() {
        throw new IllegalStateException(
            "SubscriptionResource not initialized: missing AuthService & DataRepository");
    }

    @Inject
    public SubscriptionResource(AuthProvider authProvider, DataRepository dataRepository) {
        log.debug("@Injected AuthService & DataRepository into SubscriptionResource");
        this.authProvider = authProvider;
        this.dataRepository = dataRepository;
    }

    /**
     * GET pids of all existing notification subscriptions for this subject (user). Example:
     * <pre>
     * $ curl -X GET http://localhost:8080/notifications/datasetChanges \
     * -H "Authorization: Bearer $TOKEN" \
     * -H "Content-Type: application/json"
     * </pre>
     *
     * @param resource the resource being queried (eg "datasetChanges"). (Auto-populated)
     * @return Record containing name-value pairs that will be automatically converted to the type
     *     defined in {@code @Produces}
     */
    @Operation(summary = "Get subscribed PIDs by resource type",
        description = "For the given resource type, return all PIDs to which the subject is "
            + "subscribed.")
    @Parameter(name = "Authorization", description = "Bearer token (e.g. 'Bearer <token>')",
        required = true, in = ParameterIn.HEADER, schema = @Schema(implementation = String.class))
    @Parameter(name = "resource", description = "Resource type", required = true,
        in = ParameterIn.PATH, schema = @Schema(implementation = SubscriptionResourceType.class))
    @APIResponse(responseCode = "200", description = "A list of PIDs.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = Subscription.class)))
    @APIResponse(responseCode = "400", description = "Unknown SubscriptionResourceType.")
    @APIResponse(responseCode = "401",
        description = "Authorization information is missing or invalid.")
    //
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record getSubscriptions(
        @HeaderParam("Authorization") String authHeader,
        @PathParam("resource") String resource)
        throws NotAuthorizedException {

        log.debug("GET /{}", resource);

        SubscriptionResourceType resourceType = validateResourceType(resource);
        String subject = authProvider.authenticate(authHeader);

        List<String> pids = dataRepository.getSubscriptions(subject, resourceType);
        // TODO: do we need to verify that subject still has access to all subscribed resources?

        return new Subscription(subject, resourceType, pids);
    }

    /**
     * Subscribe the authenticated subject (user) to the given resource (identified by its pid).
     * Example:
     * <pre>
     * $ curl -X POST "http://localhost:8080/notifications/datasetChanges/urn:uuid:3f930da-c3ac10e9" \
     * -H "Authorization: Bearer $TOKEN" \
     * -H "Content-Type: application/json"
     * </pre>
     *
     * @param resource the resource type (eg "datasetChanges"). Automatically populated
     * @return Record containing name-value pairs that will be automatically converted to the type
     *     defined in {@code @Produces}
     */
    @Operation(summary = "Subscribe to resource type by PID",
        description = "Subscribe the subject to the given resource type, for the given PID.")
    @Parameter(name = "Authorization", description = "Bearer token (e.g. 'Bearer <token>')",
        required = true, in = ParameterIn.HEADER, schema = @Schema(implementation = String.class))
    @Parameter(name = "resource", description = "Resource type", required = true,
        in = ParameterIn.PATH, schema = @Schema(implementation = SubscriptionResourceType.class))
    @Parameter(name = "pid", description = "PID", required = true, in = ParameterIn.PATH,
        schema = @Schema(implementation = String.class))
    @APIResponse(responseCode = "200", description = "Subscription record.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = Subscription.class)))
    @APIResponse(responseCode = "400", description = "Unknown SubscriptionResourceType.")
    @APIResponse(responseCode = "401",
        description = "Authorization information is missing or invalid.")
    //
    @POST
    @Path("/{pid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record subscribe(
        @HeaderParam("Authorization") String authHeader,
        @PathParam("resource") String resource,
        @NotNull @PathParam("pid") String pid)
        throws NotAuthorizedException, NotFoundException {

        log.debug("POST /{}/{}", resource, pid);

        validatePid(pid);
        SubscriptionResourceType resourceType = validateResourceType(resource);
        String subject = authProvider.authenticate(authHeader);
        authProvider.authorize(subject, resourceType, List.of(pid));
        return dataRepository.addSubscription(subject, resourceType, pid);
    }

    /**
     * Unsubscribe the authenticated subject (user) from the given resource (identified by its pid).
     * Example:
     * <pre>
     * $ curl -X DELETE "http://localhost:8080/notifications/datasetChanges/urn:uuid:3f930da-c3ad3e9" \
     * -H "Authorization: Bearer $TOKEN" \
     * -H "Content-Type: application/json"
     * </pre>
     *
     * @param resource the resource type (eg "datasetChanges"). Automatically populated
     * @return Record containing name-value pairs that will be automatically converted to the type
     *     defined in {@code @Produces}
     */
    @Operation(summary = "Unsubscribe from resource type by PID",
        description = "Unsubscribe the subject from the given resource type, for the given PID.")
    @Parameter(name = "Authorization", description = "Bearer token (e.g. 'Bearer <token>')",
        required = true, in = ParameterIn.HEADER, schema = @Schema(implementation = String.class))
    @Parameter(name = "resource", description = "Resource type", required = true,
        in = ParameterIn.PATH, schema = @Schema(implementation = SubscriptionResourceType.class))
    @Parameter(name = "pid", description = "PID", required = true, in = ParameterIn.PATH,
        schema = @Schema(implementation = String.class))
    @APIResponse(responseCode = "200", description = "Removed subscriptions.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = Subscription.class)))
    @APIResponse(responseCode = "400", description = "Unknown SubscriptionResourceType.")
    @APIResponse(responseCode = "401",
        description = "Authorization information is missing or invalid.")
    //
    @DELETE
    @Path("/{pid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record unsubscribe(
        @HeaderParam("Authorization") String authHeader,
        @PathParam("resource") @NotNull String resource,
        @NotNull @PathParam("pid") String pid)
        throws NotAuthorizedException, NotFoundException {

        log.debug("DELETE /{}/{}", resource, pid);

        validatePid(pid);
        SubscriptionResourceType resourceType = validateResourceType(resource);
        String subject = authProvider.authenticate(authHeader);
        authProvider.authorize(subject, resourceType, List.of(pid));
        return dataRepository.deleteSubscriptions(subject, resourceType, List.of(pid));
    }


    private SubscriptionResourceType validateResourceType(String resource) {
        if (resource == null) {
            log.error("Missing resource type");
            throw new NotFoundException("Missing resource type");
        }
        SubscriptionResourceType resourceType;
        try {
            resourceType = SubscriptionResourceType.fromString(resource);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        return resourceType;
    }

    private void validatePid(String pid) {
        if (pid == null) {
            log.error("Missing pid");
            throw new NotFoundException("Missing pid");
        }
    }
}
