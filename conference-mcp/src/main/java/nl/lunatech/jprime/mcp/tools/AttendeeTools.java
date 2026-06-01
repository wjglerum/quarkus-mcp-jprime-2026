package nl.lunatech.jprime.mcp.tools;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import nl.lunatech.jprime.mcp.api.MeConferenceApi;
import nl.lunatech.jprime.mcp.dto.Acknowledgement;
import nl.lunatech.jprime.mcp.dto.BookmarkDto;
import nl.lunatech.jprime.mcp.dto.CreateBookmarkRequest;
import nl.lunatech.jprime.mcp.dto.CreateRatingRequest;
import nl.lunatech.jprime.mcp.dto.RatingDto;
import nl.lunatech.jprime.mcp.dto.SessionDto;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
@RolesAllowed("attendee")
public class AttendeeTools {

    @Inject
    @RestClient
    MeConferenceApi me;

    @Tool(name = "bookmark_session",
            description = "Add a session to the authenticated attendee's personal agenda. The "
                    + "bookmark is recorded under the user's identity and audited. Use this when "
                    + "the user says they want to attend, save, or pin a talk. Returns the new "
                    + "bookmark with the embedded session details.")
    public BookmarkDto bookmarkSession(
            @ToolArg(name = "session_id",
                    description = "Numeric session id to bookmark.",
                    required = true) Long sessionId) {
        return me.addBookmark(new CreateBookmarkRequest(sessionId));
    }

    @Tool(name = "unbookmark_session",
            description = "Remove a session from the authenticated attendee's personal agenda. "
                    + "Idempotent: returns success even if the bookmark was already gone. Use this "
                    + "when the user wants to drop, unsave, or remove a talk from their schedule.")
    public Acknowledgement unbookmarkSession(
            @ToolArg(name = "session_id",
                    description = "Numeric session id to remove from the agenda.",
                    required = true) Long sessionId) {
        try (Response r = me.removeBookmark(sessionId)) {
            return new Acknowledgement(r.getStatus() < 300,
                    "session " + sessionId + " removed from agenda");
        }
    }

    @Tool(name = "my_agenda",
            description = "List the sessions currently on the authenticated attendee's personal "
                    + "agenda, ordered by start time. Use this when the user asks for their "
                    + "agenda, schedule, plan, or bookmarks for the conference.")
    public List<BookmarkDto> myAgenda() {
        return me.myAgenda();
    }

    @Tool(name = "my_conflicts",
            description = "List bookmarked sessions that overlap in time. Use this when the user "
                    + "asks whether their agenda has any conflicts, clashes, or double-bookings.")
    public List<SessionDto> myConflicts() {
        return me.conflicts();
    }

    @Tool(name = "rate_session",
            description = "Submit a 1 to 5 star rating with an optional comment for a session the "
                    + "attendee attended. Recorded under the user's identity and fully audited. "
                    + "Stars must be between 1 and 5 (inclusive). The server refuses to rate a "
                    + "session that has not started yet; this surfaces as a "
                    + "`rejected: session_not_started` error.")
    public RatingDto rateSession(
            @ToolArg(name = "session_id",
                    description = "Numeric session id to rate.",
                    required = true) Long sessionId,
            @ToolArg(name = "stars",
                    description = "Star rating between 1 and 5 inclusive.",
                    required = true) Integer stars,
            @ToolArg(name = "comment",
                    description = "Optional free-text comment recorded with the rating.",
                    required = false) String comment) {
        if (stars == null || stars < 1 || stars > 5) {
            throw new ToolCallException("invalid_argument: stars must be between 1 and 5");
        }
        try (Response r = me.rateSession(sessionId, new CreateRatingRequest(stars, comment))) {
            if (r.getStatus() == 422) {
                String body = r.readEntity(String.class);
                throw new ToolCallException("rejected: "
                        + (body != null && body.contains("session_not_started")
                                ? "session_not_started"
                                : body));
            }
            if (r.getStatus() >= 400) {
                throw new ToolCallException("backend_error: " + r.readEntity(String.class));
            }
            return r.readEntity(RatingDto.class);
        }
    }

    @Tool(name = "my_ratings",
            description = "List ratings the authenticated attendee has submitted, with the related "
                    + "session title, stars, and comment. Use this when the user asks what they "
                    + "have rated.")
    public List<RatingDto> myRatings() {
        return me.myRatings();
    }
}
