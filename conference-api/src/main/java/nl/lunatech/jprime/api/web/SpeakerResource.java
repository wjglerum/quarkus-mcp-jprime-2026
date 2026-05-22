package nl.lunatech.jprime.api.web;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.domain.Speaker;
import nl.lunatech.jprime.api.dto.SessionDto;
import nl.lunatech.jprime.api.dto.SpeakerListDto;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/speakers")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "speakers", description = "Conference speakers")
public class SpeakerResource {

    @GET
    @Transactional
    public List<SpeakerListDto> list() {
        return Speaker.<Speaker>listAll().stream()
                .sorted((a, b) -> a.name.compareToIgnoreCase(b.name))
                .map(sp -> {
                    List<SessionDto> sessions = Session.<Session>list(
                                    "from Session s left join fetch s.speaker where s.speaker.id = ?1 order by s.startsAt",
                                    sp.id)
                            .stream().map(SessionDto::of).toList();
                    return new SpeakerListDto(sp.id, sp.name, sp.bio, sessions);
                })
                .toList();
    }
}
