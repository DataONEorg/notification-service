package org.dataone.notifications.api.metrics;

import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsApiTest extends JerseyTest {

    @Override
    protected Application configure() {
        return new ResourceConfig().register(Metrics.class);
    }

    @Test
    void ping_returns200AndJson() {
        Response response = target("/metrics/ping").request().get();

        assertEquals(200, response.getStatus());
        assertEquals(MediaType.APPLICATION_JSON, response.getHeaderString(HttpHeaders.CONTENT_TYPE));

        String body = response.readEntity(String.class);
        assertTrue(body.contains("\"status\""));
        assertTrue(body.contains("\"ok\""));
    }
}
