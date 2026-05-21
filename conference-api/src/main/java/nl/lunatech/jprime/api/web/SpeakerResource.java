package nl.lunatech.jprime.api.web;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.domain.Speaker;
import nl.lunatech.jprime.api.web.Dtos.SessionDto;
import nl.lunatech.jprime.api.web.Dtos.SpeakerDto;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/speakers")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "speakers", description = "Conference speakers")
public class SpeakerResource {

    @GET
    public List<SpeakerDto> list() {
        return Speaker.<Speaker>listAll().stream()
                .sorted((a, b) -> a.name.compareToIgnoreCase(b.name))
                .map(SpeakerDto::of)
                .toList();
    }

    @GET
    @Path("/{id}")
    public SpeakerDto get(@PathParam("id") Long id) {
        Speaker s = Speaker.findById(id);
        if (s == null) throw new NotFoundException("speaker " + id);
        return SpeakerDto.of(s);
    }

    @GET
    @Path("/{id}/sessions")
    public List<SessionDto> sessions(@PathParam("id") Long id) {
        Speaker s = Speaker.findById(id);
        if (s == null) throw new NotFoundException("speaker " + id);
        return Session.<Session>list(
                "select distinct s from Session s join fetch s.speakers sp where sp.id = ?1 order by s.startsAt",
                id)
                .stream().map(SessionDto::of).toList();
    }
}
