package nl.lunatech.jprime.chat.web;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.chat.llm.LlmToolPlanner;
import nl.lunatech.jprime.chat.web.ToolDispatcher.ToolResult;

@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ChatResource {

    @Inject
    ToolDispatcher dispatcher;

    @Inject
    LlmToolPlanner planner;

    @Inject
    ChatModel chatModel;

    @POST
    @Path("/send")
    public ChatTurn send(ChatRequest req) {
        String prompt = req == null || req.prompt() == null ? "" : req.prompt().trim();
        if (prompt.isEmpty()) {
            return ChatTurn.text("Type a prompt to get started.");
        }

        LlmToolPlanner.Plan plan = planner.plan(chatModel, prompt);
        if (plan.error() != null) {
            return new ChatTurn(prompt, plan.error(), null);
        }
        if (!plan.hasTool()) {
            return new ChatTurn(prompt, plan.note(), null);
        }
        ToolResult result = dispatcher.invoke(plan.tool(), plan.args());
        return new ChatTurn(prompt, plan.note(), result);
    }

    public record ChatRequest(String prompt) {}

    public record ChatTurn(String prompt, String note, ToolResult result) {
        public static ChatTurn text(String note) {
            return new ChatTurn(null, note, null);
        }
    }
}
