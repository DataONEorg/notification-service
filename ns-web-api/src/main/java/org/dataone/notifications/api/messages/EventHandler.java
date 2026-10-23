package org.dataone.notifications.api.messages;

import org.slf4j.LoggerFactory;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.dataone.notifications.NotificationListener;
import org.dataone.notifications.api.resource.SubscriptionResourceType;
import org.dataone.notifications.api.resource.SubscriptionResource;
import org.dataone.notifications.storage.DataRepository;
import org.dataone.notifications.storage.Subscription;
import org.dataone.notifications.EmailNotifier;

import org.slf4j.Logger;

import java.util.List;

@RequestScoped
@Path("/private/eventhandler")
public class EventHandler {
    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private final DataRepository dataRepository;
    private final NotificationListener emailListener;

    // Constructor for dependency injection. The no-arg constructor is required for CDI.
    @Deprecated
    public EventHandler(){
        throw new IllegalStateException("EventHandler must be constructed with a DataRepository");
    }

    @Inject
    public EventHandler(DataRepository dataRepository){
        this.dataRepository = dataRepository;
        this.emailListener = new EmailNotifier();
    }

    @POST
    @Path("/send")
    @Produces(MediaType.APPLICATION_JSON)
    public Record send(
        @QueryParam("resource") String resource,
        @QueryParam("pid") String pid
    ){
        log.debug("Got request to process event for pid: {} of resource type: {}", pid, resource);
        SubscriptionResourceType resourceType = validateResourceType(resource);
        validatePid(pid);
        List<Subscription> subscriptions =
            dataRepository.getSubscriptionsByPid(pid, resourceType);
        log.debug(
            "Sending notification to {} subscribers, for pid: {} of resource type: {}",
            subscriptions.size(), pid, resourceType);
        for (Subscription subscription : subscriptions) {
            String subscriber = subscription.subject();
            log.debug("Sending notification to subscriber ORCID: {}", subscriber);
            // If we decide to notify via other channels besides email (eg SMS? etc.), we can add
            // code here to handle different notification types
            String response = emailListener.handleNotification(subscriber, resourceType.toString(), pid);
            log.debug("Notification response: {}", response);
        }

        return new SendResponse(
            "Received event for resource type: "
             + resourceType + " and pid: " + pid + ". Sent to "
             + subscriptions.size() + " subscriptions.");
    }

    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    public Record ping() {
        log.debug("Received ping request");
        return new PingResponse("pong");
    }
    // Simple DTO implementing the same marker type used elsewhere
    public record PingResponse(String status) {}

    // Simple DTO for the send request
    public record SendResponse(String message) {}

    private SubscriptionResourceType validateResourceType(String resource) {
        return SubscriptionResource.getSubscriptionResourceType(resource, log);
    }



    private void validatePid(String pid) {
        if (pid == null) {
            log.error("Missing pid");
            throw new NotFoundException("Missing pid");
        }
    }
}
