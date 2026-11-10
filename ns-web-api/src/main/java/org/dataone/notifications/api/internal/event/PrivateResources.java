package org.dataone.notifications.api.internal.event;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import org.dataone.notifications.event.LastSuccessfulRuntimes;
import java.util.List;
import org.dataone.notifications.resource.SubscriptionResourceType;
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

    /*
     * Gets the last successful runtime timestamp and nodeId pairs for a given resourceType.
     *
     * @param resourceType the type of resource for which to retrieve the runtimes & nodeIds
     * @return a record containing the last successful runtime timestamp and nodeId pairs for the
     *         specified resourceType
     */
    @GET
    @Path("/lastSuccessfulRuntime/{resourceType}")
    @Produces(MediaType.APPLICATION_JSON)
    public Record getLastRuntime(
        @NotNull @PathParam("resourceType") SubscriptionResourceType resourceType
    ){
        log.debug("Request to get last successful source runtimes for resourceType: {}",
            resourceType);
        LastSuccessfulRuntimes response = dataRepository.getLastSuccessfulRuntimes(resourceType);
        if (response == null) {
            log.warn("No lastSuccessfulRuntimes found for resourceType: {}", resourceType);
            throw new IllegalArgumentException(
                "No lastSuccessfulRuntime found for resourceType: " + resourceType);
        }
        if (response.entries().isEmpty()) {
            log.warn("No lastSuccessfulRuntimes found for resourceType: {}", resourceType);
            throw new NotFoundException(
                "No lastSuccessfulRuntime found for resourceType: " + resourceType);
        }
        log.debug("Got lastSuccessfulRuntime list for resourceType: {}. List: {}",
                  resourceType, response);
        return response;
    }

    @PUT
    @Path("/lastSuccessfulRuntime")
    @Produces(MediaType.APPLICATION_JSON)
    public Record setLastRuntime(
        @NotNull @FormParam("timestamp") Long timestampEpochMillis,
        @NotNull @FormParam("nodeId") String nodeId,
        @NotNull @FormParam("resourceType") SubscriptionResourceType resourceType) {
        log.debug(
            "Request to set lastSuccessfulRuntime to timestamp: {}; nodeId: {}, resourceType: {}",
            timestampEpochMillis, nodeId, resourceType);
        Instant timestampUtc = Instant.ofEpochMilli(timestampEpochMillis);
        validateTimestamp(timestampUtc);
        List<LastSuccessfulRuntimes.Entry> entries =
            List.of(new LastSuccessfulRuntimes.Entry(timestampUtc, nodeId));
        dataRepository.setLastSuccessfulRuntimes(resourceType, entries);
        log.debug(
            "Updated lastSuccessfulRuntime to timestamp: {}; nodeId: {}, resourceType: {}",
            timestampEpochMillis, nodeId, resourceType);
        return new LastSuccessfulRuntimes(entries);
    }

    private void validateTimestamp(Instant timestampUtc) {
        if (timestampUtc.isAfter(Instant.now().plusSeconds(60))      // allow for clock skew
            || timestampUtc.isBefore(Instant.now().minusSeconds(SEVEN_DAYS_IN_SECONDS))) {
            throw new BadRequestException("timestamp out of range: " + timestampUtc.toEpochMilli());
        }
    }

}
