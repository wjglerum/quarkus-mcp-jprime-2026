package nl.lunatech.jprime.chat.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "conference-api-public")
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
public interface PublicConferenceApi {

    @GET
    @Path("/sessions")
    List<Dtos.SessionDto> listSessions(
            @QueryParam("day") Integer day,
            @QueryParam("track") String track,
            @QueryParam("speaker_id") Long speakerId,
            @QueryParam("level") String level,
            @QueryParam("q") String q);

    @GET
    @Path("/sessions/{id}")
    Dtos.SessionDto getSession(@PathParam("id") Long id);

    @GET
    @Path("/sessions/current")
    List<Dtos.SessionDto> currentSessions(@QueryParam("at") String at);

    @GET
    @Path("/sessions/next")
    List<Dtos.SessionDto> nextSessions(@QueryParam("at") String at, @QueryParam("limit") Integer limit);

    @GET
    @Path("/speakers")
    List<Dtos.SpeakerDto> listSpeakers();

    @GET
    @Path("/speakers/{id}/sessions")
    List<Dtos.SessionDto> speakerSessions(@PathParam("id") Long id);
}
