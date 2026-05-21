package nl.lunatech.jprime.chat.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import nl.lunatech.jprime.chat.client.Dtos.AttendeeBookmarkDto;
import nl.lunatech.jprime.chat.client.Dtos.BookmarkDto;
import nl.lunatech.jprime.chat.client.Dtos.CancelSessionRequest;
import nl.lunatech.jprime.chat.client.Dtos.CreateBookmarkRequest;
import nl.lunatech.jprime.chat.client.Dtos.CreateRatingRequest;
import nl.lunatech.jprime.chat.client.Dtos.RatingDto;
import nl.lunatech.jprime.chat.client.Dtos.SessionDto;
import nl.lunatech.jprime.chat.client.Dtos.SessionFeedbackDto;
import nl.lunatech.jprime.chat.client.Dtos.SpeakerDto;
import nl.lunatech.jprime.chat.client.MeConferenceApi;
import nl.lunatech.jprime.chat.client.PublicConferenceApi;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Executes the MCP-shaped tool call described by an {@code Intent} against
 * conference-api. The chat UI renders the outbound and inbound payloads as if
 * they were MCP wire messages -- because they map 1:1 to the tools exposed by
 * conference-mcp.
 */
@ApplicationScoped
public class ToolDispatcher {

    @Inject
    @RestClient
    PublicConferenceApi publicApi;

    @Inject
    @RestClient
    MeConferenceApi me;

    @ConfigProperty(name = "demo.now")
    Optional<String> demoNow;

    public ToolResult invoke(String tool, Map<String, Object> args) {
        try {
            return switch (tool) {
                case "list_sessions" -> {
                    String q = asString(args.get("query"));
                    Integer day = asInt(args.get("day"));
                    String track = asString(args.get("track"));
                    yield ToolResult.ok(tool, args, publicApi.listSessions(day, track, null, null, q));
                }
                case "get_session" -> ToolResult.ok(tool, args,
                        publicApi.getSession(requireLong(args, "session_id")));
                case "whats_on_now" -> ToolResult.ok(tool, args,
                        publicApi.currentSessions(demoNow.orElse(null)));
                case "whats_next" -> ToolResult.ok(tool, args,
                        publicApi.nextSessions(demoNow.orElse(null), asInt(args.get("limit"))));
                case "find_speaker" -> {
                    String name = asString(args.get("name"));
                    SpeakerDto match = publicApi.listSpeakers().stream()
                            .filter(s -> s.name() != null
                                    && s.name().toLowerCase(Locale.ENGLISH)
                                          .contains(name.toLowerCase(Locale.ENGLISH)))
                            .findFirst().orElse(null);
                    if (match == null) yield ToolResult.err(tool, args, "No speaker matching " + name);
                    yield ToolResult.ok(tool, args, Map.of(
                            "speaker", match,
                            "sessions", publicApi.speakerSessions(match.id())));
                }
                case "bookmark_session" -> {
                    Long sid = resolveSessionId(args);
                    BookmarkDto bm = me.addBookmark(new CreateBookmarkRequest(sid));
                    yield ToolResult.ok(tool, withResolved(args, sid), bm);
                }
                case "unbookmark_session" -> {
                    Long sid = resolveSessionId(args);
                    try (Response r = me.removeBookmark(sid)) {
                        yield ToolResult.ok(tool, withResolved(args, sid),
                                Map.of("ok", r.getStatus() < 300));
                    }
                }
                case "my_agenda" -> ToolResult.ok(tool, args, me.myAgenda());
                case "my_conflicts" -> ToolResult.ok(tool, args, me.conflicts());
                case "my_ratings" -> ToolResult.ok(tool, args, me.myRatings());
                case "my_session_feedback" -> {
                    List<SessionFeedbackDto> fb = me.mySessionFeedback();
                    yield ToolResult.ok(tool, args, fb);
                }
                case "rate_session" -> {
                    Long sid = resolveSessionId(args);
                    int stars = asInt(args.get("stars")) == null ? 5 : asInt(args.get("stars"));
                    String comment = asString(args.get("comment"));
                    try (Response r = me.rateSession(sid, new CreateRatingRequest(stars, comment))) {
                        if (r.getStatus() == 422) {
                            yield ToolResult.err(tool, withResolved(args, sid),
                                    "Server rejected: " + r.readEntity(String.class));
                        }
                        RatingDto rating = r.readEntity(RatingDto.class);
                        yield ToolResult.ok(tool, withResolved(args, sid), rating);
                    }
                }
                case "view_session_attendees" -> {
                    Long sid = resolveSessionId(args);
                    try (Response r = me.sessionAttendees(sid)) {
                        if (r.getStatus() == 401) {
                            yield ToolResult.stepUp(tool, withResolved(args, sid));
                        }
                        if (r.getStatus() >= 400) {
                            yield ToolResult.err(tool, withResolved(args, sid),
                                    "Backend error " + r.getStatus());
                        }
                        List<AttendeeBookmarkDto> attendees = r.readEntity(
                                new jakarta.ws.rs.core.GenericType<List<AttendeeBookmarkDto>>() {});
                        yield ToolResult.ok(tool, withResolved(args, sid), attendees);
                    }
                }
                case "cancel_my_session" -> {
                    Long sid = resolveSessionId(args);
                    String reason = asString(args.get("reason"));
                    try {
                        SessionDto s = me.cancelSession(sid,
                                new CancelSessionRequest(reason == null ? "no reason given" : reason));
                        yield ToolResult.ok(tool, withResolved(args, sid), s);
                    } catch (WebApplicationException wae) {
                        if (wae.getResponse().getStatus() == 401) {
                            yield ToolResult.stepUp(tool, withResolved(args, sid));
                        }
                        throw wae;
                    }
                }
                default -> ToolResult.err(tool, args, "Unknown tool: " + tool);
            };
        } catch (WebApplicationException wae) {
            int st = wae.getResponse().getStatus();
            if (st == 401) return ToolResult.stepUp(tool, args);
            return ToolResult.err(tool, args, "HTTP " + st + " from conference-api");
        } catch (Exception e) {
            return ToolResult.err(tool, args, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Long resolveSessionId(Map<String, Object> args) {
        Long explicit = asLong(args.get("session_id"));
        if (explicit != null) return explicit;
        String query = asString(args.get("session_query"));
        if (query == null || query.isBlank()) {
            throw new WebApplicationException("session_query or session_id required", 400);
        }
        if ("current".equalsIgnoreCase(query)) {
            List<SessionDto> now = publicApi.currentSessions(demoNow.orElse(null));
            if (!now.isEmpty()) return now.get(0).id();
        }
        List<SessionDto> matches = publicApi.listSessions(null, null, null, null, query);
        if (matches.isEmpty()) {
            throw new WebApplicationException("No session matched '" + query + "'", 404);
        }
        return matches.get(0).id();
    }

    private static Map<String, Object> withResolved(Map<String, Object> args, Long sid) {
        java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>(args);
        copy.put("session_id", sid);
        return copy;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Integer asInt(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(o.toString()); } catch (Exception e) { return null; }
    }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return null; }
    }

    private static Long requireLong(Map<String, Object> args, String key) {
        Long v = asLong(args.get(key));
        if (v == null) throw new WebApplicationException(key + " is required", 400);
        return v;
    }

    public record ToolResult(String tool, Map<String, Object> args, Object result,
                              String error, boolean stepUpRequired) {
        public static ToolResult ok(String tool, Map<String, Object> args, Object result) {
            return new ToolResult(tool, args, result, null, false);
        }
        public static ToolResult err(String tool, Map<String, Object> args, String error) {
            return new ToolResult(tool, args, null, error, false);
        }
        public static ToolResult stepUp(String tool, Map<String, Object> args) {
            return new ToolResult(tool, args, null,
                    "insufficient_user_authentication: this tool requires step-up MFA",
                    true);
        }
    }
}
