package org.dataone.notifications.api.exception;

import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.postgresql.util.PSQLException;

import java.sql.SQLException;

@Provider
public class PersistenceExceptionMapper implements ExceptionMapper<PersistenceException> {

    @Override
    public Response toResponse(PersistenceException exception) {

        Throwable cause = exception.getCause();
        String message = "Unknown database error";
        if (cause == null) {
            message = exception.getMessage();
        } else {
            // find root cause
            while (cause != null) {
                if (cause.getCause() == null) {
                    message = cause.getMessage();
                    break;
                }
                cause = cause.getCause();
            }
        }
        // Escape special JSON characters
        String escapedMessage = message.replace("\\", "\\\\")  // Escape backslashes first
            .replace("\"", "\\\"")   // Escape double quotes
            .replace("\n", " ")      // remove newlines
            .replace("\r", " ")      // remove carriage returns
            .replace("\t", "  ");    // replace tabs

        return Response.status(Response.Status.BAD_REQUEST)
            .entity("{\"error\": \"Database problem: " + escapedMessage + "\"}")
            .type("application/json").build();
    }
}
