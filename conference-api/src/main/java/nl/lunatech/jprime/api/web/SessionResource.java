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
import nl.lunatech.jprime.api.domain.Level;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.domain.Track;
import nl.lunatech.jprime.api.web.Dtos.SessionDto;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Path("/api/v1/sessions")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "sessions", description = "Public conference schedule")
public class SessionResource {

    @Inject
    DemoClock clock;

    @GET
    public List<SessionDto> list(
            @QueryParam("day") Integer day,
            @QueryParam("track") Track track,
            @QueryParam("speaker_id") Long speakerId,
            @QueryParam("level") Level level,
            @QueryParam("q") String q
    ) {
        StringBuilder hql = new StringBuilder("from Session s left join fetch s.speakers where 1=1");
        Map<String, Object> params = new HashMap<>();
        if (day != null) {
            hql.append(" and s.day = :day");
            params.put("day", day);
        }
        if (track != null) {
            hql.append(" and s.track = :track");
            params.put("track", track);
        }
        if (level != null) {
            hql.append(" and s.level = :level");
            params.put("level", level);
        }
        if (speakerId != null) {
            hql.append(" and exists (select 1 from Session s2 join s2.speakers sp where s2.id = s.id and sp.id = :spId)");
            params.put("spId", speakerId);
        }
        if (q != null && !q.isBlank()) {
            hql.append(" and (lower(s.title) like :q or lower(s.abstractText) like :q)");
            params.put("q", "%" + q.toLowerCase() + "%");
        }
        hql.append(" order by s.startsAt asc");

        List<Session> sessions = Session.find(hql.toString(), params).list();
        return distinctById(sessions).stream().map(SessionDto::of).toList();
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
        OffsetDateTime now = clock.at(at);
        List<Session> rows = Session.list(
                "from Session s left join fetch s.speakers where s.startsAt <= ?1 and s.endsAt > ?1 and s.cancelled = false order by s.startsAt",
                now);
        return distinctById(rows).stream().map(SessionDto::of).toList();
    }

    @GET
    @Path("/next")
    public List<SessionDto> next(@QueryParam("at") String at, @QueryParam("limit") Integer limit) {
        OffsetDateTime now = clock.at(at);
        int lim = limit == null ? 3 : Math.max(1, Math.min(limit, 20));
        List<Session> rows = Session.list(
                "from Session s left join fetch s.speakers where s.startsAt > ?1 and s.cancelled = false order by s.startsAt",
                now);
        List<Session> distinct = distinctById(rows);
        return distinct.stream().limit(lim).map(SessionDto::of).toList();
    }

    private static List<Session> distinctById(List<Session> sessions) {
        Map<Long, Session> byId = new java.util.LinkedHashMap<>();
        for (Session s : sessions) byId.putIfAbsent(s.id, s);
        return new ArrayList<>(byId.values());
    }
}
