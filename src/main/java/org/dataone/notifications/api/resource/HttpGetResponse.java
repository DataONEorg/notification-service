package org.dataone.notifications.api.resource;

public record HttpGetResponse(String subject, String email, String resourceType,
                              String[] resources) {
}
