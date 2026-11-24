package org.dataone.notifications.api.internal.event;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.PathParam;
import org.dataone.notifications.event.LastModificationTimes;
import org.dataone.notifications.resource.SubscriptionResourceType;
import org.dataone.notifications.storage.Subscription;
import org.slf4j.LoggerFactory;

import java.util.List;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.dataone.notifications.storage.DataRepository;

import org.slf4j.Logger;

/// A class that provides internal API endpoints for managing private resources. It enables various
/// components of the system to read or write database records, without needing individual components
/// to have direct access to the database.
///
/// IMPORTANT: The endpoints defined in this class are intended for internal use within the cluster
/// only, and should never be exposed to external clients.
///
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

    /// Gets the last modification time timestamp and nodeId pairs for a given resourceType.
    ///
    /// @param resourceType the type of resource for which to retrieve the runtimes & nodeIds - see
    ///                     {@link SubscriptionResourceType} enum for valid values
    /// @return a record containing the last modification time timestamp (integer milliseconds since
    /// the epoch (UTC)) and nodeId pairs for the specified resourceType
    ///
    @GET
    @Path("/lastModificationTime/{resourceType}")
    @Produces(MediaType.APPLICATION_JSON)
    public Record getLastModificationTime(
        @NotNull @PathParam("resourceType") SubscriptionResourceType resourceType
    ){
        log.debug("Request to get last successful source runtimes for resourceType: {}",
            resourceType);
        LastModificationTimes response = dataRepository.getLastModificationTimes(resourceType);
        if (response == null) {
            log.warn("No lastModificationTimes found for resourceType: {}", resourceType);
            throw new IllegalArgumentException(
                "No lastModificationTime found for resourceType: " + resourceType);
        }
        if (response.runtimes().isEmpty()) {
            log.warn("No lastModificationTimes found for resourceType: {}", resourceType);
            throw new NotFoundException(
                "No lastModificationTime found for resourceType: " + resourceType);
        }
        log.debug("Got lastModificationTime list for resourceType: {}. List: {}",
                  resourceType, response);
        return response;
    }

    /// Gets the list of subscriber subjects (user ids) for a given resourceType and pid.
    ///
    /// @param resourceType the type of resource - see {@link SubscriptionResourceType} enum
    /// @param pid the object id for which to retrieve subscriber subjects
    /// @return a List of subject (user id) Strings; empty if no subscriptions exist
    ///
    @GET
    @Path("/subscriptions/{resourceType}/{pid}")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getSubscriptionSubjects(
        @NotNull @PathParam("resourceType") SubscriptionResourceType resourceType,
        @NotNull @PathParam("pid") String pid
    ) {
        log.debug("Request to get subscriber subjects for resourceType: {}, pid: {}",
                  resourceType, pid);
        List<String> subjects = dataRepository.getSubscriptionsByPid(pid, resourceType)
            .stream()
            .map(Subscription::subject)
            .toList();
        log.debug("Got {} subscriber subjects for resourceType: {}, pid: {}",
                  subjects.size(), resourceType, pid);
        return subjects;
    }

    /// Updates all subscriptions for a given subject, replacing the old pid with the new pid.
    ///
    /// Example resulting request:
    /// ```json
    ///   PUT /private/subscriptions/urn:uuid:old-pid/urn:uuid:new-pid/http://orcid.org/0000-0001-2345-6789
    /// ```
    ///
    /// @param oldPid the current pid to be replaced
    /// @param newPid the new pid to set
    /// @param subject the subject (user id) whose subscriptions should be updated
    /// @return a record containing the number of subscriptions updated
    ///
    @PUT
    @Path("/subscriptions/{oldPid}/{newPid}/{subject}")
    @Produces(MediaType.APPLICATION_JSON)
    public Record updateSubscriptionPid(
        @NotNull @PathParam("oldPid") String oldPid,
        @NotNull @PathParam("newPid") String newPid,
        @NotNull @PathParam("subject") String subject
    ) {
        log.debug("Request to update subscription pid from {} to {} for subject {}",
                  oldPid, newPid, subject);
        if (oldPid == null || oldPid.isBlank()) {
            throw new BadRequestException("oldPid is required");
        }
        if (newPid == null || newPid.isBlank()) {
            throw new BadRequestException("newPid is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new BadRequestException("subject is required");
        }
        int updated = dataRepository.updateSubscriptionPid(oldPid, newPid, subject);
        log.debug("Updated {} subscription(s) from pid {} to {} for subject {}",
                  updated, oldPid, newPid, subject);
        return new UpdateResult(updated);
    }

    /// A record containing the result of an update operation.
    /// @param updated the number of records updated
    public record UpdateResult(int updated) {}

    /// Sets the last modification time timestamp and nodeId pairs for a given resourceType.
    /// ("upserts" the given entries; other nodeIds are unchanged)
    ///
    /// Example resulting request:
    /// ```json
    ///   PUT /private/lastModificationTime/datasetChanges
    ///   Content-Type: application/json
    ///   {"runtimes": [
    ///     {"timestamp": 1794338367000, "nodeId": "urn:node:KNB"},
    ///     {"timestamp": 1794338379000, "nodeId": "urn:node:ARCTIC"}
    ///   ]}
    /// ```
    ///
    /// @param runtimes LastModificationTimes object containing the last modification time
    ///                 timestamp (integer milliseconds since the epoch (UTC)) and nodeId pairs to
    ///                 set for the specified resourceType
    /// @param resourceType SubscriptionResourceType enum value representing the type of resource
    ///                     for which to set the runtimes & nodeIds - see
    ///                     {@link SubscriptionResourceType} enum for valid values
    ///
    @PUT
    @Path("/lastModificationTime/{resourceType}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public void setLastModificationTime(
        @NotNull LastModificationTimes runtimes,
        @NotNull @PathParam("resourceType") SubscriptionResourceType resourceType) {
        log.debug("Request to set lastModificationTimes for resourceType: {} to: {}",
                  resourceType, runtimes);
        if (runtimes == null) {
            throw new BadRequestException("Request body is required");
        }
        for (LastModificationTimes.Entry entry : runtimes.runtimes()) {
            if (entry.timestamp() == null || entry.nodeId() == null || entry.nodeId().isBlank()) {
                throw new BadRequestException(
                    "Each entry requires a timestamp and nodeId: " + entry);
            }
        }
        dataRepository.setLastModificationTimes(resourceType, runtimes);
        log.debug("Updated lastModificationTimes for resourceType: {} to: {}",
                  resourceType, runtimes);
    }
}
