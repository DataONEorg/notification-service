package org.dataone.notifications.api.internal.event;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import org.dataone.notifications.event.LastSuccessfulRuntimes;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.dataone.notifications.storage.DataRepository;

import org.slf4j.Logger;

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
        if (response.runtimes().isEmpty()) {
            log.warn("No lastSuccessfulRuntimes found for resourceType: {}", resourceType);
            throw new NotFoundException(
                "No lastSuccessfulRuntime found for resourceType: " + resourceType);
        }
        log.debug("Got lastSuccessfulRuntime list for resourceType: {}. List: {}",
                  resourceType, response);
        return response;
    }

    /// Sets the last successful runtime timestamp and nodeId pairs for a given resourceType.
    /// ("upserts" the given entries; other nodeIds are unchanged)
    ///
    /// Example resulting request:
    /// ```json
    ///   PUT /private/lastSuccessfulRuntime/datasetChanges
    ///   Content-Type: application/json
    ///   {"runtimes": [
    ///     {"timestamp": 1794338367000, "nodeId": "urn:node:KNB"},
    ///     {"timestamp": 1794338379000, "nodeId": "urn:node:ARCTIC"}
    ///   ]}
    /// ```
    ///
    /// @param runtimes LastSuccessfulRuntimes object containing the last successful runtime
    ///                 timestamp and nodeId pairs to set for the specified resourceType
    /// @param resourceType SubscriptionResourceType enum value representing the type of resource
    ///                     for which to set the runtimes & nodeIds
    ///
    @PUT
    @Path("/lastSuccessfulRuntime/{resourceType}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public void setLastRuntime(
        @NotNull LastSuccessfulRuntimes runtimes,
        @NotNull @PathParam("resourceType") SubscriptionResourceType resourceType) {
        log.debug("Request to set lastSuccessfulRuntimes for resourceType: {} to: {}",
                  resourceType, runtimes);
        if (runtimes == null) {
            throw new BadRequestException("Request body is required");
        }
        for (LastSuccessfulRuntimes.Entry entry : runtimes.runtimes()) {
            if (entry.timestamp() == null || entry.nodeId() == null || entry.nodeId().isBlank()) {
                throw new BadRequestException(
                    "Each entry requires a timestamp and nodeId: " + entry);
            }
            validateTimestamp(entry.timestamp());
        }
        dataRepository.setLastSuccessfulRuntimes(resourceType, runtimes);
        log.debug("Updated lastSuccessfulRuntimes for resourceType: {} to: {}",
                  resourceType, runtimes);
    }

    private void validateTimestamp(long timestampMillis) {
        long nowMillis = System.currentTimeMillis();
        if (timestampMillis > nowMillis + 60_000                      // allow for clock skew
            || timestampMillis < nowMillis - SEVEN_DAYS_IN_SECONDS * 1000L) {
            throw new BadRequestException("timestamp out of range: " + timestampMillis);
        }
    }

}
