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

    // The OAuth client that executed the action (azp claim), so the trail shows it was an AI
    // client acting for the user, not the user clicking directly.
    @Column(name = "executed_by_client")
    public String executedByClient;

    // The authorization server that minted the token (iss claim), proving where the identity
    // and the authorizing claims came from.
    @Column(name = "token_iss")
    public String tokenIss;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt;

    @Column(columnDefinition = "TEXT")
    public String detail;
}
