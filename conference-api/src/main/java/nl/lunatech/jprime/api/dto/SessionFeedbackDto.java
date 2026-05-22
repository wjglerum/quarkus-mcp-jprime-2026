package nl.lunatech.jprime.api.dto;

import nl.lunatech.jprime.api.domain.Rating;
import nl.lunatech.jprime.api.domain.Session;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
