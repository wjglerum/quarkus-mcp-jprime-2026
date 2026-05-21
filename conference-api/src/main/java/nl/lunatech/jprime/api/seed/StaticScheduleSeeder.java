package nl.lunatech.jprime.api.seed;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.lunatech.jprime.api.domain.Level;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.domain.Speaker;
import nl.lunatech.jprime.api.domain.Track;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offline-safe schedule baseline. Always runs if the database is empty so the
 * demo never depends on jprime.io being reachable.
 */
@ApplicationScoped
public class StaticScheduleSeeder {

    @Transactional
    public int seedIfEmpty() {
        if (Session.count() > 0) {
            Log.infof("Static schedule: already %d sessions present, skipping baseline seed", Session.count());
            return 0;
        }

        Map<String, Speaker> speakers = new LinkedHashMap<>();
        speakers.put("wjg", speaker("wjg", "Willem Jan Glerum", "Lunatech", "@wjglerum",
                "Software engineer at Lunatech, focused on JVM platforms and security."));
        speakers.put("se", speaker("se", "Sergej Tomic", "Red Hat", null,
                "Quarkus security engineer."));
        speakers.put("vk", speaker("vk", "Venkat Subramaniam", "Agile Developer", "@venkat_s",
                "Award-winning author and software architect."));
        speakers.put("ts", speaker("ts", "Trisha Gee", "Gradle", "@trisha_gee",
                "Lead developer evangelist."));
        speakers.put("md", speaker("md", "Mario Fusco", "Red Hat", "@mariofusco",
                "Author of Modern Java in Action."));
        speakers.put("hk", speaker("hk", "Holly Cummins", "IBM", "@holly_cummins",
                "Senior principal software engineer."));
        speakers.put("vn", speaker("vn", "Viktor Klang", "Oracle", "@viktorklang",
                "Java architect on the JDK team."));
        speakers.put("nl", speaker("nl", "Nicolai Parlog", "Oracle", "@nipafx",
                "Java Developer Advocate."));

        // Day 1 -- 3 June 2026
        session("100", "Keynote: The OAuth You Always Wanted", "How OAuth 2.1 finally cleans up a decade of compromise.",
                Track.HALL_A, "Hall A", Level.BEGINNER, "2026-06-03T09:00:00+03:00", "2026-06-03T09:55:00+03:00", 1,
                List.of(speakers.get("vn")));
        session("110", "Practical MCP Security in Action", "MCP authorization is OAuth 2.1 done right. Quarkus makes it tractable. Governance matters more than the token format.",
                Track.HALL_B, "Hall B", Level.BEGINNER, "2026-06-03T10:00:00+03:00", "2026-06-03T10:50:00+03:00", 1,
                List.of(speakers.get("wjg")));
        session("120", "JSpecify: Nullness for the Whole Ecosystem", "Annotation interop across JDK, Kotlin, frameworks.",
                Track.HALL_A, "Hall A", Level.INTERMEDIATE, "2026-06-03T11:00:00+03:00", "2026-06-03T11:50:00+03:00", 1,
                List.of(speakers.get("nl")));
        session("125", "Workshop: Build an MCP Server in 60 Minutes", "Hands-on with quarkus-mcp-server-sse.",
                Track.WORKSHOP, "Workshop Room", Level.BEGINNER, "2026-06-03T11:00:00+03:00", "2026-06-03T12:30:00+03:00", 1,
                List.of(speakers.get("se")));
        session("130", "Concurrency Crossroads: Virtual Threads, Loom and Beyond", "Deep dive into Project Loom for production.",
                Track.HALL_B, "Hall B", Level.ADVANCED, "2026-06-03T12:00:00+03:00", "2026-06-03T12:50:00+03:00", 1,
                List.of(speakers.get("wjg"), speakers.get("vk")));
        session("140", "Lunch", "Catered lunch and demos in the foyer.",
                Track.HALL_A, "Foyer", null, "2026-06-03T13:00:00+03:00", "2026-06-03T14:00:00+03:00", 1, List.of());
        session("150", "Refactoring at Scale", "Lessons from migrating a 5M-line Java codebase.",
                Track.HALL_A, "Hall A", Level.INTERMEDIATE, "2026-06-03T14:00:00+03:00", "2026-06-03T14:50:00+03:00", 1,
                List.of(speakers.get("ts")));
        session("160", "Reactive Streams in 2026", "Where Mutiny, Reactor, and the JDK Flow API land.",
                Track.HALL_B, "Hall B", Level.INTERMEDIATE, "2026-06-03T15:00:00+03:00", "2026-06-03T15:50:00+03:00", 1,
                List.of(speakers.get("md")));
        session("170", "Day 1 Closing", "Drinks and networking.",
                Track.HALL_A, "Foyer", null, "2026-06-03T17:00:00+03:00", "2026-06-03T18:30:00+03:00", 1, List.of());

        // Day 2 -- 4 June 2026
        session("200", "Keynote: Software Engineering after AI", "What stays. What changes.",
                Track.HALL_A, "Hall A", Level.BEGINNER, "2026-06-04T09:00:00+03:00", "2026-06-04T09:55:00+03:00", 2,
                List.of(speakers.get("hk")));
        session("210", "Modern Persistence Patterns with Panache 3", "Hibernate ORM with Panache for the productive Quarkus developer.",
                Track.HALL_A, "Hall A", Level.INTERMEDIATE, "2026-06-04T10:00:00+03:00", "2026-06-04T10:50:00+03:00", 2,
                List.of(speakers.get("md")));
        session("220", "Java 25 Language Features You Will Actually Use", "Pattern matching, records, sealed types, sequenced collections in practice.",
                Track.HALL_B, "Hall B", Level.BEGINNER, "2026-06-04T10:00:00+03:00", "2026-06-04T10:50:00+03:00", 2,
                List.of(speakers.get("nl")));
        session("230", "Testing Reactive Code Without Losing Your Mind", "Mutiny, virtual threads, and a sane test architecture.",
                Track.HALL_A, "Hall A", Level.ADVANCED, "2026-06-04T11:00:00+03:00", "2026-06-04T11:50:00+03:00", 2,
                List.of(speakers.get("ts")));
        session("240", "Building AI Agents that Respect Your Identity", "How OIDC and step-up auth make AI tool use safe.",
                Track.HALL_B, "Hall B", Level.INTERMEDIATE, "2026-06-04T11:00:00+03:00", "2026-06-04T11:50:00+03:00", 2,
                List.of(speakers.get("wjg"), speakers.get("se")));
        session("250", "Closing keynote: 30 Years of Java", "A look back, and forward.",
                Track.HALL_A, "Hall A", Level.BEGINNER, "2026-06-04T16:00:00+03:00", "2026-06-04T16:55:00+03:00", 2,
                List.of(speakers.get("vk")));

        Log.infof("Static schedule: seeded %d sessions and %d speakers", Session.count(), Speaker.count());
        return (int) Session.count();
    }

    private static Speaker speaker(String externalId, String name, String company, String twitter, String bio) {
        Speaker s = new Speaker();
        s.externalId = externalId;
        s.name = name;
        s.company = company;
        s.twitterHandle = twitter;
        s.bio = bio;
        s.persist();
        return s;
    }

    private static Session session(String externalId, String title, String abstractText, Track track, String room,
                                   Level level, String startsAt, String endsAt, int day,
                                   List<Speaker> speakers) {
        Session s = new Session();
        s.externalId = externalId;
        s.title = title;
        s.abstractText = abstractText;
        s.track = track;
        s.room = room;
        s.level = level;
        s.startsAt = OffsetDateTime.parse(startsAt);
        s.endsAt = OffsetDateTime.parse(endsAt);
        s.day = day;
        s.cancelled = false;
        for (Speaker sp : speakers) {
            if (sp != null) s.speakers.add(sp);
        }
        s.persist();
        return s;
    }
}
