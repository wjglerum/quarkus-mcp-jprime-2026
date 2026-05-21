package nl.lunatech.jprime.api.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "audit_event")
public class AuditEvent extends PanacheEntity {

    @Column(name = "attendee_subject", nullable = false)
    public String attendeeSubject;

    @Column(nullable = false, length = 64)
    public String action;

    @Column(nullable = false)
    public String target;

    @Column(name = "token_acr")
    public String tokenAcr;

    @Column(name = "token_amr", columnDefinition = "TEXT")
    public String tokenAmr;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    public String detail;
}
