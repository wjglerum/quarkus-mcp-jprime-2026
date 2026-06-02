package nl.lunatech.jprime.chat.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ToolDispatcher {

    private static final Logger LOG = Logger.getLogger(ToolDispatcher.class);

    private static final Set<String> TOOLS_NEEDING_SESSION_ID = Set.of(
            "bookmark_session", "unbookmark_session", "rate_session",
            "get_session", "view_session_attendees", "cancel_my_session");

    @Inject
    ToolProvider mcpToolProvider;

    @Inject
    ObjectMapper mapper;

    public ToolResult invoke(String tool, Map<String, Object> args) {
        Map<String, Object> safeArgs = args == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(args);

        try {
            ToolProviderResult tools = mcpToolProvider.provideTools(null);

            if (TOOLS_NEEDING_SESSION_ID.contains(tool) && !hasNumeric(safeArgs, "session_id")) {
                Long resolved = resolveSessionId(tools, safeArgs);
                if (resolved == null) {
                    String query = String.valueOf(safeArgs.get("session_query"));
                    return ToolResult.err(tool, safeArgs,
                            "Could not resolve a session from query '" + query + "'.");
                }
                safeArgs.remove("session_query");
                safeArgs.put("session_id", resolved);
            }

            ToolExecutor executor = findExecutor(tools, tool);
            if (executor == null) {
                return ToolResult.err(tool, safeArgs, "Unknown MCP tool: " + tool);
            }

            String argsJson = mapper.writeValueAsString(safeArgs);
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name(tool)
                    .arguments(argsJson)
                    .build();
            String resultText = executor.execute(request, null);
            if (resultText != null && isStepUp(resultText)) {
                return ToolResult.stepUp(tool, safeArgs);
            }
            return ToolResult.ok(tool, safeArgs, parseJsonIfPossible(resultText));
        } catch (Exception e) {
            LOG.warnf(e, "MCP tool call failed: tool=%s", tool);
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            if (isStepUp(msg)) {
                return ToolResult.stepUp(tool, safeArgs);
            }
            return ToolResult.err(tool, safeArgs, e.getClass().getSimpleName() + ": " + msg);
        }
    }

    private Long resolveSessionId(ToolProviderResult tools, Map<String, Object> args) throws Exception {
        Object queryRaw = args.get("session_query");
        if (queryRaw == null) return null;
        String query = queryRaw.toString().trim();
        if (query.isEmpty()) return null;

        if ("current".equalsIgnoreCase(query)) {
            Long viaNow = firstSessionIdFromTool(tools, "whats_on_now", Map.of());
            if (viaNow != null) return viaNow;
        }
        return firstSessionIdFromTool(tools, "list_sessions", Map.of("query", query));
    }

    private Long firstSessionIdFromTool(ToolProviderResult tools, String tool, Map<String, Object> args) throws Exception {
        ToolExecutor executor = findExecutor(tools, tool);
        if (executor == null) return null;
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name(tool)
                .arguments(mapper.writeValueAsString(args))
                .build();
        String body = executor.execute(request, null);
        if (body == null || body.isBlank()) return null;
        Object parsed = parseJsonIfPossible(body);
        return firstId(parsed);
    }

    @SuppressWarnings("unchecked")
    private static Long firstId(Object node) {
        if (node instanceof List<?> list && !list.isEmpty()) {
            return firstId(list.get(0));
        }
        if (node instanceof Map<?, ?> map) {
            Object id = ((Map<String, Object>) map).get("id");
            if (id instanceof Number n) return n.longValue();
            if (id != null) {
                try { return Long.parseLong(id.toString()); } catch (NumberFormatException ignore) { /* fall through */ }
            }
        }
        return null;
    }

    private static ToolExecutor findExecutor(ToolProviderResult tools, String name) {
        for (AiServiceTool t : tools.aiServiceTools()) {
            if (name.equals(t.name())) return t.toolExecutor();
        }
        return null;
    }

    private static boolean hasNumeric(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null) return false;
        if (v instanceof Number) return true;
        try { Long.parseLong(v.toString()); return true; } catch (NumberFormatException e) { return false; }
    }

    private Object parseJsonIfPossible(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        String trimmed = raw.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return raw;
        }
        try {
            // The MCP server emits one content block per list element, which the client
            // concatenates into a single string. Read every JSON value, not just the first,
            // so multi-result tools (e.g. whats_on_now across tracks) keep all their items.
            List<Object> values = mapper.readerFor(Object.class).<Object>readValues(trimmed).readAll();
            if (values.isEmpty()) return raw;
            return values.size() == 1 ? values.get(0) : values;
        } catch (Exception e) {
            return raw;
        }
    }

    private static boolean isStepUp(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return lower.contains("insufficient_user_authentication")
                || lower.contains("step-up")
                || lower.contains("acr_values");
    }

    public record ToolResult(String tool, Map<String, Object> args, Object result,
                              String error, boolean stepUpRequired) {
        public static ToolResult ok(String tool, Map<String, Object> args, Object result) {
            return new ToolResult(tool, copy(args), result, null, false);
        }
        public static ToolResult err(String tool, Map<String, Object> args, String error) {
            return new ToolResult(tool, copy(args), null, error, false);
        }
        public static ToolResult stepUp(String tool, Map<String, Object> args) {
            return new ToolResult(tool, copy(args), null,
                    "insufficient_user_authentication: this tool requires step-up MFA",
                    true);
        }
        private static Map<String, Object> copy(Map<String, Object> a) {
            return a == null ? Map.of() : new LinkedHashMap<>(a);
        }
    }
}
