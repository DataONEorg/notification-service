package org.dataone.notifications.messaging.consumer;

import org.dataone.notifications.messaging.SubscriptionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;

/**
 * Placeholder processor invoked after a RabbitMQ message is consumed.
 */
public class SubscriptionMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(
        SubscriptionMessageProcessor.class);

    public void process(SubscriptionEvent event) {
        log.info("Processing subscription event: resourceType={}, pid={}",
            event.resourceType(), event.pid());
        
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(
                    "http://ns-messages:8081/notifications/v1/eventhandler/send?resource=" +
                event.resourceType() + "&pid=" + 
                event.pid()))
                .header("Accept", "application/json")
                .GET() // Default method, included for clarity
                .build();
        try {
            HttpResponse<String> response = client.send(request, 
                HttpResponse.BodyHandlers.ofString());

            System.out.println("Status Code: " + response.statusCode());
            System.out.println("Response Body: " + response.body());
            
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
