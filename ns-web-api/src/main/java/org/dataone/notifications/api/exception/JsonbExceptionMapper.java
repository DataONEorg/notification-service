package org.dataone.notifications.api.exception;

import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps request bodies that cannot be deserialized (malformed JSON, wrong field types) to 400
 * rather than letting them surface as a 500.
 */
@Provider
public class JsonbExceptionMapper implements ExceptionMapper<JsonbException> {

    private static final Logger log = LoggerFactory.getLogger(JsonbExceptionMapper.class);

    @Override
    public Response toResponse(JsonbException exception) {
        log.warn("Unable to deserialize request body", exception);
        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\": \"Malformed request body\"}")
            .type(MediaType.APPLICATION_JSON)
            .build();
    }
}
