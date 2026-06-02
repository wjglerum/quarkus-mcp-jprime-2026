package nl.lunatech.jprime.chat.web;

import dev.langchain4j.model.chat.ChatModel;
import io.quarkus.oidc.IdToken;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import nl.lunatech.jprime.chat.llm.LlmToolPlanner;
import nl.lunatech.jprime.chat.security.JwtClaims;
import nl.lunatech.jprime.chat.web.ToolDispatcher.ToolResult;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class ChatResource {

    @Inject
    @IdToken
    JsonWebToken idToken;

    @Inject
    JsonWebToken accessToken;

    @Inject
    ToolDispatcher dispatcher;

    @Inject
    LlmToolPlanner planner;

    @Inject
    ChatModel chatModel;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/me")
    public Map<String, Object> me() {
        Set<String> roles = identity.getRoles() == null ? Set.of() : identity.getRoles();
        return Map.of(
                "subject", JwtClaims.string(accessToken, "preferred_username", accessToken.getSubject()),
                "name", nameClaim(),
                "roles", roles,
                "acr", JwtClaims.string(accessToken, "acr", "1"),
                "amr", JwtClaims.stringList(accessToken, "amr")
        );
    }

    @GET
    @Path("/quick-prompts")
    public List<QuickPrompts.QuickPrompt> quickPrompts() {
        return QuickPrompts.all();
    }

    @POST
    @Path("/send")
    public Response send(ChatRequest req) {
        String prompt = req == null || req.prompt() == null ? "" : req.prompt().trim();
        if (prompt.isEmpty()) {
            return Response.ok(ChatTurn.text("Type a prompt to get started.")).build();
        }

        LlmToolPlanner.Plan plan = planner.plan(chatModel, prompt);
        if (plan.error() != null) {
            return Response.ok(new ChatTurn(prompt, plan.error(), null)).build();
        }
        if (!plan.hasTool()) {
            return Response.ok(new ChatTurn(prompt, plan.note(), null)).build();
        }
        ToolResult result = dispatcher.invoke(plan.tool(), plan.args());
        return Response.ok(new ChatTurn(prompt, plan.note(), result)).build();
    }

    private String nameClaim() {
        String name = JwtClaims.string(idToken, "name");
        if (name != null) return name;
        String pref = JwtClaims.string(idToken, "preferred_username");
        if (pref != null) return pref;
        return accessToken.getSubject();
    }

    public record ChatRequest(String prompt) {}

    public record ChatTurn(String prompt, String note, ToolResult result) {
        public static ChatTurn text(String note) {
            return new ChatTurn(null, note, null);
        }
    }
}
