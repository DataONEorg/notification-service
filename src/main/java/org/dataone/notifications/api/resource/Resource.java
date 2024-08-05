package org.dataone.notifications.api.resource;

import jakarta.security.auth.message.AuthException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response.Status;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dataone.notifications.api.data.DataAccess;

import java.util.List;

import static org.apache.logging.log4j.util.Strings.isBlank;

@Path("/{resource}")
public class Resource {

    private final Logger LOGGER = LogManager.getLogger(this.getClass().getName());

    /**
     * GET pids of all existing notification subscriptions for this subject (user). Example:
     * $ curl -X GET -H "Authorization: Bearer $TOKEN" http://localhost:8080/notifications/datasets
     *
     * @param resource the resource being queried (eg "datasets"). Automatically populated
     * @return Record containing name-value pairs that will be automatically converted to the type
     *              defined in <code>@Produces</code>
     */
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Record getSubscriptions(@PathParam("resource") String resource) {

        LOGGER.debug("GET /{}", resource);
        String subject;
        try {
            subject = getSubject();
        } catch (AuthException e) {
            return new HttpErrorRecord(Status.UNAUTHORIZED, "Not authorized");
        }

        ResourceType resourceType = ResourceType.valueOf(resource.toUpperCase());

        List<String> pids = DataAccess.getInstance().getSubscribedPids(subject, resourceType);

        NsRecord response =  new NsRecord(subject, resourceType, pids);

        return response;
    }

//    @GET
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Produces(MediaType.APPLICATION_JSON)
//    public Record getSubscriptions(@PathParam("resource") ResourceType resource) {
//
//        LOGGER.debug("GET /{}", resource);
//        String subject;
//        try {
//            subject = getSubject();
//        } catch (AuthException e) {
//            return new HttpErrorRecord(Status.UNAUTHORIZED, "Not authorized");
//        }
//
////      // TODO: HARD-CODED EXAMPLE! get from database instead
//        NsRecord response =
//            new NsRecord(subject, resource, getSubscribedPids(subject, resource));
//
//        return response;
//    }

//    /**
//     * GET pids of all existing notification subscriptions for this subject (user). Example:
//     * $ curl -X GET -H "Authorization: Bearer $TOKEN" http://localhost:8080/notifications/datasets
//     *
//     * @param resource the resource type (eg "datasets"). Automatically populated
//     * @param body the NsRecord request body. Automatically populated by converting from the
//     *            type defined in <code>@Consumes</code>
//     * @return Record containing name-value pairs that will be automatically converted to the type
//     *              defined in <code>@Produces</code>
//     */
//    @POST
//    @Path("/{pid}")
//    @Consumes(MediaType.APPLICATION_JSON)
////    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
//    @Produces(MediaType.APPLICATION_JSON)
//    public Record subscribe(
//        @NotNull @PathParam("resource") ResourceType resource,
//        @NotNull @PathParam("pid") String pid,
//        @NotNull NsRecord body) {
//
//        List<String> targetPids;
//
//        if (body != null) {
//            targetPids = body.resourceIds();
//        } else {
//            targetPids = new ArrayList<>(1);
//        }
//        if (!isBlank(pid)) {
//            targetPids.add(pid);
//        }
//        if (targetPids.isEmpty()) {
//            return new HttpErrorRecord(Status.BAD_REQUEST,
//                                       "At least one pid must be provided");
//        }
//
//
//        LOGGER.debug("POST /{}/{} with subject={}", resource.toString(), targetPids, subject);
//
//
//
//        String subject;
//        try {
//            subject = getSubject();
//        } catch (AuthException e) {
//            return new HttpErrorRecord(Status.UNAUTHORIZED, "Not authorized");
//        }
//
////        // TODO: HARD-CODED EXAMPLE!
////        //       1. Check this subject has read access to the targetPid
////        //       2. subscribe in DB
//        NsRecord response = new NsRecord(subject, resource.toString(),
//                                                 new String[]{ targetPid });
//
//        return response;
//    }

    private String getSubject() throws AuthException {

//      // TODO: HARD-CODED EXAMPLE! get subject from authenticated jwt token...
        String subject = "https://orcid.org/0000-2222-4444-999X";

        if (isBlank(subject)) {
            LOGGER.info("Subject not authorized - throwing AuthException");
            throw new AuthException("Subject not authorized") ;
        }
        return subject;
    }
}
