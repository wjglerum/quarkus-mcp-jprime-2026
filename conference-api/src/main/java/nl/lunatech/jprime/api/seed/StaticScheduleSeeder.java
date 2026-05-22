package nl.lunatech.jprime.api.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.domain.Speaker;

import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class StaticScheduleSeeder {

    private static final String AGENDA_RESOURCE = "seed/jprime-2026-agenda.json";

    @Transactional
    public int seedIfEmpty() {
        if (Session.count() > 0) {
            Log.infof("Static schedule: already %d sessions present, skipping baseline seed",
                    Session.count());
            return 0;
        }

        Agenda agenda = loadAgenda();
        if (agenda == null || agenda.sessions == null || agenda.sessions.isEmpty()) {
            Log.warn("Static schedule: agenda JSON missing or empty, no sessions seeded");
            return 0;
        }

        Map<String, Speaker> speakers = new HashMap<>();
        if (agenda.speakers != null) {
            for (SpeakerJson sp : agenda.speakers) {
                if (sp == null || sp.name == null || sp.name.isBlank()) continue;
                Speaker entity = new Speaker();
                entity.name = sp.name;
                entity.bio = sp.bio;
                entity.persist();
                speakers.put(sp.name, entity);
            }
        }

        int sessionCount = 0;
        for (SessionJson row : agenda.sessions) {
            if (row == null || row.title == null || row.title.isBlank()) continue;
            Speaker speaker = row.speaker == null ? null : speakers.get(row.speaker);
            if (speaker == null && row.speaker != null && !row.speaker.isBlank()) {
                speaker = new Speaker();
                speaker.name = row.speaker;
                speaker.persist();
                speakers.put(row.speaker, speaker);
            }

            Session s = new Session();
            s.title = row.title;
            s.abstractText = row.abstractText != null ? row.abstractText : row.title;
            s.room = row.room == null ? "TBA" : row.room;
            s.startsAt = OffsetDateTime.parse(row.startsAt);
            s.endsAt = OffsetDateTime.parse(row.endsAt);
            s.cancelled = false;
            s.speaker = speaker;
            s.persist();
            sessionCount++;
        }

        Log.infof("Static schedule: seeded %d sessions and %d speakers from %s",
                sessionCount, speakers.size(), AGENDA_RESOURCE);
        return sessionCount;
    }

    private Agenda loadAgenda() {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(AGENDA_RESOURCE)) {
            if (in == null) {
                Log.warnf("Static schedule: %s not found on classpath", AGENDA_RESOURCE);
                return null;
            }
            return new ObjectMapper().readValue(in, Agenda.class);
        } catch (Exception e) {
            Log.warnf(e, "Static schedule: failed to parse %s", AGENDA_RESOURCE);
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Agenda {
        public Integer year;
        public String timezone;
        public List<SpeakerJson> speakers;
        public List<SessionJson> sessions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SpeakerJson {
        public String name;
        public String bio;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SessionJson {
        public String title;
        @JsonProperty("abstract")
        public String abstractText;
        public String speaker;
        public String room;
        public String startsAt;
        public String endsAt;
    }
}
