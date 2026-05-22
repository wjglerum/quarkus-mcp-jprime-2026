package nl.lunatech.jprime.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

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
}
