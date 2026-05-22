package nl.lunatech.jprime.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookmarkDto(Long id, Long sessionId, OffsetDateTime createdAt, SessionDto session) {}
