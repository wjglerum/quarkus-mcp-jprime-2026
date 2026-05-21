package nl.lunatech.jprime.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "conference_session")
public class Session extends PanacheEntity {

    @Column(name = "external_id")
    public String externalId;

    @Column(nullable = false, length = 512)
    public String title;

    @Column(name = "abstract_text", columnDefinition = "TEXT")
    public String abstractText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public Track track;

    @Column(nullable = false)
    public String room;

    @Enumerated(EnumType.STRING)
    public Level level;

    @Column(name = "starts_at", nullable = false)
    public OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    public OffsetDateTime endsAt;

    @Column(nullable = false)
    public int day;

    @Column(nullable = false)
    public boolean cancelled;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    public String cancellationReason;

    @ManyToMany
    @JoinTable(
            name = "conference_session_speaker",
            joinColumns = @JoinColumn(name = "session_id"),
            inverseJoinColumns = @JoinColumn(name = "speaker_id")
    )
    public Set<Speaker> speakers = new HashSet<>();

    public static Session findByExternalId(String externalId) {
        return find("externalId", externalId).firstResult();
    }

    public boolean overlaps(Session other) {
        return startsAt.isBefore(other.endsAt) && other.startsAt.isBefore(endsAt);
    }
}
