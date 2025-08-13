package org.dataone.notifications.api.metrics;


import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Metrics and health checks for notification service
 */
@Path("/metrics")
public class Metrics {

    /**
     * Public simple ping route for K8s. Example:
     * <pre>
     * $ curl -X GET http://localhost:8080/notifications/metrics/ping
     * </pre>
     *
     * @return HTTP 200 OK response header, and { "status": "ok" } response body
     */
    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    public Record ping() {
        return new PingResponse("ok");
    }

    // Simple DTO implementing the same marker type used elsewhere
    public record PingResponse(String status) {}
}
