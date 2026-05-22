package nl.lunatech.jprime.chat.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LlmToolPlanner {

    private static final Logger LOG = Logger.getLogger(LlmToolPlanner.class);

    private static final String SYSTEM_PROMPT = """
            You are the jPrime 2026 conference companion. You help the speaker
            and the audience on stage. You only invoke the tools provided to
            you, which come from the conference MCP server. Pick exactly one
            tool that matches the user's request and call it with the right
            arguments. If no tool matches, reply briefly that you do not have
            a tool for this and stop. Never invent tools, never call multiple
            tools in one turn.
            """;

    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    ToolProvider mcpToolProvider;

    public Plan plan(ChatModel model, String userPrompt) {
        try {
            ToolProviderResult tools = mcpToolProvider.provideTools(null);
            List<dev.langchain4j.agent.tool.ToolSpecification> specs = tools.aiServiceTools().stream()
                    .map(AiServiceTool::toolSpecification)
                    .toList();
            ChatRequest request = ChatRequest.builder()
                    .messages(List.of(SystemMessage.from(SYSTEM_PROMPT), UserMessage.from(userPrompt)))
                    .parameters(ChatRequestParameters.builder().toolSpecifications(specs).build())
                    .build();
            ChatResponse response = model.chat(request);
            AiMessage reply = response.aiMessage();
            if (reply.hasToolExecutionRequests() && !reply.toolExecutionRequests().isEmpty()) {
                ToolExecutionRequest call = reply.toolExecutionRequests().get(0);
                Map<String, Object> args = parseArgs(call.arguments());
                return Plan.tool(call.name(), args, "LLM picked tool " + call.name() + ".");
            }
            String text = reply.text();
            return Plan.text(text == null || text.isBlank()
                    ? "The model did not pick any tool for this prompt."
                    : text);
        } catch (Exception e) {
            LOG.warnf(e, "LLM planning failed");
            return Plan.error("LLM call failed: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) return Map.of();
        try {
            Object o = mapper.readValue(argsJson, Object.class);
            if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        } catch (Exception e) {
            LOG.warnf("Could not parse LLM tool args as JSON: %s", argsJson);
        }
        return Map.of();
    }

    public record Plan(String tool, Map<String, Object> args, String note, String error) {
        public static Plan tool(String t, Map<String, Object> a, String n) { return new Plan(t, a, n, null); }
        public static Plan text(String n) { return new Plan(null, Map.of(), n, null); }
        public static Plan error(String err) { return new Plan(null, Map.of(), null, err); }
        public boolean hasTool() { return tool != null; }
    }
}
