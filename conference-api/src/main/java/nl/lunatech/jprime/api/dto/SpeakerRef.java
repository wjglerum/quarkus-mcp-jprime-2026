package nl.lunatech.jprime.api.dto;

import nl.lunatech.jprime.api.domain.Speaker;

public record SpeakerRef(Long id, String name) {
    public static SpeakerRef of(Speaker s) {
        return s == null ? null : new SpeakerRef(s.id, s.name);
    }
}
