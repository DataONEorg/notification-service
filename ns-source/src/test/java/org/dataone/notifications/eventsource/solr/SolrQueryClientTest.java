package org.dataone.notifications.eventsource.solr;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

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
        entry = Map.entry("wt", "json");
        SolrQueryClient.validateParamEnty(entry);
        entry = Map.entry("q", "id:*");
        SolrQueryClient.validateParamEnty(entry);
        final Map.Entry<String, String> entry1 = Map.entry("qt", "/update");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            SolrQueryClient.validateParamEnty(entry1);
        });
        final Map.Entry<String, String> entry2 = Map.entry("%71%74", "/update");
        IllegalArgumentException exception2 = assertThrows(IllegalArgumentException.class, () -> {
            SolrQueryClient.validateParamEnty(entry2);
        });
        final Map.Entry<String, String> entry3 = Map.entry("df", "id");
        IllegalArgumentException exception3 = assertThrows(IllegalArgumentException.class, () -> {
            SolrQueryClient.validateParamEnty(entry3);
        });
    }
}
