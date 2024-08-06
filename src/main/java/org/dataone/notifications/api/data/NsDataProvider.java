package org.dataone.notifications.api.data;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dataone.notifications.api.resource.NsRecord;
import org.dataone.notifications.api.resource.ResourceType;
import org.dataone.notifications.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * A class that provides access to the data store for the notifications service.
 * <code>@ApplicationScoped</code> means this is a singleton bean.
 */
@ApplicationScoped
public class NsDataProvider implements DataProvider {

    private final Logger log = LogManager.getLogger(this.getClass().getName());

    public List<String> getSubscriptions(String subject, ResourceType resourceType)
        throws NotAuthorizedException, NotFoundException {

        log.debug("Get subscriptions to {} for {}", resourceType, subject);

        validateInput(subject, resourceType);

        // TODO: HARD-CODED EXAMPLE! ///////////////////////////////////////////////////////////////
        // TODO: get pids from database
        List<String> pids = new ArrayList<>();
        pids.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50251");
        pids.add("urn:uuid:1add8838-861b-4afb-af00-7b2ecca585bf");
        pids.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50233");
        pids.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50255");
        // TODO: END OF HARD-CODED EXAMPLE /////////////////////////////////////////////////////////

        return pids;
    }

    public NsRecord addSubscription(String subject, ResourceType resourceType, String pid) {

        log.debug("Add new subscription to {}/{} for {}", resourceType, pid, subject);

        validateInput(subject, resourceType, pid);

//      // TODO: HARD-CODED EXAMPLE! save to database instead... ///////////////////////////////////
        NsRecord result = new NsRecord(subject, resourceType, List.of(pid));
        // TODO: END OF HARD-CODED EXAMPLE /////////////////////////////////////////////////////////

        return result;
    }

    private void validateInput(String subject, ResourceType resourceType) {
        if (StringUtils.isBlank(subject)) {
            log.error("Subject is null or empty");
            throw new NotAuthorizedException("Subject is null or empty");
        }
        if (resourceType == null) {
            log.error("ResourceType is null");
            throw new NotFoundException("ResourceType is null");
        }
    }

    private void validateInput(String subject, ResourceType resourceType, String pid) {

        validateInput(subject, resourceType);

        if (StringUtils.isBlank(pid)) {
            log.error("PID is null or empty");
            throw new NotFoundException("PID is null or empty");
        }
    }
}
