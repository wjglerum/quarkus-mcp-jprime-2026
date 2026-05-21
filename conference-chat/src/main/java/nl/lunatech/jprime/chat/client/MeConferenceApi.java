package nl.lunatech.jprime.chat.client;

import io.quarkus.oidc.token.propagation.common.AccessToken;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "conference-api-me")
@AccessToken
@Path("/api/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MeConferenceApi {

    @GET
    @Path("/me")
    Dtos.SessionDto me();

    @GET
    @Path("/me/agenda")
    List<Dtos.BookmarkDto> myAgenda();

    @POST
    @Path("/me/agenda")
    Dtos.BookmarkDto addBookmark(Dtos.CreateBookmarkRequest req);

    @DELETE
    @Path("/me/agenda/{sessionId}")
    Response removeBookmark(@PathParam("sessionId") Long sessionId);

    @GET
    @Path("/me/conflicts")
    List<List<Dtos.SessionDto>> conflicts();

    @POST
    @Path("/sessions/{id}/ratings")
    Response rateSession(@PathParam("id") Long sessionId, Dtos.CreateRatingRequest req);

    @GET
    @Path("/me/ratings")
    List<Dtos.RatingDto> myRatings();

    @GET
    @Path("/me/sessions/feedback")
    List<Dtos.SessionFeedbackDto> mySessionFeedback();

    @GET
    @Path("/sessions/{id}/attendees")
    Response sessionAttendees(@PathParam("id") Long sessionId);

    @POST
    @Path("/sessions/{id}/cancel")
    Dtos.SessionDto cancelSession(@PathParam("id") Long sessionId, Dtos.CancelSessionRequest req);
}
