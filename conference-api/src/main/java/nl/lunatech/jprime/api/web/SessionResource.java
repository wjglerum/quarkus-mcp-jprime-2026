package nl.lunatech.jprime.api.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.api.clock.DemoClock;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.dto.SessionDto;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "sessions", description = "Public conference schedule")
public class SessionResource {

    @Inject
    DemoClock clock;

    @GET
    public List<SessionDto> list(
            @QueryParam("speaker_id") Long speakerId,
            @QueryParam("speaker_name") String speakerName,
            @QueryParam("q") String q
    ) {
        return Session.search(speakerId, speakerName, q).stream().map(SessionDto::of).toList();
    }

    @GET
    @Path("/{id}")
    public SessionDto get(@PathParam("id") Long id) {
        Session s = Session.findById(id);
        if (s == null) throw new NotFoundException("session " + id);
        return SessionDto.of(s);
    }

    @GET
    @Path("/current")
    public List<SessionDto> current(@QueryParam("at") String at) {
        return Session.currentAt(clock.at(at)).stream().map(SessionDto::of).toList();
    }

    @GET
    @Path("/next")
    public List<SessionDto> next(@QueryParam("at") String at, @QueryParam("limit") Integer limit) {
        int lim = limit == null ? 3 : Math.max(1, Math.min(limit, 20));
        return Session.upcomingAfter(clock.at(at), lim).stream().map(SessionDto::of).toList();
    }
}
