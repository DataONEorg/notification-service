package org.dataone.notifications.storage;

import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.event.LastSuccessfulRuntimes;
import org.dataone.notifications.resource.SubscriptionResourceType;

import java.util.List;

/**
 * Interface for CRUD operations on the data store.
 */
public interface DataRepository {

    /**
     * Retrieves the list of PIDs that a given subject is subscribed to for a given resourceType.
     *
     * @param subject      the subject (user ORCID) for which to retrieve subscriptions
     * @param resourceType the type of resource for which to retrieve subscriptions
     * @return a List of PIDs that the subject is subscribed to for the given resourceType. This
     *         method will never return null; if no subscriptions are found, an empty List will be
     *         returned.
     * @throws NotAuthorizedException if the subject is not authorized to access the data store
     * @throws NotFoundException       if the subject is not found in the data store
     * @throws PersistenceException    if there is an error accessing the data store
     */
    List<String> getSubscriptions(String subject, SubscriptionResourceType resourceType)
        throws NotAuthorizedException, NotFoundException, PersistenceException;

    Subscription addSubscription(String subject, SubscriptionResourceType resourceType, String pid)
        throws NotAuthorizedException, PersistenceException;

    Subscription deleteSubscriptions(
        String subject, SubscriptionResourceType resourceType, List<String> pidList)
        throws NotAuthorizedException, NotFoundException, PersistenceException;

    List<String> getResourceTypesByPid(String subject, String pid)
        throws NotAuthorizedException, NotFoundException, PersistenceException;

    List<Subscription> getSubscriptionsByPid(String pid, SubscriptionResourceType type)
        throws NotAuthorizedException, NotFoundException, PersistenceException;

    /**
     * Retrieves the last successful runtime timestamps and nodeIds for a given resourceType.
     *
     * @param resourceType the type of resource for which to retrieve the runtimes & nodeIds
     * @return a LastSuccessfulRuntimes record for the specified resourceType, containing 0..n
     *     LastSuccessfulRuntimes.Entry records, each containing a timestamp and nodeId. This method
     *     will never return null; if no entries are found, a NotFoundException will be thrown.
     * @throws NotFoundException if no last successful runtimes are found for the given
     *                           resourceType
     */
    LastSuccessfulRuntimes getLastSuccessfulRuntimes(SubscriptionResourceType resourceType)
        throws NotFoundException;

    /**
     * for a given resourceType, sets 0..n pairs of last successful runtime timestamps and nodeIds
     * from the provided List of LastSuccessfulRuntimes.Entry records. If an entry with the same
     * nodeId already exists for the given resourceType, it will be updated with the new timestamp;
     * otherwise, a new entry will be created. If the provided List is empty, no action is taken. If
     * the provided List is null, an IllegalArgumentException will be thrown. Entries are never
     * deleted by this method.
     *
     * @param resourceType the type of resource for which to set the runtimes & nodeIds
     * @param entries      a List of LastSuccessfulRuntimes.Entry records, each containing a
     *                     timestamp and nodeId to be set for the given resourceType.
     * @throws IllegalArgumentException if the provided List is null
     * @throws PersistenceException     if there is an error persisting the entries to the data
     *                                  store
     */
    void setLastSuccessfulRuntimes(
        SubscriptionResourceType resourceType, LastSuccessfulRuntimes entries)
        throws IllegalArgumentException, PersistenceException;
}
