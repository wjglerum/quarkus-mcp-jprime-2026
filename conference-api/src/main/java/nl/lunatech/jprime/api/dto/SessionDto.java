package nl.lunatech.jprime.api.dto;

import nl.lunatech.jprime.api.domain.Session;

import java.time.OffsetDateTime;

public record SessionDto(
        Long id,
        String title,
        String abstractText,
        String room,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        boolean cancelled,
        String cancellationReason,
        SpeakerRef speaker
) {
    public static SessionDto of(Session s) {
        return new SessionDto(
                s.id, s.title, s.abstractText, s.room,
                s.startsAt, s.endsAt, s.cancelled, s.cancellationReason,
                SpeakerRef.of(s.speaker));
    }
}
