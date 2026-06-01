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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@ApplicationScoped
public class DemoDataSeeder {

    private static final List<String[]> FAKE_PEOPLE = List.of(
            new String[]{"attendee-alice",   "Alice Krasteva"},
            new String[]{"attendee-bob",     "Bob Dimitrov"},
            new String[]{"attendee-carla",   "Carla Petrova"},
            new String[]{"attendee-dimo",    "Dimo Yankov"},
            new String[]{"attendee-eva",     "Eva Stoyanova"},
            new String[]{"attendee-filip",   "Filip Nikolov"},
            new String[]{"attendee-galya",   "Galya Ivanova"},
            new String[]{"attendee-hristo",  "Hristo Marinov"},
            new String[]{"attendee-iva",     "Iva Georgieva"},
            new String[]{"attendee-jordan",  "Jordan Petrov"}
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

        Log.infof("Demo data: seeded %d attendees, %d bookmarks, %d ratings (%d on Willem Jan's sessions)",
                attendees.size(), bookmarks, ratings + wjgRatings, wjgRatings);
        return attendees.size();
    }
}
