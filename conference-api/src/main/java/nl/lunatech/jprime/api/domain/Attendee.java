package nl.lunatech.jprime.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "attendee")
public class Attendee extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String subject;

    @Column(name = "display_name", nullable = false)
    public String displayName;

    @ManyToOne
    @JoinColumn(name = "speaker_id")
    public Speaker speaker;

    public static Attendee findBySubject(String subject) {
        return find("subject", subject).firstResult();
    }

    public boolean isSpeaker() {
        return speaker != null;
    }
}
