package nl.lunatech.jprime.mcp.tools;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

    @Inject
    SessionResolver sessions;

    @Tool(name = "bookmark_session",
            description = "Add a session to the attendee's personal agenda, recorded under their "
                    + "identity and audited. Use when the user wants to attend, save, or pin a talk. "
                    + "Pass session_query directly when you do not have the id; do not look it up first.")
    public BookmarkDto bookmarkSession(
            @ToolArg(name = "session_id", description = SessionResolver.SESSION_ID, required = false) Long sessionId,
            @ToolArg(name = "session_query", description = SessionResolver.SESSION_QUERY, required = false) String sessionQuery) {
        return me.addBookmark(new CreateBookmarkRequest(sessions.resolve(sessionId, sessionQuery)));
    }

    @Tool(name = "unbookmark_session",
            description = "Remove a session from the attendee's agenda. Idempotent. Use when the user "
                    + "wants to drop, unsave, or remove a talk. Pass session_query when you lack the id.")
    public Acknowledgement unbookmarkSession(
            @ToolArg(name = "session_id", description = SessionResolver.SESSION_ID, required = false) Long sessionId,
            @ToolArg(name = "session_query", description = SessionResolver.SESSION_QUERY, required = false) String sessionQuery) {
        long id = sessions.resolve(sessionId, sessionQuery);
        me.removeBookmark(id);
        return new Acknowledgement(true, "session " + id + " removed from agenda");
    }

    @Tool(name = "my_agenda",
            description = "List the attendee's bookmarked sessions, ordered by start time. Use for "
                    + "their agenda, schedule, plan, or bookmarks.")
    public List<BookmarkDto> myAgenda() {
        return me.myAgenda();
    }

    @Tool(name = "my_conflicts",
            description = "List bookmarked sessions that overlap in time. Use for conflicts, clashes, "
                    + "or double-bookings.")
    public List<SessionDto> myConflicts() {
        return me.conflicts();
    }

    @Tool(name = "rate_session",
            description = "Submit a 1 to 5 star rating with an optional comment, recorded under the "
                    + "user's identity and audited. Pass session_query directly to rate by name; do "
                    + "not look up the id first. The server rejects rating a session that has not "
                    + "started yet with `rejected: session_not_started`.")
    public RatingDto rateSession(
            @ToolArg(name = "session_id", description = SessionResolver.SESSION_ID, required = false) Long sessionId,
            @ToolArg(name = "session_query", description = SessionResolver.SESSION_QUERY, required = false) String sessionQuery,
            @ToolArg(name = "stars",
                    description = "Star rating between 1 and 5 inclusive.",
                    required = true) Integer stars,
            @ToolArg(name = "comment",
                    description = "Optional free-text comment recorded with the rating.",
                    required = false) String comment) {
        if (stars == null || stars < 1 || stars > 5) {
            throw new ToolCallException("invalid_argument: stars must be between 1 and 5");
        }
        return me.rateSession(sessions.resolve(sessionId, sessionQuery), new CreateRatingRequest(stars, comment));
    }

    @Tool(name = "my_ratings",
            description = "List the ratings the attendee has submitted, with session title, stars, "
                    + "and comment. Use when the user asks what they have rated.")
    public List<RatingDto> myRatings() {
        return me.myRatings();
    }
}
