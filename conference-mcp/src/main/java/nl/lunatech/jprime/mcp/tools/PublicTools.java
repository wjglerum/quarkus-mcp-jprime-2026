package nl.lunatech.jprime.mcp.tools;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.jprime.mcp.api.PublicConferenceApi;
import nl.lunatech.jprime.mcp.dto.SessionDto;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PublicTools {

    @Inject
    @RestClient
    PublicConferenceApi api;

    @Inject
    SessionResolver sessions;

    @ConfigProperty(name = "demo.now")
    Optional<String> demoNow;

    @Tool(name = "list_sessions",
            description = "Search the jPrime 2026 conference schedule by topic, keyword, or speaker. "
                    + "Returns matching sessions sorted by start time. Pass `query` for free-text search "
                    + "over titles and abstracts, `speaker_name` to filter by speaker; both may combine.")
    public List<SessionDto> listSessions(
            @ToolArg(name = "query",
                    description = "Case-insensitive substring matched against titles and abstracts.",
                    required = false) String query,
            @ToolArg(name = "speaker_name",
                    description = "Speaker name (case-insensitive substring match).",
                    required = false) String speakerName) {
        return api.listSessions(null, speakerName, query);
    }

    @Tool(name = "get_session",
            description = "Get full details for one session: abstract, room, timing, cancellation "
                    + "state, and speaker. Pass session_query (a few words of the title) when you do "
                    + "not have the numeric id.")
    public SessionDto getSession(
            @ToolArg(name = "session_id", description = SessionResolver.SESSION_ID, required = false) Long sessionId,
            @ToolArg(name = "session_query", description = SessionResolver.SESSION_QUERY, required = false) String sessionQuery) {
        return api.getSession(sessions.resolve(sessionId, sessionQuery));
    }

    @Tool(name = "whats_on_now",
            description = "List the sessions happening right now at jPrime 2026. Use for `what is on "
                    + "now`, `what is happening`, `current slot`.")
    public List<SessionDto> whatsOnNow() {
        return api.currentSessions(demoNow.orElse(null));
    }

    @Tool(name = "whats_next",
            description = "List the next upcoming sessions after the current conference clock. Use "
                    + "for `what is next` or `what is coming up`. Returns three by default; cap at 20.")
    public List<SessionDto> whatsNext(
            @ToolArg(name = "limit",
                    description = "Maximum number of upcoming sessions to return. Defaults to 3.",
                    required = false, defaultValue = "3") Integer limit) {
        return api.nextSessions(demoNow.orElse(null), limit);
    }
}
