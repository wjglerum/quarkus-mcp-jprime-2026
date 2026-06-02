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

    @ConfigProperty(name = "demo.now")
    Optional<String> demoNow;

    @Tool(name = "list_sessions",
            description = "Search the jPrime 2026 conference schedule. Use this when the user asks "
                    + "to find talks by topic, keyword, or by speaker name. Returns matching sessions "
                    + "sorted by start time, each with its title, abstract, room, timing, and the "
                    + "primary speaker. Pass `query` for free-text search across titles and abstracts, "
                    + "or `speaker_name` to filter by a speaker (case-insensitive substring match). "
                    + "Both arguments may be combined.")
    public List<SessionDto> listSessions(
            @ToolArg(name = "query",
                    description = "Optional case-insensitive substring matched against session "
                            + "titles and abstracts.",
                    required = false) String query,
            @ToolArg(name = "speaker_name",
                    description = "Optional speaker name (case-insensitive substring match).",
                    required = false) String speakerName) {
        return api.listSessions(null, speakerName, query);
    }

    @Tool(name = "get_session",
            description = "Get full details for one session, including its abstract, room, timing, "
                    + "cancellation state, and the primary speaker. Use this after `list_sessions` "
                    + "when the user picks a specific talk and wants more context.")
    public SessionDto getSession(
            @ToolArg(name = "session_id",
                    description = "Numeric session id returned by `list_sessions`.",
                    required = true) Long sessionId) {
        return api.getSession(sessionId);
    }

    @Tool(name = "whats_on_now",
            description = "List the sessions that are happening right now at jPrime 2026. Use this "
                    + "when the user asks `what is on now`, `what is happening`, or `what is in the "
                    + "current slot`. The conference clock can be pinned via the `DEMO_NOW` "
                    + "environment variable so rehearsals stay deterministic.")
    public List<SessionDto> whatsOnNow() {
        return api.currentSessions(demoNow.orElse(null));
    }

    @Tool(name = "whats_next",
            description = "List the next upcoming sessions starting after the current conference "
                    + "clock. Use this when the user asks `what is next`, `what is coming up`, or "
                    + "wants a short look-ahead. Returns three sessions by default; cap at 20.")
    public List<SessionDto> whatsNext(
            @ToolArg(name = "limit",
                    description = "Maximum number of upcoming sessions to return. Defaults to 3.",
                    required = false, defaultValue = "3") Integer limit) {
        return api.nextSessions(demoNow.orElse(null), limit);
    }
}
