package nl.lunatech.jprime.chat.web;

import io.quarkus.oidc.IdToken;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import nl.lunatech.jprime.chat.security.JwtClaims;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Set;

/**
 * Returns the caller's current identity as JSON. The chat fetches this after a step-up popup
 * completes to refresh the acr/amr shown in the sidebar, confirming the session was upgraded
 * to silver without reloading the page (which would lose the transcript).
 */
@Path("/api/me")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class IdentityResource {

    @Inject
    @IdToken
    JsonWebToken idToken;

    @Inject
    JsonWebToken accessToken;

    @Inject
    SecurityIdentity identity;

    @GET
    public Me me() {
        String subject = JwtClaims.string(accessToken, "preferred_username", accessToken.getSubject());
        String name = JwtClaims.displayName(idToken, accessToken);
        Set<String> roles = identity.getRoles() == null ? Set.of() : identity.getRoles();
        String rolesDisplay = roles.isEmpty() ? "(none)" : String.join(", ", roles);
        String acr = JwtClaims.string(accessToken, "acr", "1");
        String amr = JwtClaims.joinedStringList(accessToken, "amr", "(none)");
        return new Me(subject, name, rolesDisplay, acr, amr);
    }

    public record Me(String subject, String name, String roles, String acr, String amr) {}
}
