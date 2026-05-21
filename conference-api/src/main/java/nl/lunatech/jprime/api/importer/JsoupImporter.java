package nl.lunatech.jprime.api.importer;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import nl.lunatech.jprime.api.domain.Level;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.domain.Speaker;
import nl.lunatech.jprime.api.domain.Track;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Best-effort importer for the live jprime.io agenda.
 *
 * The page structure may change at any time. This importer logs a loud warning
 * and returns 0 rather than crashing if it cannot parse a page; callers should
 * fall back to the baked-in static schedule.
 */
@ApplicationScoped
public class JsoupImporter {

    static final ZoneId SOFIA = ZoneId.of("Europe/Sofia");
    static final DateTimeFormatter PARSER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

    @ConfigProperty(name = "jprime.agenda.url")
    String agendaUrl;

    @Transactional
    public ImportSummary importAgenda() {
        try {
            Document doc = Jsoup.connect(agendaUrl)
                    .userAgent("jprime-mcp-demo/1.0 (+https://lunatech.nl)")
                    .timeout(10_000)
                    .get();

            Elements sessionRows = doc.select("[data-session-id], .agenda-session, article.session");
            if (sessionRows.isEmpty()) {
                Log.warnf("jPrime importer: no session rows matched on %s — page format probably changed", agendaUrl);
                return ImportSummary.empty();
            }

            int sessionCount = 0;
            int speakerCount = 0;
            Map<String, Speaker> speakerCache = new HashMap<>();

            for (Element row : sessionRows) {
                String externalId = firstAttr(row, "data-session-id", "id");
                if (externalId == null) continue;

                Session session = Session.findByExternalId(externalId);
                if (session == null) {
                    session = new Session();
                    session.externalId = externalId;
                }
                session.title = text(row, ".session-title, h2, h3, .title");
                session.abstractText = text(row, ".session-abstract, .abstract, p");
                session.room = textOr(row, ".session-room, .room", "Hall A");
                session.track = parseTrack(session.room);
                session.level = parseLevel(text(row, ".level, .session-level"));

                String startStr = firstAttr(row, "data-start", "data-starts-at");
                String endStr = firstAttr(row, "data-end", "data-ends-at");
                if (startStr == null || endStr == null) {
                    Log.warnf("jPrime importer: session %s missing start/end attributes, skipping", externalId);
                    continue;
                }
                session.startsAt = parseTimestamp(startStr);
                session.endsAt = parseTimestamp(endStr);
                session.day = session.startsAt.toLocalDate().getDayOfMonth() == 3 ? 1 : 2;

                session.persist();
                sessionCount++;

                Elements speakerElems = row.select(".speaker, .session-speaker, [data-speaker-id]");
                for (Element sp : speakerElems) {
                    String spExt = firstAttr(sp, "data-speaker-id", "id");
                    String spName = textOr(sp, ".speaker-name, .name", sp.text());
                    if (spName == null || spName.isBlank()) continue;
                    String key = spExt != null ? spExt : spName.toLowerCase();
                    Speaker speaker = speakerCache.get(key);
                    if (speaker == null) {
                        speaker = spExt != null ? Speaker.findByExternalId(spExt) : null;
                        if (speaker == null) {
                            speaker = new Speaker();
                            speaker.externalId = spExt;
                            speakerCount++;
                        }
                        speaker.name = spName;
                        speaker.bio = textOr(sp, ".speaker-bio, .bio", speaker.bio);
                        speaker.company = textOr(sp, ".speaker-company, .company", speaker.company);
                        speaker.twitterHandle = textOr(sp, ".speaker-twitter, .twitter", speaker.twitterHandle);
                        speaker.persist();
                        speakerCache.put(key, speaker);
                    }
                    session.speakers.add(speaker);
                }
            }

            Log.infof("jPrime importer: imported %d sessions and %d new speakers", sessionCount, speakerCount);
            return new ImportSummary(sessionCount, speakerCount);
        } catch (Exception e) {
            Log.warnf(e, "jPrime importer failed; falling back to seed data");
            return ImportSummary.empty();
        }
    }

    private static String text(Element el, String selector) {
        Element found = el.selectFirst(selector);
        return found == null ? null : found.text().trim();
    }

    private static String textOr(Element el, String selector, String fallback) {
        String t = text(el, selector);
        return t == null || t.isBlank() ? fallback : t;
    }

    private static String firstAttr(Element el, String... names) {
        for (String n : names) {
            String v = el.attr(n);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private static OffsetDateTime parseTimestamp(String s) {
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception ignore) {
            return PARSER.parse(s, java.time.LocalDateTime::from).atZone(SOFIA).toOffsetDateTime();
        }
    }

    private static Track parseTrack(String room) {
        if (room == null) return Track.HALL_A;
        String lower = room.toLowerCase(Locale.ENGLISH);
        if (lower.contains("workshop")) return Track.WORKSHOP;
        if (lower.contains("b")) return Track.HALL_B;
        return Track.HALL_A;
    }

    private static Level parseLevel(String text) {
        if (text == null) return null;
        String upper = text.toUpperCase(Locale.ENGLISH);
        for (Level l : Level.values()) {
            if (upper.contains(l.name())) return l;
        }
        return null;
    }

    public record ImportSummary(int sessions, int speakers) {
        public static ImportSummary empty() {
            return new ImportSummary(0, 0);
        }
    }
}
