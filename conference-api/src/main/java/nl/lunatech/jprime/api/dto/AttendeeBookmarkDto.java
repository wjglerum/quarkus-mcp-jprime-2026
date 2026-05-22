package nl.lunatech.jprime.api.dto;

import java.time.OffsetDateTime;

public record AttendeeBookmarkDto(
        Long attendeeId,
        String displayName,
        OffsetDateTime bookmarkedAt
) {}
