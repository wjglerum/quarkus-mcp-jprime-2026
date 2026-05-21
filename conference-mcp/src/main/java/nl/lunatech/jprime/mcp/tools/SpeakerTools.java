package nl.lunatech.jprime.mcp.tools;

import io.quarkiverse.mcp.server.Tool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.lunatech.jprime.mcp.api.Dtos.SessionFeedbackDto;
import nl.lunatech.jprime.mcp.api.MeConferenceApi;
import nl.lunatech.jprime.mcp.security.McpSecurity;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class SpeakerTools {

    @Inject
    @RestClient
    MeConferenceApi me;

    @Inject
    McpSecurity security;

    @Tool(name = "my_session_feedback",
            description = "As a speaker, see the ratings and comments attendees have left for my "
                    + "sessions. Returns one entry per session with aggregate stars and the "
                    + "individual ratings. Requires the 'speaker' role.")
    public List<SessionFeedbackDto> mySessionFeedback() {
        security.requireRole("speaker");
        return me.mySessionFeedback();
    }
}
