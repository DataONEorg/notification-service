package org.dataone.notifications.api.resource;

import jakarta.ws.rs.core.Response;

/**
 * A record containing the name-value pairs to be returned to the client (e.g. as json),
 * in the event of an error.
 * As stated by JEP 395, Records are meant to be "transparent carriers for immutable data". Default
 * constructor, getters, hashCode/equals and toString() will be generated by the compiler. See
 * <a href="https://openjdk.org/jeps/395">https://openjdk.org/jeps/395</a>
 *
 * @param httpErrorCode
 * @param errorMessage
 */
public record HttpErrorRecord(
    Enum<Response.Status> httpErrorCode,
    String errorMessage) {}
