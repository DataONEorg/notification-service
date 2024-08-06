package org.dataone.notifications.api.data;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dataone.notifications.api.resource.NsRecord;
import org.dataone.notifications.api.resource.ResourceType;

import java.util.ArrayList;
import java.util.List;

/**
 * A class that provides access to the data store for the notifications service.
 * <code>@ApplicationScoped</code> means this is a singleton bean.
 */
@ApplicationScoped
public class NsDataProvider implements DataProvider {

    private final Logger log = LogManager.getLogger(this.getClass().getName());

    public List<String> getSubscriptions(String subject, ResourceType resourceType) {

        log.debug("Get subscriptions to {} for {}", resourceType, subject);

//      // TODO: HARD-CODED EXAMPLE! get pids from database instead... /////////////////////////////
        List<String> pids = new ArrayList<>();
        pids.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50251");
        pids.add("urn:uuid:1add8838-861b-4afb-af00-7b2ecca585bf");
        pids.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50233");
        pids.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50255");
        // TODO: END OF HARD-CODED EXAMPLE /////////////////////////////////////////////////////////

        return pids;
    }

    public void addSubscription(String subject, ResourceType resourceType, String pid) {

        log.debug("Add new subscription to {}/{} for {}", resourceType, pid, subject);

//      // TODO: HARD-CODED EXAMPLE! save to database instead... ///////////////////////////////////
        // TODO: END OF HARD-CODED EXAMPLE /////////////////////////////////////////////////////////
    }

    public void removeSubscription(String subject, ResourceType resourceType, String pid) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void removeAllSubscriptions(String subject, ResourceType resourceType) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void removeAllSubscriptions(String subject) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
