package nl.lunatech.jprime.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionDto(
        Long id,
        String title,
        String abstractText,
        String room,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        boolean cancelled,
        String cancellationReason,
        List<SpeakerRef> speakers
) {}
