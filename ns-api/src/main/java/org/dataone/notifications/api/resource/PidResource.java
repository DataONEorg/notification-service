package org.dataone.notifications.api.resource;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.dataone.notifications.api.auth.AuthProvider;
import org.dataone.notifications.storage.DataRepository;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * A JAX-RS resource for querying subscriptions.
 */
//
@RequestScoped
@Path("/pid")
public class PidResource {
    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private final AuthProvider authProvider;
    private final DataRepository dataRepository;

    /**
     * Default constructor for CDI.
     */
    public PidResource() {
        throw new IllegalStateException(
            "PidResource not initialized: missing AuthService & DataRepository");
    }

    @Inject
    public PidResource(AuthProvider authProvider, DataRepository dataRepository) {
        log.debug("@Injected AuthService & DataRepository into PidResource");
        this.authProvider = authProvider;
        this.dataRepository = dataRepository;
    }

    /**
     * Get the list of resource types (e.g., 'datasetChanges' etc.) that have active subscriptions
     * for a given PID. This requires the user to have read access to the PID. Example:
     * <pre>
     * $ curl -X GET "http://localhost:8080/notifications/v1/pid/urn:uuid:3f930da-c3ac10e9" \
     * -H "Authorization: Bearer $TOKEN" \
     * -H "Content-Type: application/json"
     * </pre>
     *
     * @param authHeader The authorization header ("Authorization: Bearer $TOKEN").
     * @param pid        The persistent identifier (PID) of the resource to query.
     * @return Record containing name-value pairs that will be automatically converted to the type
     *     defined in {@code @Produces}
     */
    @Operation(summary = "Get subscribed resource types by PID",
        description = "For the given PID, return all resource types to which the subject is "
            + "subscribed.")
    @Parameter(name = "Authorization", description = "Bearer token (e.g. 'Bearer <token>')",
        required = true, in = ParameterIn.HEADER, schema = @Schema(implementation = String.class))
    @Parameter(name = "pid", description = "PID", required = true, in = ParameterIn.PATH,
        schema = @Schema(implementation = String.class))
    @APIResponse(responseCode = "200", description = "A list of resource type names.",
        content = @Content(mediaType = MediaType.APPLICATION_JSON,
            schema = @Schema(implementation = List.class)))
    @APIResponse(responseCode = "401",
        description = "Authorization information is missing or invalid.")
    @APIResponse(responseCode = "404", description = "PID is missing, empty, or not found.")
    //
    @GET
    @Path("{pid}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getResourceTypesByPid(
        @HeaderParam("Authorization") String authHeader,
        @NotNull @PathParam("pid") String pid)
        throws NotAuthorizedException, NotFoundException {

        log.debug("GET /pid/{}", pid);

        validatePid(pid);
        String subject = authProvider.authenticate(authHeader);

        return dataRepository.getResourceTypesByPid(subject, pid);
    }

    private void validatePid(String pid) {
        if (pid == null || pid.isBlank()) {
            log.error("Missing or empty pid");
            throw new NotFoundException("Missing or empty pid");
        }
    }
}
