package org.dataone.notifications.api.resource;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/datasets")
public class DatasetResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetResource.class);

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public HttpGetResponse datasets(){
        LOGGER.debug("GET /datasets");

        HttpGetResponse response = new HttpGetResponse(
            "https://orcid.org/0000-0002-1472-913X","brooke@nceas.ucsb.edu", "datasets",
            new String[]{ "urn:uuid:0e01a574-35cd-4316-a834-267f70f50251",
                "urn:uuid:7add8838-861b-4afb-af00-7b2ecca585bf" });

        return response;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public HttpGetResponse subscribe(){
        LOGGER.debug("POST /datasets");

// TODO: USE FOR REFERENCE THEN DELETE
//        String subject = request.getParameter("subject");
//        if (isBlank(subject)) {
//            return errorResponse(request, httpMethod, subject, "no subject defined in request");
//        }
//
//        boolean success = true;
//        String targetPid = request.getParameter("pid");
//        LOGGER.debug("SubscriptionAction called with subject: {} and pid: {}", subject, targetPid);
//
//        if (isBlank(targetPid)) {
//            success = false;
//        }
//
//        // TODO: 1. Check this subject has read access to the targetPid
//        //       2. subscribe in DB
//
//        LOGGER.debug("success = {}; returning...", success);
//        return (success) ? RESULT_SUBSCRIBED : RESULT_UNSUBSCRIBED;

        HttpGetResponse response = new HttpGetResponse("https://orcid.org/0000-0002-1472-913X",
            "brooke@nceas.ucsb.edu", "datasets",
            new String[]{"urn:uuid:b541a0c8-aef8-4ac4-9c5c-29b6d3e1cd2b" });

        return response;
    }
}
