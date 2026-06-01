package nl.lunatech.jprime.api.dto;

import nl.lunatech.jprime.api.domain.Bookmark;

import java.time.OffsetDateTime;

public record AttendeeBookmarkDto(
        Long attendeeId,
        String displayName,
        OffsetDateTime bookmarkedAt
) {
    public static AttendeeBookmarkDto of(Bookmark b) {
        return new AttendeeBookmarkDto(b.attendee.id, b.attendee.displayName, b.createdAt);
    }
}
