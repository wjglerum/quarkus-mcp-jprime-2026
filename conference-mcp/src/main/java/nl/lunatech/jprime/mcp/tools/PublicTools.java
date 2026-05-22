package nl.lunatech.jprime.mcp.tools;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.jprime.mcp.dto.SessionDto;
import nl.lunatech.jprime.mcp.dto.SpeakerDto;
import nl.lunatech.jprime.mcp.api.PublicConferenceApi;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class PublicTools {

    @Inject
    @RestClient
    PublicConferenceApi api;

    @ConfigProperty(name = "demo.now")
    Optional<String> demoNow;

    @Tool(name = "list_sessions",
            description = "Search the jPrime 2026 schedule. Use this to find talks by topic or "
                    + "by speaker name. Returns matching sessions sorted by start time.")
    public List<SessionDto> listSessions(
            @ToolArg(name = "query", description = "Case-insensitive substring match on title or abstract",
                    required = false) String query,
            @ToolArg(name = "speaker_name",
                    description = "Filter by speaker name (case-insensitive substring match). "
                            + "If supplied, the tool first resolves the speaker via the speakers list.",
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
        return api.listSessions(speakerId, query);
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
        return api.nextSessions(demoNow.orElse(null), limit);
    }

}
