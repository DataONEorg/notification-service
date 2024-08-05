package org.dataone.notifications.api.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dataone.notifications.api.resource.ResourceType;

import java.util.ArrayList;
import java.util.List;

//class that queries the database for the requested data
public class DataAccess {
    private static DataAccess instance = null;
    private final Logger LOGGER = LogManager.getLogger(this.getClass().getName());

    private DataAccess() {}

    // constructor with double locking for singleton pattern
    public static DataAccess getInstance() {
        if (instance == null) {
            synchronized (DataAccess.class) {
                if (instance == null) {
                    instance = new DataAccess();
                }
            }
        }
        return instance;
    }

    public List<String> getSubscribedPids(String subject, ResourceType resourceType) {


//      // TODO: HARD-CODED EXAMPLE! get pids from database instead...
        List<String> pids = new ArrayList<>();
        pids.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50251");
        pids.add("urn:uuid:1add8838-861b-4afb-af00-7b2ecca585bf");
        pids.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50233");
        pids.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50255");

        return pids;
    }

    public void addSubscription(String subject, ResourceType resourceType, String pid) {

    }

    public void removeSubscription(String subject, ResourceType resourceType, String pid) {

    }

    public void removeAllSubscriptions(String subject, ResourceType resourceType) {

    }

    public void removeAllSubscriptions(String subject) {

    }
}
