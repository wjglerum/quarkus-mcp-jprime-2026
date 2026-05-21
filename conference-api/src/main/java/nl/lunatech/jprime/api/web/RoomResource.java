package nl.lunatech.jprime.api.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.api.clock.DemoClock;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.web.Dtos.RoomDto;
import nl.lunatech.jprime.api.web.Dtos.SessionDto;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Path("/api/v1/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "rooms", description = "Rooms with currently-running and upcoming sessions")
public class RoomResource {

    @Inject
    DemoClock clock;

    @GET
    public List<RoomDto> list() {
        OffsetDateTime now = clock.now();
        List<Session> all = Session.list("order by room asc, startsAt asc");
        Map<String, RoomDto> byRoom = new LinkedHashMap<>();
        for (Session s : all) {
            byRoom.computeIfAbsent(s.room, r -> new RoomDto(r, null, null));
        }
        Map<String, Session> current = new java.util.HashMap<>();
        Map<String, Session> next = new java.util.HashMap<>();
        for (Session s : all) {
            if (s.cancelled) continue;
            if (!s.startsAt.isAfter(now) && s.endsAt.isAfter(now)) {
                current.putIfAbsent(s.room, s);
            } else if (s.startsAt.isAfter(now)) {
                Session existing = next.get(s.room);
                if (existing == null || s.startsAt.isBefore(existing.startsAt)) {
                    next.put(s.room, s);
                }
            }
        }
        return byRoom.keySet().stream()
                .map(room -> new RoomDto(
                        room,
                        current.get(room) == null ? null : SessionDto.of(current.get(room)),
                        next.get(room) == null ? null : SessionDto.of(next.get(room))))
                .toList();
    }
}
