package nl.lunatech.jprime.api.dto;

import java.util.List;

public record SpeakerListDto(
        Long id,
        String name,
        String bio,
        List<SessionDto> sessions
) {}
