package nl.lunatech.jprime.mcp.tools;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import nl.lunatech.jprime.mcp.api.Dtos.AttendeeBookmarkDto;
import nl.lunatech.jprime.mcp.api.Dtos.CancelSessionRequest;
import nl.lunatech.jprime.mcp.api.Dtos.SessionDto;
import nl.lunatech.jprime.mcp.api.MeConferenceApi;
import nl.lunatech.jprime.mcp.security.McpSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class StepUpTools {

    @Inject
    @RestClient
    MeConferenceApi me;

    @Inject
    McpSecurity security;

    @Tool(name = "view_session_attendees",
            description = "As a speaker, see the list of attendees who bookmarked one of my sessions. "
                    + "This contains personal data (names and emails) and requires recent MFA-backed "
                    + "authentication (step-up). If the current token's acr is insufficient, the "
                    + "server returns an insufficient_user_authentication error and the client should "
                    + "re-authenticate with a higher acr_values request.")
    public List<AttendeeBookmarkDto> viewSessionAttendees(
            @ToolArg(name = "session_id", description = "Numeric session id", required = true)
            Long sessionId) {
        security.requireStepUp();
        try (Response r = me.sessionAttendees(sessionId)) {
            if (r.getStatus() == 401) {
                throw new ToolCallException(
                        "insufficient_user_authentication: backend requires step-up. "
                                + "Re-authenticate with acr_values=urn:mace:incommon:iap:silver and retry.");
            }
            if (r.getStatus() >= 400) {
                throw new ToolCallException("backend_error: " + r.readEntity(String.class));
            }
            return r.readEntity(new jakarta.ws.rs.core.GenericType<List<AttendeeBookmarkDto>>() {});
        } catch (WebApplicationException wae) {
            if (wae.getResponse().getStatus() == 401) {
                throw new ToolCallException(
                        "insufficient_user_authentication: backend rejected the token. "
                                + "Re-authenticate with acr_values=urn:mace:incommon:iap:silver and retry.");
            }
            throw wae;
        }
    }

    @Tool(name = "cancel_my_session",
            description = "As a speaker, mark one of my own sessions as cancelled (with a reason). "
                    + "Highly destructive but reversible by calling this tool again with the same "
                    + "session id. The action is fully audited. Requires recent MFA-backed "
                    + "authentication (step-up).")
    public SessionDto cancelMySession(
            @ToolArg(name = "session_id", description = "Numeric session id", required = true)
            Long sessionId,
            @ToolArg(name = "reason", description = "Free-text reason recorded in the audit log",
                    required = true) String reason) {
        security.requireStepUp();
        return me.cancelSession(sessionId, new CancelSessionRequest(reason));
    }
}
