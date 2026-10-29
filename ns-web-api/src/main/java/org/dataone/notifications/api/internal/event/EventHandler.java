package org.dataone.notifications.api.internal.event;

import org.dataone.notifications.api.resource.SubscriptionResourceType;
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
import org.dataone.notifications.api.resource.SubscriptionResource;
import org.dataone.notifications.storage.DataRepository;
import org.dataone.notifications.storage.Subscription;

import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

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
    public EventHandler(DataRepository dataRepository, NotificationListener emailListener){
        this.dataRepository = dataRepository;
        this.emailListener = emailListener;
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
        List<String> successList = new java.util.ArrayList<>();
        Map<String, String> failureMap = new java.util.HashMap<>();
        for (Subscription subscription : subscriptions) {
            String subscriber = subscription.subject();
            log.debug("Sending notification to subscriber ORCID: {}", subscriber);
            try {
                // If we decide to notify via other channels besides email (eg SMS? etc.), we can
                // add code here to handle different notification types:
                String response =
                    emailListener.handleNotification(subscriber, resourceType.toString(), pid);
                log.debug("Email sent successfully to subscriber: {}", response);
                successList.add(response);
            } catch (Exception e) {
                log.error("Failed to send email to: {}. Error: {}", subscriber,e.getMessage());
                failureMap.put(subscriber, e.getMessage());
            }
        }
        return new NotificationResult(successList, failureMap);
    }

    /**
     * A record representing the result of a notification handling operation.
     *
     * @param successful   A list of subscribers for whom the notification was successfully sent.
     * @param unsuccessful A map of subscribers to error messages for whom the notification failed.
     */
    public record NotificationResult(List<String> successful, Map<String, String> unsuccessful) {
    }

    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    public Record ping() {
        log.debug("Received ping request");
        return new PingResponse("ok");
    }

    public record PingResponse(String status) {}

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
