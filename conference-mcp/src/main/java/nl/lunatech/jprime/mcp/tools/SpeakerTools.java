package nl.lunatech.jprime.mcp.tools;

import io.quarkiverse.mcp.server.Tool;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.jprime.mcp.api.MeConferenceApi;
import nl.lunatech.jprime.mcp.dto.SessionFeedbackDto;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
@RolesAllowed("speaker")
public class SpeakerTools {

    @Inject
    @RestClient
    MeConferenceApi me;

    @Tool(name = "my_session_feedback",
            description = "Return the ratings and comments attendees have left for the "
                    + "authenticated speaker's sessions. One entry per session, including aggregate "
                    + "star count, average, distribution, and the individual ratings. Use this "
                    + "when the speaker asks how their talks were received or wants to read "
                    + "attendee feedback. Requires the `speaker` role.")
    public List<SessionFeedbackDto> mySessionFeedback() {
        return me.mySessionFeedback();
    }
}
