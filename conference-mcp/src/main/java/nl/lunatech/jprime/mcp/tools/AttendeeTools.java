package nl.lunatech.jprime.mcp.tools;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import nl.lunatech.jprime.mcp.api.Dtos.BookmarkDto;
import nl.lunatech.jprime.mcp.api.Dtos.CreateBookmarkRequest;
import nl.lunatech.jprime.mcp.api.Dtos.CreateRatingRequest;
import nl.lunatech.jprime.mcp.api.Dtos.RatingDto;
import nl.lunatech.jprime.mcp.api.Dtos.SessionDto;
import nl.lunatech.jprime.mcp.api.MeConferenceApi;
import nl.lunatech.jprime.mcp.security.McpSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class AttendeeTools {

    @Inject
    @RestClient
    MeConferenceApi me;

    @Inject
    McpSecurity security;

    @Tool(name = "bookmark_session",
            description = "Add a session to my personal agenda. Recorded under MY identity and "
                    + "audited. Returns the new bookmark.")
    public BookmarkDto bookmarkSession(
            @ToolArg(name = "session_id", description = "Numeric session id", required = true) Long sessionId) {
        security.requireRole("attendee");
        return me.addBookmark(new CreateBookmarkRequest(sessionId));
    }

    @Tool(name = "unbookmark_session",
            description = "Remove a session from my personal agenda.")
    public Acknowledgement unbookmarkSession(
            @ToolArg(name = "session_id", description = "Numeric session id", required = true) Long sessionId) {
        security.requireRole("attendee");
        Response r = me.removeBookmark(sessionId);
        return new Acknowledgement(r.getStatus() < 300, "session " + sessionId + " removed from agenda");
    }

    @Tool(name = "my_agenda",
            description = "Show the sessions on my personal agenda, ordered by start time.")
    public List<BookmarkDto> myAgenda() {
        security.requireRole("attendee");
        return me.myAgenda();
    }

    @Tool(name = "my_conflicts",
            description = "Show me sessions I've bookmarked that overlap in time. "
                    + "Useful when planning the conference day.")
    public List<List<SessionDto>> myConflicts() {
        security.requireRole("attendee");
        return me.conflicts();
    }

    @Tool(name = "rate_session",
            description = "Submit a 1 to 5 star rating with an optional comment for a session I "
                    + "attended. The rating is recorded under MY identity and is auditable. "
                    + "The server refuses to rate a session that has not started yet.")
    public RatingDto rateSession(
            @ToolArg(name = "session_id", description = "Numeric session id", required = true) Long sessionId,
            @ToolArg(name = "stars", description = "Rating between 1 and 5", required = true) Integer stars,
            @ToolArg(name = "comment", description = "Optional free-text comment", required = false)
            String comment) {
        security.requireRole("attendee");
        if (stars == null || stars < 1 || stars > 5) {
            throw new ToolCallException("invalid_argument: stars must be between 1 and 5");
        }
        Response r = me.rateSession(sessionId, new CreateRatingRequest(stars, comment));
        if (r.getStatus() == 422) {
            throw new ToolCallException("rejected: " + r.readEntity(String.class));
        }
        return r.readEntity(RatingDto.class);
    }

    @Tool(name = "my_ratings",
            description = "List the ratings I have submitted.")
    public List<RatingDto> myRatings() {
        security.requireRole("attendee");
        return me.myRatings();
    }

    public record Acknowledgement(boolean ok, String message) {}
}
