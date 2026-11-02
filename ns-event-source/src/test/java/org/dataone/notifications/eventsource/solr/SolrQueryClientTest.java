package org.dataone.notifications.eventsource.solr;

import org.junit.jupiter.api.Test;
import java.util.Map;

/**
 * A junit test for the SolrQueryClient class
 */
public class SolrQueryClientTest {

    /**
     * Test the validateParamsEntry method
     * @throws Exception
     */
    @Test
    public void testValidateParamEntry() throws Exception {
        Map.Entry<String, String> entry = null;
        SolrQueryClient.validateParamEnty(entry);
    }
}
