package nl.lunatech.jprime.mcp.tools;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.jprime.mcp.api.Dtos.SessionDto;
import nl.lunatech.jprime.mcp.api.Dtos.SpeakerDto;
import nl.lunatech.jprime.mcp.api.PublicConferenceApi;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Tools that any registered MCP client may call without a user log-in. The
 * MCP client itself still needs to be registered through DCR and present a
 * token (the demo uses client_credentials at the public tier).
 */
@ApplicationScoped
public class PublicTools {

    @Inject
    @RestClient
    PublicConferenceApi api;

    @ConfigProperty(name = "demo.now")
    Optional<String> demoNow;

    @Tool(name = "list_sessions",
            description = "Search the jPrime 2026 schedule. Use this to find talks by topic, "
                    + "track, day, or speaker. Returns matching sessions sorted by start time.")
    public List<SessionDto> listSessions(
            @ToolArg(name = "day", description = "Conference day, either 1 or 2", required = false)
            Integer day,
            @ToolArg(name = "track", description = "Track to filter on: HALL_A, HALL_B, or WORKSHOP",
                    required = false) String track,
            @ToolArg(name = "query", description = "Case-insensitive substring match on title or abstract",
                    required = false) String query,
            @ToolArg(name = "speaker_name",
                    description = "Filter by speaker name (case-insensitive substring match). "
                            + "If supplied, the tool first resolves the speaker via find_speaker.",
                    required = false) String speakerName) {

        Long speakerId = null;
        if (speakerName != null && !speakerName.isBlank()) {
            String needle = speakerName.toLowerCase(Locale.ENGLISH);
            speakerId = api.listSpeakers().stream()
                    .filter(s -> s.name() != null && s.name().toLowerCase(Locale.ENGLISH).contains(needle))
                    .map(SpeakerDto::id)
                    .findFirst()
                    .orElse(null);
        }
        return api.listSessions(day, track, speakerId, null, query);
    }

    @Tool(name = "get_session",
            description = "Get full details for one session including abstract and speakers.")
    public SessionDto getSession(
            @ToolArg(name = "session_id", description = "Numeric session id from list_sessions",
                    required = true) Long sessionId) {
        return api.getSession(sessionId);
    }

    @Tool(name = "whats_on_now",
            description = "Find out which sessions are happening right now at jPrime. "
                    + "Uses the configured demo clock when set so rehearsals are deterministic.")
    public List<SessionDto> whatsOnNow() {
        return api.currentSessions(demoNow.orElse(null));
    }

    @Tool(name = "whats_next",
            description = "Find out which sessions are starting next. Default returns three sessions.")
    public List<SessionDto> whatsNext(
            @ToolArg(name = "limit", description = "Max number of upcoming sessions to return",
                    required = false, defaultValue = "3") Integer limit) {
        return api.nextSessions(demoNow.orElse(null), limit == null ? 3 : limit);
    }

    @Tool(name = "find_speaker",
            description = "Look up a speaker by name and see what they're presenting. "
                    + "Returns the matched speaker plus their sessions.")
    public SpeakerLookup findSpeaker(
            @ToolArg(name = "name", description = "Speaker name to search (case-insensitive substring)",
                    required = true) String name) {
        String needle = name.toLowerCase(Locale.ENGLISH);
        SpeakerDto match = api.listSpeakers().stream()
                .filter(s -> s.name() != null && s.name().toLowerCase(Locale.ENGLISH).contains(needle))
                .findFirst()
                .orElse(null);
        if (match == null) {
            return new SpeakerLookup(null, List.of(), "No speaker found matching '" + name + "'.");
        }
        List<SessionDto> sessions = api.speakerSessions(match.id());
        return new SpeakerLookup(match, sessions, null);
    }

    public record SpeakerLookup(SpeakerDto speaker, List<SessionDto> sessions, String message) {}
}
