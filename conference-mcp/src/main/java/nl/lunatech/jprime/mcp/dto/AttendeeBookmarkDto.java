package nl.lunatech.jprime.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AttendeeBookmarkDto(
        Long attendeeId,
        String displayName,
        OffsetDateTime bookmarkedAt
) {}
