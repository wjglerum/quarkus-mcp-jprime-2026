package nl.lunatech.jprime.mcp.tools;

import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.jprime.mcp.api.PublicConferenceApi;
import nl.lunatech.jprime.mcp.dto.SessionDto;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Resolves a session reference to a numeric id so the session-acting tools can be
 * called with either an explicit {@code session_id} or a human {@code session_query}
 * (a few words of the title). This lets the model act on "the MCP Security talk" in a
 * single tool call instead of having to look the id up first.
 */
@ApplicationScoped
public class SessionResolver {

    @Inject
    @RestClient
    PublicConferenceApi api;

    public long resolve(Long sessionId, String sessionQuery) {
        if (sessionId != null) {
            return sessionId;
        }
        if (sessionQuery == null || sessionQuery.isBlank()) {
            throw new ToolCallException("invalid_argument: provide either session_id or session_query");
        }
        return api.listSessions(null, null, sessionQuery).stream()
                .findFirst()
                .map(SessionDto::id)
                .orElseThrow(() -> new ToolCallException(
                        "not_found: no session matches \"" + sessionQuery + "\". "
                                + "Try fewer, more distinctive words from the title."));
    }
}
