package org.dataone.notifications.api.messages;

import org.slf4j.LoggerFactory;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.dataone.notifications.api.resource.SubscriptionResourceType;
import org.dataone.notifications.storage.DataRepository;
import org.dataone.notifications.storage.Subscription;
import org.dataone.notifications.EmailUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


@RequestScoped
@Path("/eventhandler")
public class EventHandler {
    private final Logger log = LoggerFactory.getLogger(this.getClass().getName());
    private final DataRepository dataRepository;
    private EmailUtil emailUtil;

    public EventHandler() {
        throw new IllegalStateException(
            "EventHandler not initialized: missing DataRepository");
    }

    @Inject
    public EventHandler(DataRepository dataRepository){
        this.dataRepository = dataRepository;
        this.emailUtil = new EmailUtil();
    }

    @GET
    @Path("/send")
    @Produces(MediaType.APPLICATION_JSON)
    public Record send(
        @QueryParam("resource") String resource, 
        @QueryParam("pid") String pid
    ){
        log.debug("Received request to send event for resource type: {}",
            resource);
        SubscriptionResourceType resourceType = validateResourceType(resource);
        validatePid(pid);
        List<Subscription> subscriptions = 
            dataRepository.getSubscriptionsByPid(pid, resourceType);
        log.debug(
            "Sending event for resource type: {} and pid: {} to {} subscriptions", 
            resourceType, pid, subscriptions.size());
        for (Subscription subscription : subscriptions) {
            log.debug("Sending event to subscription: {}", subscription);
        }
        String response = emailUtil.sendMail();
        log.debug("Email send response: {}", response);
        return new PingResponse(
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
