package nl.lunatech.jprime.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionFeedbackDto(
        SessionDto session,
        long ratingCount,
        double averageStars,
        Map<Integer, Long> distribution,
        List<RatingDto> ratings
) {}
