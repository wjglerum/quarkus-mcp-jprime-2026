package nl.lunatech.jprime.chat.web;

import io.quarkus.oidc.IdToken;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.chat.intent.IntentMatcher;
import nl.lunatech.jprime.chat.intent.IntentMatcher.Intent;
import nl.lunatech.jprime.chat.web.ToolDispatcher.ToolResult;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Path("/api/chat")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class ChatResource {

    @Inject
    @IdToken
    JsonWebToken idToken;

    @Inject
    JsonWebToken accessToken;

    @Inject
    IntentMatcher matcher;

    @Inject
    ToolDispatcher dispatcher;

    @ConfigProperty(name = "chat.llm.enabled", defaultValue = "false")
    boolean llmEnabled;

    @GET
    @Path("/me")
    public Map<String, Object> me() {
        return Map.of(
                "subject", accessToken.getSubject(),
                "name", nameClaim(),
                "roles", accessToken.getGroups(),
                "acr", accessToken.containsClaim("acr") ? accessToken.getClaim("acr") : "1",
                "amr", accessToken.containsClaim("amr") ? accessToken.getClaim("amr") : List.of(),
                "llmAvailable", llmEnabled
        );
    }

    @GET
    @Path("/quick-prompts")
    public List<IntentMatcher.QuickPrompt> quickPrompts() {
        return IntentMatcher.quickPrompts();
    }

    @POST
    @Path("/send")
    public ChatTurn send(ChatRequest req) {
        String prompt = req == null || req.prompt() == null ? "" : req.prompt().trim();
        if (prompt.isEmpty()) {
            return ChatTurn.text("Type a prompt to get started.");
        }
        // LLM mode is intentionally stubbed for now -- the toggle still flips
        // in the UI so the talk can demo "this is what would change". The
        // scripted matcher is the default and works offline.
        Intent intent = matcher.match(prompt);
        if (!intent.matched()) {
            return ChatTurn.text(intent.note());
        }
        ToolResult result = dispatcher.invoke(intent.tool(), intent.args());
        return new ChatTurn(prompt, intent.note(), result, modeLabel(req));
    }

    private String modeLabel(ChatRequest req) {
        if (req == null || req.mode() == null) return "scripted";
        String m = req.mode().toLowerCase();
        if ("llm".equals(m) && llmEnabled) return "llm";
        return "scripted";
    }

    private String nameClaim() {
        String name = idToken.getClaim("name");
        if (name != null && !name.isBlank()) return name;
        String pref = idToken.getClaim("preferred_username");
        if (pref != null && !pref.isBlank()) return pref;
        return accessToken.getSubject();
    }

    public record ChatRequest(String prompt, String mode) {}

    public record ChatTurn(String prompt, String note, ToolResult result, String mode) {
        public static ChatTurn text(String note) {
            return new ChatTurn(null, note, null, "scripted");
        }
    }

    Set<String> roles() {
        return accessToken.getGroups();
    }
}
