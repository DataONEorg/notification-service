package org.dataone.notifications.api.data;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.configuration2.YAMLConfiguration;
import org.dataone.notifications.NsConfig;
import org.dataone.notifications.api.ApiConfigV1;
import org.dataone.notifications.api.resource.ResourceType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NsDataProviderTest {

    public static final String EXPECTED_SUBJECT = "https://orcid.org/0000-1234-5678-999X";
    public static final String EXPECTED_PID = "pid1";
    private NsDataProvider dataProvider;
    private static final Subscription EXPECTED_RECORD =
        new Subscription(EXPECTED_SUBJECT, ResourceType.DATASETS, List.of(EXPECTED_PID));

    /* The embedded postgres database from Testcontainers, used in all tests */
    private static PostgreSQLContainer<?> pg;

    @BeforeAll
    static void oneTimeSetUp() {
        YAMLConfiguration nsConfig = NsConfig.getConfig();

        // Set up postgres TestContainer
        pg = new PostgreSQLContainer<>("postgres:" + nsConfig.getString("database.version"));
        pg.withExposedPorts(5432)
            .withDatabaseName(nsConfig.getString("database.name"))
            .withUsername(nsConfig.getString("database.username"))
            .withPassword(nsConfig.getString("database.password"));
        pg.start();

        //initialize with test data using FlyWay
        Flyway flyway =
            Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .cleanDisabled(false).load();
        flyway.migrate();
    }

    @BeforeEach
    void perTestSetUp() {
        dataProvider = new NsDataProvider(NsTestDataSource.getInstance(pg));
    }

    @AfterAll
    static void oneTimeTearDown() {
        if (pg != null) {
            pg.stop();
        }
    }

    @Test
    void getSubscriptionsValidSubject() {
        List<String> pids = dataProvider.getSubscriptions(EXPECTED_SUBJECT, ResourceType.DATASETS);
        assertNotNull(pids);
        assertFalse(pids.isEmpty());
        assertEquals(3, pids.size());
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
