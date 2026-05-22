package nl.lunatech.jprime.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RatingDto(
        Long id,
        Long sessionId,
        String sessionTitle,
        String attendeeDisplayName,
        int stars,
        String comment,
        OffsetDateTime createdAt
) {}
