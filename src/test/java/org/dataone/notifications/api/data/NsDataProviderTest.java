package org.dataone.notifications.api.data;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.dataone.notifications.api.resource.NsRecord;
import org.dataone.notifications.api.resource.ResourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NsDataProviderTest {

    public static final String EXPECTED_SUBJECT = "https://orcid.org/0000-2222-4444-999X";
    public static final String EXPECTED_PID = "pid1";
    private NsDataProvider dataProvider;
    private static final NsRecord EXPECTED_RECORD =
        new NsRecord(EXPECTED_SUBJECT, ResourceType.DATASETS, List.of(EXPECTED_PID));

    @BeforeEach
    void setUp() {
        dataProvider = new NsDataProvider();
        // TODO: mock the database connection and inject into the dataProvider
    }

    @Test
    void getSubscriptionsValidSubject() {
        List<String> pids = dataProvider.getSubscriptions(EXPECTED_SUBJECT, ResourceType.DATASETS);
        assertNotNull(pids);
        assertFalse(pids.isEmpty());
    }

    @Test
    void getSubscriptionsInvalidSubject() {
        String subject = "";
        assertThrows(
            NotAuthorizedException.class,
            () -> dataProvider.getSubscriptions(subject, ResourceType.DATASETS));
    }

    @Test
    void getSubscriptionsInvalidResourceType() {
        assertThrows(
            NotFoundException.class,
            () -> dataProvider.getSubscriptions(EXPECTED_SUBJECT, null));
    }

    @Test
    void addSubscriptionValidData() {
        assertEquals(
            EXPECTED_RECORD,
            dataProvider.addSubscription(EXPECTED_SUBJECT, ResourceType.DATASETS, EXPECTED_PID));
    }

    @Test
    void addSubscriptionInvalidSubject() {
        String blankSubject = "";
        assertThrows(NotAuthorizedException.class,
                     () -> dataProvider.addSubscription(blankSubject, ResourceType.DATASETS,
                                                        EXPECTED_PID));
    }

    @Test
    void addSubscriptionInvalidData() {
        String blankPid = "";
        assertThrows(NotFoundException.class,
                     () -> dataProvider.addSubscription(EXPECTED_SUBJECT, ResourceType.DATASETS,
                                                        blankPid));
    }
}
