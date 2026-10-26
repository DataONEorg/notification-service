package org.dataone.notifications.exception;

/**
 * Indicates that notification delivery failed, wrapping a provider-specific cause.
 */
public class NotificationDeliveryException extends Exception {
    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
