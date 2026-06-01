package nl.lunatech.jprime.chat.web;

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
import nl.lunatech.jprime.chat.intent.IntentMatcher;
import nl.lunatech.jprime.chat.intent.IntentMatcher.Intent;
import nl.lunatech.jprime.chat.llm.LlmToolPlanner;
import nl.lunatech.jprime.chat.llm.ProviderRegistry;
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
    IntentMatcher matcher;

    @Inject
    ToolDispatcher dispatcher;

    @Inject
    ProviderRegistry providers;

    @Inject
    LlmToolPlanner planner;

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
                "amr", JwtClaims.stringList(accessToken, "amr"),
                "provider", providers.activeProvider(),
                "llmAvailable", providers.isLlmActive()
        );
    }

    @GET
    @Path("/quick-prompts")
    public List<IntentMatcher.QuickPrompt> quickPrompts() {
        return IntentMatcher.quickPrompts();
    }

    @GET
    @Path("/providers")
    public Map<String, Object> listProviders() {
        return providers.snapshot();
    }

    @POST
    @Path("/provider")
    public Response setProvider(ProviderSwitchRequest req) {
        if (req == null || req.provider() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", "provider field required")).build();
        }
        ProviderRegistry.SwitchResult r = providers.setActive(req.provider());
        if (!r.ok()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", r.error(), "active", r.active())).build();
        }
        return Response.ok(providers.snapshot()).build();
    }

    @POST
    @Path("/send")
    public Response send(ChatRequest req) {
        String prompt = req == null || req.prompt() == null ? "" : req.prompt().trim();
        if (prompt.isEmpty()) {
            return Response.ok(ChatTurn.text("Type a prompt to get started.", providers.activeProvider())).build();
        }

        if ("llm".equalsIgnoreCase(req == null ? null : req.mode())) {
            var chat = providers.activeChatModel();
            if (chat.isEmpty()) {
                return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                        .entity(Map.of("error", "provider_unavailable", "active", providers.activeProvider()))
                        .build();
            }
            return Response.ok(runLlm(prompt, chat.get())).build();
        }
        return Response.ok(runScripted(prompt)).build();
    }

    private ChatTurn runScripted(String prompt) {
        Intent intent = matcher.match(prompt);
        if (!intent.matched()) {
            return ChatTurn.text(intent.note(), "scripted");
        }
        ToolResult result = dispatcher.invoke(intent.tool(), intent.args());
        return new ChatTurn(prompt, intent.note(), result, "scripted");
    }

    private ChatTurn runLlm(String prompt, dev.langchain4j.model.chat.ChatModel chat) {
        LlmToolPlanner.Plan plan = planner.plan(chat, prompt);
        if (plan.error() != null) {
            return new ChatTurn(prompt, plan.error(), null, "llm");
        }
        if (!plan.hasTool()) {
            return new ChatTurn(prompt, plan.note(), null, "llm");
        }
        ToolResult result = dispatcher.invoke(plan.tool(), plan.args());
        return new ChatTurn(prompt, plan.note(), result, "llm");
    }

    private String nameClaim() {
        String name = JwtClaims.string(idToken, "name");
        if (name != null) return name;
        String pref = JwtClaims.string(idToken, "preferred_username");
        if (pref != null) return pref;
        return accessToken.getSubject();
    }

    public record ChatRequest(String prompt, String mode) {}

    public record ProviderSwitchRequest(String provider) {}

    public record ChatTurn(String prompt, String note, ToolResult result, String mode) {
        public static ChatTurn text(String note, String mode) {
            return new ChatTurn(null, note, null, mode);
        }
    }
}
