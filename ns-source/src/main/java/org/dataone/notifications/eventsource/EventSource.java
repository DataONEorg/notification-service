package org.dataone.notifications.eventsource;

/**
 * A source that performs one poll per scheduled run. Implementations must have a public
 * no-argument constructor, propagate poll failures, and leave the shared SubscriptionProcessor
 * open for the runner to close.
 */
public interface EventSource {
    void poll() throws Exception;
}
