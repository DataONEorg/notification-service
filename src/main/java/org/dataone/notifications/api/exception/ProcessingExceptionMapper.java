package org.dataone.notifications.api.exception;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ProcessingExceptionMapper implements ExceptionMapper<ProcessingException> {
    @Override
    public Response toResponse(ProcessingException exception) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .entity("{\"error\": \"Response processing error: " + exception.getMessage() + "\"}")
            .type("application/json")
            .build();
    }
}
