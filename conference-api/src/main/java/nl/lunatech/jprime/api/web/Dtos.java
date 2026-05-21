package nl.lunatech.jprime.api.web;

import nl.lunatech.jprime.api.domain.Attendee;
import nl.lunatech.jprime.api.domain.AuditEvent;
import nl.lunatech.jprime.api.domain.Bookmark;
import nl.lunatech.jprime.api.domain.Level;
import nl.lunatech.jprime.api.domain.Rating;
import nl.lunatech.jprime.api.domain.Session;
import nl.lunatech.jprime.api.domain.Speaker;
import nl.lunatech.jprime.api.domain.Track;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class Dtos {

    private Dtos() {}

    public record SpeakerDto(
            Long id,
            String externalId,
            String name,
            String bio,
            String company,
            String twitterHandle
    ) {
        public static SpeakerDto of(Speaker s) {
            return new SpeakerDto(s.id, s.externalId, s.name, s.bio, s.company, s.twitterHandle);
        }
    }

    public record SpeakerRef(Long id, String name, String company) {
        public static SpeakerRef of(Speaker s) {
            return new SpeakerRef(s.id, s.name, s.company);
        }
    }

    public record SessionDto(
            Long id,
            String externalId,
            String title,
            String abstractText,
            Track track,
            String room,
            Level level,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            int day,
            boolean cancelled,
            String cancellationReason,
            List<SpeakerRef> speakers
    ) {
        public static SessionDto of(Session s) {
            List<SpeakerRef> sp = s.speakers == null
                    ? List.of()
                    : s.speakers.stream().map(SpeakerRef::of).toList();
            return new SessionDto(
                    s.id, s.externalId, s.title, s.abstractText, s.track, s.room, s.level,
                    s.startsAt, s.endsAt, s.day, s.cancelled, s.cancellationReason, sp);
        }
    }

    public record AttendeeDto(
            Long id,
            String subject,
            String displayName,
            String email,
            boolean isSpeaker,
            Long speakerId
    ) {
        public static AttendeeDto of(Attendee a) {
            return new AttendeeDto(a.id, a.subject, a.displayName, a.email, a.isSpeaker,
                    a.speaker == null ? null : a.speaker.id);
        }
    }

    public record BookmarkDto(Long id, Long sessionId, OffsetDateTime createdAt, SessionDto session) {
        public static BookmarkDto of(Bookmark b) {
            return new BookmarkDto(b.id, b.session.id, b.createdAt, SessionDto.of(b.session));
        }
    }

    public record CreateBookmarkRequest(Long sessionId) {}

    public record RatingDto(
            Long id,
            Long sessionId,
            String sessionTitle,
            String attendeeDisplayName,
            int stars,
            String comment,
            OffsetDateTime createdAt
    ) {
        public static RatingDto of(Rating r) {
            return new RatingDto(
                    r.id,
                    r.session.id,
                    r.session.title,
                    r.attendee.displayName,
                    r.stars,
                    r.comment,
                    r.createdAt);
        }
    }

    public record CreateRatingRequest(int stars, String comment) {}

    public record CancelSessionRequest(String reason) {}

    public record RoomDto(String room, SessionDto currentSession, SessionDto nextSession) {}

    public record SessionFeedbackDto(
            SessionDto session,
            long ratingCount,
            double averageStars,
            Map<Integer, Long> distribution,
            List<RatingDto> ratings
    ) {
        public static SessionFeedbackDto of(Session session, List<Rating> ratings) {
            double avg = ratings.stream().mapToInt(r -> r.stars).average().orElse(0.0);
            Map<Integer, Long> dist = ratings.stream()
                    .collect(Collectors.groupingBy(r -> r.stars, Collectors.counting()));
            List<RatingDto> dtos = ratings.stream().map(RatingDto::of).toList();
            return new SessionFeedbackDto(SessionDto.of(session), ratings.size(), avg, dist, dtos);
        }
    }

    public record AttendeeBookmarkDto(
            Long attendeeId,
            String displayName,
            String email,
            OffsetDateTime bookmarkedAt
    ) {}

    public record AuditEventDto(
            Long id,
            String attendeeSubject,
            String action,
            String target,
            String tokenAcr,
            String tokenAmr,
            OffsetDateTime createdAt,
            String detail
    ) {
        public static AuditEventDto of(AuditEvent e) {
            return new AuditEventDto(
                    e.id, e.attendeeSubject, e.action, e.target,
                    e.tokenAcr, e.tokenAmr, e.createdAt, e.detail);
        }
    }

    public static Set<SpeakerRef> refsFor(Set<Speaker> speakers) {
        if (speakers == null) return Set.of();
        return speakers.stream().map(SpeakerRef::of).collect(Collectors.toUnmodifiableSet());
    }
}
