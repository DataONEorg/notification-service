package org.dataone.notifications.api.resource;

import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DatasetResourceTest {

    public static final String SUBJECT = "https://orcid.org/0000-0002-1472-913X";
    public static final String RESOURCE_TYPE = "dataset";
    public static final String PID = "urn:mypid:12345-67890";
    DatasetResource resource = new DatasetResource();

    @Test
    void getSubscriptions() {
        // HAPPY PATH
        HttpResponseRecord result =
            (HttpResponseRecord) resource.getSubscriptions(RESOURCE_TYPE, SUBJECT);
        assertNotNull(result);
        assertEquals(SUBJECT, result.subject());
        assertEquals(RESOURCE_TYPE, result.resourceType());
        assertEquals(2, result.resources().length);
        assertTrue(result.resources()[0].contains("urn:uuid:0"));
        assertTrue(result.resources()[1].contains("urn:uuid:1"));
    }

    @Test
    void subscribe() {

        // HAPPY PATH
        HttpResponseRecord result =
            (HttpResponseRecord) resource.subscribe(RESOURCE_TYPE, PID, SUBJECT);
        assertNotNull(result);
        assertEquals(SUBJECT, result.subject());
        assertEquals(RESOURCE_TYPE, result.resourceType());
        assertEquals(1, result.resources().length);
        assertEquals(PID, result.resources()[0]);

        // EXPECTED ERRORS

        HttpErrorRecord errResult =
            (HttpErrorRecord) resource.subscribe("", PID, SUBJECT);
        assertNotNull(errResult);
        assertEquals(Status.BAD_REQUEST, errResult.httpErrorCode());
        assertTrue(errResult.errorMessage().contains("resource"));

        errResult =
            (HttpErrorRecord) resource.subscribe(RESOURCE_TYPE, "", SUBJECT);
        assertNotNull(errResult);
        assertEquals(Status.BAD_REQUEST, errResult.httpErrorCode());
        assertTrue(errResult.errorMessage().contains("pid"));

        errResult =
            (HttpErrorRecord) resource.subscribe(RESOURCE_TYPE, PID, "");
        assertNotNull(errResult);
        assertEquals(Status.BAD_REQUEST, errResult.httpErrorCode());
        assertTrue(errResult.errorMessage().contains("subject"));
    }
}
