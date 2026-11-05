package org.dataone.notifications.api.internal.event;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.PUT;
import org.dataone.notifications.api.resource.SubscriptionResourceType;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.dataone.notifications.storage.DataRepository;

import org.slf4j.Logger;

import java.time.Instant;

/**
 * A class that provides internal API endpoints for managing private resources. It enables various
 * components of the system to read or write database records, without needing individual components
 * to have direct access to the database.
 *
 * IMPORTANT: The endpoints defined in this class are intended for internal use within the cluster
 * only, and should never be exposed to external clients.
 */
@RequestScoped
@Path("/private")
public class PrivateResources {
    private static final int SEVEN_DAYS_IN_SECONDS = 60 * 60 * 24 * 7;
    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private final DataRepository dataRepository;

    // Constructor for dependency injection. The no-arg constructor is required for CDI.
    public PrivateResources(){
        throw new IllegalStateException("PrivateResources must be constructed with a DataRepository");
    }

    @Inject
    public PrivateResources(DataRepository dataRepository){
        this.dataRepository = dataRepository;
    }

    @PUT
    @Path("/lastSuccessfulRuntime")
    @Produces(MediaType.APPLICATION_JSON)
    public Record setLastRuntime(
        @NotNull @FormParam("timestamp") Long timestampEpochMillis,
        @NotNull @FormParam("resourceType") SubscriptionResourceType resourceType,
        @NotNull @FormParam("nodeId") String nodeId
        ){
        log.debug(
            "Request to set lastSuccessfulRuntime to timestamp: {}; resourceType: {}, nodeId: {}",
            timestampEpochMillis, resourceType, nodeId);
        Instant timestampUtc = Instant.ofEpochMilli(timestampEpochMillis);
        validateTimestamp(timestampUtc);
        dataRepository.setLastSuccessfulRuntime(timestampUtc, resourceType, nodeId);
        log.debug("Updated lastSuccessfulRuntime to timestamp: {}; resourceType: {}, nodeId: {}",
                timestampEpochMillis, resourceType, nodeId);
        return new NotificationResult(timestampUtc, resourceType, nodeId);
    }

    private void validateTimestamp(Instant timestampUtc) {
        if (timestampUtc.isAfter(Instant.now().plusSeconds(60))      // allow for clock skew
            || timestampUtc.isBefore(Instant.now().minusSeconds(SEVEN_DAYS_IN_SECONDS))) {
            throw new BadRequestException("timestamp out of range: " + timestampUtc.toEpochMilli());
        }
    }

    public record NotificationResult(Instant timestampEpochMillis,
                                     SubscriptionResourceType resourceType, String nodeId) {
    }
}
