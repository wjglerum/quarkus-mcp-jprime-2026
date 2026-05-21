package nl.lunatech.jprime.mcp.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class Dtos {

    private Dtos() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpeakerRef(Long id, String name, String company) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionDto(
            Long id,
            String externalId,
            String title,
            String abstractText,
            String track,
            String room,
            String level,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            int day,
            boolean cancelled,
            String cancellationReason,
            List<SpeakerRef> speakers
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpeakerDto(
            Long id,
            String externalId,
            String name,
            String bio,
            String company,
            String twitterHandle
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookmarkDto(Long id, Long sessionId, OffsetDateTime createdAt, SessionDto session) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateBookmarkRequest(Long sessionId) {}

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CreateRatingRequest(int stars, String comment) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CancelSessionRequest(String reason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SessionFeedbackDto(
            SessionDto session,
            long ratingCount,
            double averageStars,
            Map<Integer, Long> distribution,
            List<RatingDto> ratings
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AttendeeBookmarkDto(
            Long attendeeId,
            String displayName,
            String email,
            OffsetDateTime bookmarkedAt
    ) {}
}
