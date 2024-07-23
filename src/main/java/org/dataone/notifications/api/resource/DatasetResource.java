package org.dataone.notifications.api.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response.Status;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.logging.log4j.util.Strings.isBlank;

@Path("/{resourceType}/{pid}")
public class DatasetResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatasetResource.class);

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record getSubscriptions(@PathParam("resourceType") String resourceType,
                                   @QueryParam("subject") String subject){

        LOGGER.debug("GET /{}", resourceType);

        if (isBlank(resourceType)) {
            return new HttpErrorRecord(Status.BAD_REQUEST,
                                       "resource type must be provided in request URI");
        }
        if (isBlank(subject)) {
            return new HttpErrorRecord(Status.BAD_REQUEST,
                                       "subject must be provided in request params");
        }

//      // TODO: HARD-CODED EXAMPLE! get from database instead
        HttpResponseRecord response = new HttpResponseRecord(
            "https://orcid.org/0000-0002-1472-913X","dataset",
            new String[]{ "urn:uuid:0e01a574-35cd-4316-a834-267f70f50251",
                "urn:uuid:1add8838-861b-4afb-af00-7b2ecca585bf" });

        return response;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record subscribe(
        @PathParam("resourceType") String resourceType, @PathParam("pid") String targetPid,
        @QueryParam("subject") String subject) {

        LOGGER.debug("POST /{}/{} with subject={}", resourceType, targetPid, subject);

        if (isBlank(resourceType)) {
            return new HttpErrorRecord(Status.BAD_REQUEST,
                                       "resource type must be provided in request URI");
        }
        if (isBlank(targetPid)) {
            return new HttpErrorRecord(Status.BAD_REQUEST,
                                       "pid must be provided in request URI");
        }
        if (isBlank(subject)) {
            return new HttpErrorRecord(Status.BAD_REQUEST,
                                       "subject must be provided in request body");
        }

//        // TODO: HARD-CODED EXAMPLE!
//        //       1. Check this subject has read access to the targetPid
//        //       2. subscribe in DB

        HttpResponseRecord response = new HttpResponseRecord(subject, resourceType,
                                                             new String[]{ targetPid });

        return response;
    }
}
