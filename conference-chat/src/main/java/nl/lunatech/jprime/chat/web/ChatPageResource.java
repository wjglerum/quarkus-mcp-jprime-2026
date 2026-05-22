package nl.lunatech.jprime.chat.web;

import io.quarkus.oidc.IdToken;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.chat.intent.IntentMatcher;
import nl.lunatech.jprime.chat.llm.ProviderRegistry;
import nl.lunatech.jprime.chat.security.JwtClaims;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Path("/")
@Authenticated
public class ChatPageResource {

    @Inject
    @IdToken
    JsonWebToken idToken;

    @Inject
    JsonWebToken accessToken;

    @Inject
    ProviderRegistry providers;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance chat(Me me,
                                                   List<IntentMatcher.QuickPrompt> quickPrompts,
                                                   ProvidersView providers,
                                                   String mode);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        Me me = buildMe();
        ProvidersView pv = buildProvidersView();
        String mode = pv.active();
        return Templates.chat(me, IntentMatcher.quickPrompts(), pv, mode);
    }

    private Me buildMe() {
        String subject = accessToken.getSubject();
        String name = nameClaim();
        Set<String> roles = accessToken.getGroups() == null ? Set.of() : accessToken.getGroups();
        String rolesDisplay = roles.isEmpty() ? "(none)" : String.join(", ", roles);
        String acr = JwtClaims.string(accessToken, "acr", "1");
        String amrDisplay = JwtClaims.joinedStringList(accessToken, "amr", "(none)");
        return new Me(subject, name, roles, rolesDisplay, acr, amrDisplay);
    }

    private ProvidersView buildProvidersView() {
        Map<String, Object> snap = providers.snapshot();
        String active = String.valueOf(snap.get("active"));
        @SuppressWarnings("unchecked")
        List<ProviderRegistry.ProviderInfo> list =
                (List<ProviderRegistry.ProviderInfo>) snap.get("providers");
        return new ProvidersView(active, list);
    }

    private String nameClaim() {
        String name = JwtClaims.string(idToken, "name");
        if (name != null) return name;
        String pref = JwtClaims.string(idToken, "preferred_username");
        if (pref != null) return pref;
        return accessToken.getSubject();
    }

    public record Me(String subject,
                     String name,
                     Set<String> roles,
                     String rolesDisplay,
                     String acr,
                     String amrDisplay) {}

    public record ProvidersView(String active, List<ProviderRegistry.ProviderInfo> providers) {}
}
