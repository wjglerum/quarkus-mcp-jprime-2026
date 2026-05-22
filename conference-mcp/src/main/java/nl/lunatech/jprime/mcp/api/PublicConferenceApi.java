package nl.lunatech.jprime.mcp.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.mcp.dto.SessionDto;
import nl.lunatech.jprime.mcp.dto.SpeakerDto;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "conference-api-public")
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface PublicConferenceApi {

    @GET
    @Path("/sessions")
    List<SessionDto> listSessions(
            @QueryParam("speaker_id") Long speakerId,
            @QueryParam("q") String q);

    @GET
    @Path("/sessions/{id}")
    SessionDto getSession(@PathParam("id") Long id);

    @GET
    @Path("/sessions/current")
    List<SessionDto> currentSessions(@QueryParam("at") String at);

    @GET
    @Path("/sessions/next")
    List<SessionDto> nextSessions(@QueryParam("at") String at, @QueryParam("limit") Integer limit);

    @GET
    @Path("/speakers")
    List<SpeakerDto> listSpeakers();
}
