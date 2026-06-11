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

    @Inject
    SessionResolver sessions;

    @Tool(name = "view_session_attendees",
            description = "List the display names of attendees who bookmarked one of the speaker's "
                    + "sessions. Exposes personal data, so it requires recent MFA-backed step-up. If "
                    + "the token does not satisfy the acr requirement, returns "
                    + "`insufficient_user_authentication`; the client must re-authenticate with "
                    + "`acr_values=urn:jprime:mfa` and retry. Tell the user to sign in "
                    + "again with a one-time code.")
    public List<AttendeeBookmarkDto> viewSessionAttendees(
            @ToolArg(name = "session_id", description = SessionResolver.SESSION_ID, required = false) Long sessionId,
            @ToolArg(name = "session_query", description = SessionResolver.SESSION_QUERY, required = false) String sessionQuery) {
        stepUp.require();
        return me.sessionAttendees(sessions.resolve(sessionId, sessionQuery));
    }

    @Tool(name = "cancel_my_session",
            description = "Mark one of the speaker's own sessions as cancelled, with a recorded "
                    + "reason. Reversible: calling again toggles it off and records "
                    + "`CANCEL_SESSION_UNDONE`. Destructive, so it requires MFA-backed step-up. Pass "
                    + "session_query when you lack the id. Tell the user it is audited and visible on "
                    + "the live audit feed.")
    public SessionDto cancelMySession(
            @ToolArg(name = "session_id", description = SessionResolver.SESSION_ID, required = false) Long sessionId,
            @ToolArg(name = "session_query", description = SessionResolver.SESSION_QUERY, required = false) String sessionQuery,
            @ToolArg(name = "reason",
                    description = "Free-text reason recorded in the audit log.",
                    required = true) String reason) {
        stepUp.require();
        return me.cancelSession(sessions.resolve(sessionId, sessionQuery), new CancelSessionRequest(reason));
    }
}
