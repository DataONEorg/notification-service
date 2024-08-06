package org.dataone.notifications.api.resource;

import jakarta.inject.Inject;
import org.dataone.notifications.api.auth.AuthProvider;
import org.dataone.notifications.api.data.DataProvider;
import org.junit.jupiter.api.BeforeAll;

import java.util.ArrayList;
import java.util.List;

class DatasetResourceTest {

    private static final String SUBJECT = "https://orcid.org/0000-2222-4444-999X";
    private static final String PID = "urn:mypid:12345-67890";
    private static final List<String> PID_LIST = new ArrayList<>();
    private static final NsRecord EXPECTED_JSON =
        new NsRecord(SUBJECT, ResourceType.DATASETS, PID_LIST);
    Resource resource;

    @Inject
    public DatasetResourceTest(AuthProvider authProvider, DataProvider dataProvider) {
        resource = new Resource(authProvider, dataProvider);
    }

    @BeforeAll
    static void setUp() {
        PID_LIST.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50251");
        PID_LIST.add("urn:uuid:1add8838-861b-4afb-af00-7b2ecca585bf");
        PID_LIST.add("urn:uuid:0e01a574-35cd-4316-a834-267f70f50233");
        PID_LIST.add(PID);
    }

//    @Test
//    void getSubscriptions() {
//        // HAPPY PATH
//        NsRecord result =
//            (NsRecord) resource.getSubscriptions(ResourceType.DATASETS);
//        assertNotNull(result);
//        assertEquals(EXPECTED_JSON.subject(), result.subject());
//        assertEquals(ResourceType.DATASETS, result.resourceType());
//        assertEquals(4, result.resourceIds().size());
//        assertTrue(result.resourceIds().get(0).contains("urn:uuid:0"));
//        assertTrue(result.resourceIds().get(1).contains("urn:uuid:1"));
//    }

//    @Test
//    void subscribe() {
//
//        // HAPPY PATH
//        NsRecord result =
//            (NsRecord) resource.subscribe(ResourceType.DATASETS);
//        assertNotNull(result);
//        assertEquals(EXPECTED_JSON.subject(), result.subject());
//        assertEquals(RESOURCE_TYPE, result.resourceType());
//        assertEquals(1, result.resourceIds().length);
//        assertEquals(PID, result.resourceIds()[0]);
//
//        // EXPECTED ERRORS
//        NsRecord json =
//            new NsRecord("", null, new String[]{PID});
//        HttpErrorRecord errResult =
//            (HttpErrorRecord) resource.subscribe(null, json);
//        assertNotNull(errResult);
//        assertEquals(Status.BAD_REQUEST, errResult.httpErrorCode());
//        assertTrue(errResult.errorMessage().contains("resource"));
//
//        errResult =
//            (HttpErrorRecord) resource.subscribe(RESOURCE_TYPE, json);
//        assertNotNull(errResult);
//        assertEquals(Status.BAD_REQUEST, errResult.httpErrorCode());
//        assertTrue(errResult.errorMessage().contains("pid"));
//
//        errResult =
//            (HttpErrorRecord) resource.subscribe(RESOURCE_TYPE, json);
//        assertNotNull(errResult);
//        assertEquals(Status.BAD_REQUEST, errResult.httpErrorCode());
//        assertTrue(errResult.errorMessage().contains("subject"));
//    }
}
