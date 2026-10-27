package org.dataone.notifications.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/** Typed view of the JSON message body:
 * { "nsEvent": {
 *     "_version_":1879779856146759680,
 *     "id":"resource_map_urn:uuid:71f96fe5-ed91-4923-89aa-0ab5171d5a01",
 *     "identifier":"resource_map_urn:uuid:71f96fe5-ed91-4923-89aa-0ab5171d5a01",
 *     "sku":"resource_map_urn:uuid:71f96fe5-ed91-4923-89aa-0ab5171d5a01",
 *     "fileName":"resource_map_urn_uuid_8a592fd1_88f9_42fc_8a25_a0439af2a042.rdf.xml",
 *     "formatId":"http://www.openarchives.org/ore/terms", "formatType":"RESOURCE", "size":4388,
 *     "checksum":"4aaadbed267184a6d798b53aabaa39ee",
 *     "submitter":"http://orcid.org/0000-0002-1615-3963", "checksumAlgorithm":"MD5",
 *     "rightsHolder":"http://orcid.org/0000-0002-1615-3963", "replicationAllowed":false,
 *     "obsoletes":"resource_map_urn:uuid:f6b48ea3-b6a6-45a0-866f-25c36b283e79", "archived":false,
 *     "dateUploaded":"2026-10-22T19:36:07.717Z", "updateDate":"2026-10-22T19:36:07.717Z",
 *     "dateModified":"2026-10-22T19:36:07.858Z", "datasource":"urn:node:mnTestKNB",
 *     "authoritativeMN":"urn:node:mnTestKNB", "readPermission":["public",
 *     "CN=arctic-data-admins,DC=dataone,DC=org", "http://orcid.org/0000-0003-0391-541X"],
 *     "writePermission":["CN=arctic-data-admins,DC=dataone,DC=org",
 *     "http://orcid.org/0000-0003-0391-541X"],
 *     "changePermission":["CN=arctic-data-admins,DC=dataone,DC=org"], "isPublic":true,
 *     "dataUrl":"https://cn.dataone.org/cn/v2/resolve/resource_map_urn%3Auuid%3A71f96fe5-ed91-4923-89aa-0ab5171d5a01",
 *     "prov_instanceOfClass":["http://www.openarchives.org/ore/terms/ResourceMap"],
 *     "serviceCoupling":"false", "isService":false
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NsEventMessage(NsEvent nsEvent) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NsEvent(
            long _version_,
            String id,
            String identifier,
            String sku,
            String fileName,
            String formatId,
            String formatType,
            long size,
            String checksum,
            String checksumAlgorithm,
            String submitter,
            String rightsHolder,
            boolean replicationAllowed,
            String obsoletes,
            String obsoletedBy,
            boolean archived,
            Instant dateUploaded,
            Instant updateDate,
            Instant dateModified,
            String datasource,
            String authoritativeMN,
            List<String> readPermission,
            List<String> writePermission,
            List<String> changePermission,
            boolean isPublic,
            String dataUrl,
            List<String> resourceMap,
            @com.fasterxml.jackson.annotation.JsonProperty("prov_instanceOfClass")
            List<String> provInstanceOfClass,
            String serviceCoupling,
            boolean isService) {}
}
