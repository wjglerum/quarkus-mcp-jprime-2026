package nl.lunatech.jprime.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "conference_session")
public class Session extends PanacheEntity {

    @Column(nullable = false, length = 512)
    public String title;

    @Column(name = "abstract_text", columnDefinition = "TEXT")
    public String abstractText;

    @Column(nullable = false)
    public String room;

    @Column(name = "starts_at", nullable = false)
    public OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    public OffsetDateTime endsAt;

    @Column(nullable = false)
    public boolean cancelled;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    public String cancellationReason;

    @ManyToOne
    @JoinColumn(name = "speaker_id")
    public Speaker speaker;

    public boolean overlaps(Session other) {
        return startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(endsAt);
    }

    /** Schedule search with any combination of speaker id, speaker name, and free-text filters. */
    public static List<Session> search(Long speakerId, String speakerName, String q) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();
        if (speakerId != null) {
            conditions.add("s.speaker.id = :speakerId");
            params.put("speakerId", speakerId);
        }
        if (speakerName != null && !speakerName.isBlank()) {
            conditions.add("lower(s.speaker.name) like :speakerName");
            params.put("speakerName", "%" + speakerName.toLowerCase() + "%");
        }
        if (q != null && !q.isBlank()) {
            conditions.add("(lower(s.title) like :q or lower(s.abstractText) like :q)");
            params.put("q", "%" + q.toLowerCase() + "%");
        }
        String where = conditions.isEmpty() ? "" : " where " + String.join(" and ", conditions);
        return list("from Session s left join fetch s.speaker" + where + " order by s.startsAt asc", params);
    }

    public static List<Session> listForSpeaker(Long speakerId) {
        return list("from Session s left join fetch s.speaker where s.speaker.id = ?1 order by s.startsAt", speakerId);
    }

    public static List<Session> currentAt(OffsetDateTime now) {
        return list("from Session s left join fetch s.speaker"
                + " where s.startsAt <= ?1 and s.endsAt > ?1 and s.cancelled = false order by s.startsAt", now);
    }

    public static List<Session> upcomingAfter(OffsetDateTime now, int limit) {
        return find("from Session s left join fetch s.speaker"
                + " where s.startsAt > ?1 and s.cancelled = false order by s.startsAt", now)
                .page(0, limit).list();
    }

    /** Returns, in schedule order, the subset of the given sessions that overlap at least one other. */
    public static List<Session> overlapping(List<Session> sessions) {
        Set<Long> ids = new LinkedHashSet<>();
        for (int i = 0; i < sessions.size(); i++) {
            for (int j = i + 1; j < sessions.size(); j++) {
                if (sessions.get(i).overlaps(sessions.get(j))) {
                    ids.add(sessions.get(i).id);
                    ids.add(sessions.get(j).id);
                }
            }
        }
        return sessions.stream().filter(s -> ids.contains(s.id)).toList();
    }
}
