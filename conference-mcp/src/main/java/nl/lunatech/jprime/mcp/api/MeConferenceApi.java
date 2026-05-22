package nl.lunatech.jprime.mcp.api;

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
import nl.lunatech.jprime.mcp.dto.BookmarkDto;
import nl.lunatech.jprime.mcp.dto.CancelSessionRequest;
import nl.lunatech.jprime.mcp.dto.CreateBookmarkRequest;
import nl.lunatech.jprime.mcp.dto.CreateRatingRequest;
import nl.lunatech.jprime.mcp.dto.RatingDto;
import nl.lunatech.jprime.mcp.dto.SessionDto;
import nl.lunatech.jprime.mcp.dto.SessionFeedbackDto;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "conference-api-me")
@AccessToken
@Path("/api/v1")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MeConferenceApi {

    @GET
    @Path("/me/agenda")
    List<BookmarkDto> myAgenda();

    @POST
    @Path("/me/agenda")
    BookmarkDto addBookmark(CreateBookmarkRequest req);

    @DELETE
    @Path("/me/agenda/{sessionId}")
    Response removeBookmark(@PathParam("sessionId") Long sessionId);

    @GET
    @Path("/me/conflicts")
    List<SessionDto> conflicts();

    @POST
    @Path("/sessions/{id}/ratings")
    Response rateSession(@PathParam("id") Long sessionId, CreateRatingRequest req);

    @GET
    @Path("/me/ratings")
    List<RatingDto> myRatings();

    @GET
    @Path("/me/sessions/feedback")
    List<SessionFeedbackDto> mySessionFeedback();

    @GET
    @Path("/sessions/{id}/attendees")
    Response sessionAttendees(@PathParam("id") Long sessionId);

    @POST
    @Path("/sessions/{id}/cancel")
    SessionDto cancelSession(@PathParam("id") Long sessionId, CancelSessionRequest req);
}
