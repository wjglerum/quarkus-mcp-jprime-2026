package nl.lunatech.jprime.api.dto;

import nl.lunatech.jprime.api.domain.AuditEvent;

import java.time.OffsetDateTime;

public record AuditEventDto(
        Long id,
        String attendeeSubject,
        String action,
        String target,
        String tokenAcr,
        String tokenAmr,
        String executedByClient,
        String tokenIss,
        OffsetDateTime createdAt,
        String detail
) {
    public static AuditEventDto of(AuditEvent e) {
        return new AuditEventDto(
                e.id, e.attendeeSubject, e.action, e.target,
                e.tokenAcr, e.tokenAmr, e.executedByClient, e.tokenIss, e.createdAt, e.detail);
    }
}
