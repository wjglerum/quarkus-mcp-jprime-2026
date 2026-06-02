package nl.lunatech.jprime.chat.web;

import io.quarkus.oidc.IdToken;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.chat.security.JwtClaims;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
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
    SecurityIdentity identity;

    @CheckedTemplate
    public static class Templates {
        public static native TemplateInstance chat(Me me, List<QuickPrompts.QuickPrompt> quickPrompts);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index() {
        return Templates.chat(buildMe(), QuickPrompts.all());
    }

    private Me buildMe() {
        String subject = JwtClaims.string(accessToken, "preferred_username", accessToken.getSubject());
        String name = JwtClaims.displayName(idToken, accessToken);
        Set<String> roles = identity.getRoles() == null ? Set.of() : identity.getRoles();
        String rolesDisplay = roles.isEmpty() ? "(none)" : String.join(", ", roles);
        String acr = JwtClaims.string(accessToken, "acr", "1");
        String amrDisplay = JwtClaims.joinedStringList(accessToken, "amr", "(none)");
        return new Me(subject, name, roles, rolesDisplay, acr, amrDisplay);
    }

    public record Me(String subject,
                     String name,
                     Set<String> roles,
                     String rolesDisplay,
                     String acr,
                     String amrDisplay) {}
}
