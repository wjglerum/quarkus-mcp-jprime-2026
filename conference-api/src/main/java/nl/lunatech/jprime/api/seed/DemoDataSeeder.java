package nl.lunatech.jprime.api.seed;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.lunatech.jprime.api.domain.Attendee;
import nl.lunatech.jprime.api.domain.Bookmark;
import nl.lunatech.jprime.api.domain.Rating;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.domain.Speaker;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Random;

/**
 * Generates the demo's fake attendees, bookmarks, and ratings. Idempotent --
 * skips itself if attendees already exist. Designed to leave plenty of feedback
 * on Willem Jan's sessions for the speaker-feedback demo.
 */
@ApplicationScoped
public class DemoDataSeeder {

    private static final List<String[]> FAKE_PEOPLE = List.of(
            new String[]{"attendee-alice",   "Alice Krasteva",  "alice@example.com"},
            new String[]{"attendee-bob",     "Bob Dimitrov",    "bob@example.com"},
            new String[]{"attendee-carla",   "Carla Petrova",   "carla@example.com"},
            new String[]{"attendee-dimo",    "Dimo Yankov",     "dimo@example.com"},
            new String[]{"attendee-eva",     "Eva Stoyanova",   "eva@example.com"},
            new String[]{"attendee-filip",   "Filip Nikolov",   "filip@example.com"},
            new String[]{"attendee-galya",   "Galya Ivanova",   "galya@example.com"},
            new String[]{"attendee-hristo",  "Hristo Marinov",  "hristo@example.com"},
            new String[]{"attendee-iva",     "Iva Georgieva",   "iva@example.com"},
            new String[]{"attendee-jordan",  "Jordan Petrov",   "jordan@example.com"}
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
        speakerAttendee.email = "willem.jan@lunatech.nl";
        speakerAttendee.isSpeaker = true;
        speakerAttendee.speaker = willemJan;
        speakerAttendee.persist();

        for (String[] row : FAKE_PEOPLE) {
            Attendee a = new Attendee();
            a.subject = row[0];
            a.displayName = row[1];
            a.email = row[2];
            a.isSpeaker = false;
            a.persist();
        }

        List<Attendee> attendees = Attendee.listAll();
        List<Session> sessions = Session.listAll();
        Random rnd = new Random(42);

        int bookmarks = 0;
        for (Attendee a : attendees) {
            int n = 1 + rnd.nextInt(3);
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

        if (willemJan != null) {
            List<Session> mine = Session.list(
                    "select distinct s from Session s join s.speakers sp where sp.id = ?1",
                    willemJan.id);
            for (Session s : mine) {
                for (int i = 0; i < 5 && i < attendees.size(); i++) {
                    Attendee a = attendees.get(i);
                    if (Bookmark.findOne(a.id, s.id) == null) {
                        Bookmark b = new Bookmark();
                        b.attendee = a;
                        b.session = s;
                        b.createdAt = OffsetDateTime.parse("2026-06-02T10:00:00+03:00").plusMinutes(bookmarks);
                        b.persist();
                        bookmarks++;
                    }
                }
            }
        }

        int ratings = 0;
        for (int i = 0; i < 30; i++) {
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

        if (willemJan != null) {
            List<Session> mine = Session.list(
                    "select distinct s from Session s join s.speakers sp where sp.id = ?1",
                    willemJan.id);
            for (Session s : mine) {
                for (int i = 0; i < 5 && i < attendees.size(); i++) {
                    Attendee a = attendees.get(i);
                    if (a.subject.equals("willem.jan")) continue;
                    if (Rating.findOne(a.id, s.id) != null) continue;
                    Rating r = new Rating();
                    r.attendee = a;
                    r.session = s;
                    r.stars = 4 + (i % 2);
                    r.comment = NICE_COMMENTS.get(i % NICE_COMMENTS.size());
                    r.createdAt = OffsetDateTime.parse("2026-06-03T11:30:00+03:00").plusMinutes(ratings);
                    r.persist();
                    ratings++;
                }
            }
        }

        Log.infof("Demo data: seeded %d attendees, %d bookmarks, %d ratings",
                attendees.size(), bookmarks, ratings);
        return attendees.size();
    }

    @Transactional
    public void wipeUserData() {
        Rating.deleteAll();
        Bookmark.deleteAll();
        nl.lunatech.jprime.api.domain.AuditEvent.deleteAll();
        Attendee.deleteAll();
        Log.info("Demo data: wiped user-generated rows (ratings, bookmarks, audit, attendees)");
    }
}
