package nl.lunatech.jprime.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(
        name = "bookmark",
        uniqueConstraints = @UniqueConstraint(columnNames = {"attendee_id", "session_id"})
)
public class Bookmark extends PanacheEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "attendee_id")
    public Attendee attendee;

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    public Session session;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    public static List<Bookmark> listByAttendee(Long attendeeId) {
        return list("attendee.id = ?1 order by session.startsAt", attendeeId);
    }

    public static Bookmark findOne(Long attendeeId, Long sessionId) {
        return find("attendee.id = ?1 and session.id = ?2", attendeeId, sessionId).firstResult();
    }
}
