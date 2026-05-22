package nl.lunatech.jprime.api.dto;

import nl.lunatech.jprime.api.domain.Attendee;

public record AttendeeDto(
        Long id,
        String subject,
        String displayName,
        boolean isSpeaker,
        Long speakerId
) {
    public static AttendeeDto of(Attendee a) {
        return new AttendeeDto(a.id, a.subject, a.displayName, a.isSpeaker(),
                a.speaker == null ? null : a.speaker.id);
    }
}
