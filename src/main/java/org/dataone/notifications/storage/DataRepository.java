package org.dataone.notifications.storage;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.api.resource.ResourceType;

import java.util.List;

/**
 * Interface for CRUD operations on the data store.
 */
public interface DataRepository {
    List<String> getSubscriptions(String subject, ResourceType resourceType)
        throws NotAuthorizedException, NotFoundException;

    Subscription addSubscription(String subject, ResourceType resourceType, String pid);

    Subscription deleteSubscriptions(
        String subject, ResourceType resourceType, List<String> pidList);
}
