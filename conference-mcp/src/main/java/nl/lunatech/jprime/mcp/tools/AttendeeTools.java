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
            description = "Add a session to the authenticated attendee's personal agenda. The "
                    + "bookmark is recorded under the user's identity and audited. Use this when "
                    + "the user says they want to attend, save, or pin a talk. Identify the talk "
                    + "with session_query (a few words of the title) when you do not already have "
                    + "the numeric id; do not call another tool first to look it up. Returns the "
                    + "new bookmark with the embedded session details.")
    public BookmarkDto bookmarkSession(
            @ToolArg(name = "session_id",
                    description = "Numeric session id. Provide this if you already know it.",
                    required = false) Long sessionId,
            @ToolArg(name = "session_query",
                    description = "A few distinctive words from the talk title (e.g. 'MCP Security'), "
                            + "used to find the session when the numeric id is unknown.",
                    required = false) String sessionQuery) {
        return me.addBookmark(new CreateBookmarkRequest(sessions.resolve(sessionId, sessionQuery)));
    }

    @Tool(name = "unbookmark_session",
            description = "Remove a session from the authenticated attendee's personal agenda. "
                    + "Idempotent: returns success even if the bookmark was already gone. Use this "
                    + "when the user wants to drop, unsave, or remove a talk from their schedule. "
                    + "Identify the talk with session_query when you do not have the numeric id.")
    public Acknowledgement unbookmarkSession(
            @ToolArg(name = "session_id",
                    description = "Numeric session id. Provide this if you already know it.",
                    required = false) Long sessionId,
            @ToolArg(name = "session_query",
                    description = "A few distinctive words from the talk title, used when the "
                            + "numeric id is unknown.",
                    required = false) String sessionQuery) {
        long id = sessions.resolve(sessionId, sessionQuery);
        me.removeBookmark(id);
        return new Acknowledgement(true, "session " + id + " removed from agenda");
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
                    + "To rate a talk by name, pass session_query with a few words of its title "
                    + "(e.g. 'MCP Security') directly in this call; you do NOT need to look up the "
                    + "id first. Stars must be between 1 and 5 (inclusive). The server refuses to "
                    + "rate a session that has not started yet; this surfaces as a "
                    + "`rejected: session_not_started` error.")
    public RatingDto rateSession(
            @ToolArg(name = "session_id",
                    description = "Numeric session id. Provide this if you already know it.",
                    required = false) Long sessionId,
            @ToolArg(name = "session_query",
                    description = "A few distinctive words from the talk title (e.g. 'MCP Security'), "
                            + "used to find the session when the numeric id is unknown.",
                    required = false) String sessionQuery,
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
            description = "List ratings the authenticated attendee has submitted, with the related "
                    + "session title, stars, and comment. Use this when the user asks what they "
                    + "have rated.")
    public List<RatingDto> myRatings() {
        return me.myRatings();
    }
}
