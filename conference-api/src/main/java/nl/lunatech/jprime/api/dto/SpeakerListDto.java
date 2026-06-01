package nl.lunatech.jprime.api.dto;

import nl.lunatech.jprime.api.domain.Speaker;

import java.util.List;

public record SpeakerListDto(
        Long id,
        String name,
        String bio,
        List<SessionDto> sessions
) {
    public static SpeakerListDto of(Speaker s, List<SessionDto> sessions) {
        return new SpeakerListDto(s.id, s.name, s.bio, sessions);
    }
}
