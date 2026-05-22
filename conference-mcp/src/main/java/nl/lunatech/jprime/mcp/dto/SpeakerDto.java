package nl.lunatech.jprime.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SpeakerDto(
        Long id,
        String name,
        String bio
) {}
