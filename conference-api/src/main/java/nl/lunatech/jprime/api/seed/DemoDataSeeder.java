package nl.lunatech.jprime.api.seed;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.lunatech.jprime.api.domain.Attendee;
import nl.lunatech.jprime.api.domain.AuditEvent;
import nl.lunatech.jprime.api.domain.Bookmark;
import nl.lunatech.jprime.api.domain.Rating;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.domain.Speaker;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@ApplicationScoped
public class DemoDataSeeder {

    private static final List<String[]> FAKE_PEOPLE = List.of(
            new String[]{"attendee-alice",   "Alice Anderson"},
            new String[]{"attendee-bob",     "Bob Brown"},
            new String[]{"attendee-carol",   "Carol Clarke"},
            new String[]{"attendee-dave",    "Dave Davies"},
            new String[]{"attendee-erin",    "Erin Edwards"},
            new String[]{"attendee-frank",   "Frank Foster"},
            new String[]{"attendee-grace",   "Grace Green"},
            new String[]{"attendee-heidi",   "Heidi Hughes"},
            new String[]{"attendee-ivan",    "Ivan Irwin"},
            new String[]{"attendee-judy",    "Judy Jones"}
    );

    private static final List<String> NICE_COMMENTS = List.of(
            "Excellent talk, very practical.",
            "Loved the live demo.",
            "Wish the slides had more code.",
            "Best session of the day so far.",
            "Great use of caffeine.",
            "A bit fast for a beginner audience but solid material.",
            "Made a complex topic feel simple."
    );

    @Transactional
    public int seedIfEmpty() {
        if (Attendee.count() > 0) {
            Log.infof("Demo data: %d attendees already present, skipping", Attendee.count());
            return 0;
        }

        Speaker willemJan = Speaker.find("name", "Willem Jan Glerum").firstResult();

        Attendee speakerAttendee = new Attendee();
        speakerAttendee.subject = "willem.jan";
        speakerAttendee.displayName = "Willem Jan Glerum";
        speakerAttendee.speaker = willemJan;
        speakerAttendee.persist();

        List<Attendee> attendees = new ArrayList<>();
        attendees.add(speakerAttendee);
        for (String[] row : FAKE_PEOPLE) {
            Attendee a = new Attendee();
            a.subject = row[0];
            a.displayName = row[1];
            a.persist();
            attendees.add(a);
        }

        List<Session> sessions = Session.listAll();
        Random rnd = new Random(42);

        int bookmarks = 0;
        if (!sessions.isEmpty()) {
            for (Attendee a : attendees) {
                int n = 1 + rnd.nextInt(2);
                for (int i = 0; i < n; i++) {
                    Session s = sessions.get(rnd.nextInt(sessions.size()));
                    if (Bookmark.findOne(a.id, s.id) != null) continue;
                    Bookmark b = new Bookmark();
                    b.attendee = a;
                    b.session = s;
                    b.createdAt = OffsetDateTime.parse("2026-06-01T09:00:00+03:00").plusMinutes(bookmarks);
                    b.persist();
                    bookmarks++;
                }
            }
        }

        if (willemJan != null) {
            List<Session> mine = Session.list("speaker.id", willemJan.id);
            for (Session s : mine) {
                for (int i = 0; i < 3 && i + 1 < attendees.size(); i++) {
                    Attendee a = attendees.get(i + 1);
                    if (Bookmark.findOne(a.id, s.id) != null) continue;
                    Bookmark b = new Bookmark();
                    b.attendee = a;
                    b.session = s;
                    b.createdAt = OffsetDateTime.parse("2026-06-02T10:00:00+03:00").plusMinutes(bookmarks);
                    b.persist();
                    bookmarks++;
                }
            }
        }

        int ratings = 0;
        if (!sessions.isEmpty()) {
            for (int i = 0; i < 50 && ratings < 25; i++) {
                Attendee a = attendees.get(rnd.nextInt(attendees.size()));
                Session s = sessions.get(rnd.nextInt(sessions.size()));
                if (Rating.findOne(a.id, s.id) != null) continue;
                Rating r = new Rating();
                r.attendee = a;
                r.session = s;
                r.stars = 2 + rnd.nextInt(4);
                r.comment = NICE_COMMENTS.get(rnd.nextInt(NICE_COMMENTS.size()));
                r.createdAt = OffsetDateTime.parse("2026-06-03T11:00:00+03:00").plusMinutes(ratings);
                r.persist();
                ratings++;
            }
        }

        int wjgRatings = 0;
        if (willemJan != null) {
            List<Session> mine = Session.list("speaker.id", willemJan.id);
            for (Session s : mine) {
                for (int i = 1; i < attendees.size() && wjgRatings < 5; i++) {
                    Attendee a = attendees.get(i);
                    if (a.subject.equals("willem.jan")) continue;
                    if (Rating.findOne(a.id, s.id) != null) continue;
                    Rating r = new Rating();
                    r.attendee = a;
                    r.session = s;
                    r.stars = 4 + (i % 2);
                    r.comment = NICE_COMMENTS.get(i % NICE_COMMENTS.size());
                    r.createdAt = OffsetDateTime.parse("2026-06-03T11:30:00+03:00").plusMinutes(ratings + wjgRatings);
                    r.persist();
                    wjgRatings++;
                }
                if (wjgRatings >= 5) break;
            }
        }

        int auditEvents = seedAuditTrail(willemJan, sessions);

        Log.infof("Demo data: seeded %d attendees, %d bookmarks, %d ratings (%d on Willem Jan's sessions), %d audit events",
                attendees.size(), bookmarks, ratings + wjgRatings, wjgRatings, auditEvents);
        return attendees.size();
    }

    /**
     * Seed a representative audit trail so the live dashboard shows real activity on load,
     * with varied tiers (public, step-up, destructive) for the second-screen demo. Real
     * actions performed during the talk land on top of these.
     */
    private int seedAuditTrail(Speaker willemJan, List<Session> sessions) {
        if (sessions.isEmpty()) return 0;
        long talkSid = sessions.stream()
                .filter(s -> "Practical MCP Security in Action".equals(s.title))
                .map(s -> s.id).findFirst().orElse(sessions.get(0).id);
        long wjgSid = willemJan == null
                ? talkSid
                : Session.<Session>find("speaker.id", willemJan.id).<Session>firstResultOptional()
                        .map(s -> s.id).orElse(talkSid);

        String strongAcr = "urn:mace:incommon:iap:silver";
        Object[][] trail = {
                {"attendee-alice", "BOOKMARK_ADD", "session:" + talkSid, "1", "pwd", "Practical MCP Security in Action"},
                {"attendee-bob", "BOOKMARK_ADD", "session:" + talkSid, "1", "pwd", "Practical MCP Security in Action"},
                {"attendee-carol", "RATE_SESSION", "session:" + talkSid, "1", "pwd", "stars=5 comment=great use of caffeine"},
                {"attendee-dave", "RATE_SESSION_REJECTED_NOT_STARTED", "session:" + wjgSid, "1", "pwd", "stars=4"},
                {"attendee-erin", "BOOKMARK_REMOVE", "session:" + talkSid, "1", "pwd", null},
                {"willem.jan", "VIEW_SESSION_ATTENDEES", "session:" + wjgSid, strongAcr, "pwd,mfa,otp", "viewed attendee list"},
                {"willem.jan", "CANCEL_SESSION_ATTEMPTED", "session:" + wjgSid, "1", "pwd", "step-up required"},
                {"willem.jan", "CANCEL_SESSION", "session:" + wjgSid, strongAcr, "pwd,mfa,otp", "reason=room change"},
                {"willem.jan", "CANCEL_SESSION_UNDONE", "session:" + wjgSid, strongAcr, "pwd,mfa,otp", "reason=back on track"},
        };

        OffsetDateTime base = OffsetDateTime.now(ZoneOffset.of("+03:00")).minusMinutes(12);
        int n = 0;
        for (Object[] row : trail) {
            AuditEvent ev = new AuditEvent();
            ev.attendeeSubject = (String) row[0];
            ev.action = (String) row[1];
            ev.target = (String) row[2];
            ev.tokenAcr = (String) row[3];
            ev.tokenAmr = (String) row[4];
            ev.detail = (String) row[5];
            ev.createdAt = base.plusSeconds(n * 70L);
            ev.persist();
            n++;
        }
        return n;
    }
}
