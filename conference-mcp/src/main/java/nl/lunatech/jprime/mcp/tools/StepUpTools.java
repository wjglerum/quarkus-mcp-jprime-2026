package nl.lunatech.jprime.mcp.tools;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.jprime.mcp.api.MeConferenceApi;
import nl.lunatech.jprime.mcp.dto.AttendeeBookmarkDto;
import nl.lunatech.jprime.mcp.dto.CancelSessionRequest;
import nl.lunatech.jprime.mcp.dto.SessionDto;
import nl.lunatech.jprime.mcp.security.StepUp;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
@RolesAllowed("speaker")
public class StepUpTools {

    @Inject
    @RestClient
    MeConferenceApi me;

    @Inject
    StepUp stepUp;

    @Tool(name = "view_session_attendees",
            description = "List the attendees (display names) who bookmarked one of the "
                    + "authenticated speaker's sessions. This exposes personal data and requires "
                    + "recent MFA-backed authentication, also known as step-up. If the current "
                    + "token does not satisfy the acr requirement, the tool returns an "
                    + "`insufficient_user_authentication` error; the client must then "
                    + "re-authenticate with `acr_values=urn:mace:incommon:iap:silver` and retry. "
                    + "Tell the user that signing in again with a one-time code is needed.")
    public List<AttendeeBookmarkDto> viewSessionAttendees(
            @ToolArg(name = "session_id",
                    description = "Numeric session id owned by the authenticated speaker.",
                    required = true) Long sessionId) {
        stepUp.require();
        return me.sessionAttendees(sessionId);
    }

    @Tool(name = "cancel_my_session",
            description = "Mark one of the authenticated speaker's own sessions as cancelled, with "
                    + "a recorded reason. Reversible: calling the tool again with the same session "
                    + "id toggles the cancellation off and records `CANCEL_SESSION_UNDONE`. Highly "
                    + "destructive in intent, so it requires recent MFA-backed authentication "
                    + "(step-up). Tell the user this action is fully audited and visible on the "
                    + "live audit feed.")
    public SessionDto cancelMySession(
            @ToolArg(name = "session_id",
                    description = "Numeric session id owned by the authenticated speaker.",
                    required = true) Long sessionId,
            @ToolArg(name = "reason",
                    description = "Free-text reason recorded in the audit log.",
                    required = true) String reason) {
        stepUp.require();
        return me.cancelSession(sessionId, new CancelSessionRequest(reason));
    }
}
