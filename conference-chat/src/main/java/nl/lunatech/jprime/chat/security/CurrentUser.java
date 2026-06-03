package nl.lunatech.jprime.chat.security;

import io.quarkus.oidc.IdToken;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Set;

/**
 * The caller's identity, derived once from the OIDC tokens. Shared by the chat page (Qute)
 * and the identity endpoint (JSON) so the claim extraction lives in one place.
 */
@RequestScoped
public class CurrentUser {

    @Inject
    @IdToken
    JsonWebToken idToken;

    @Inject
    JsonWebToken accessToken;

    @Inject
    SecurityIdentity identity;

    public String subject() {
        return JwtClaims.string(accessToken, "preferred_username", accessToken.getSubject());
    }

    public String name() {
        return JwtClaims.displayName(idToken, accessToken);
    }

    public Set<String> roles() {
        return identity.getRoles() == null ? Set.of() : identity.getRoles();
    }

    public String rolesDisplay() {
        Set<String> roles = roles();
        return roles.isEmpty() ? "(none)" : String.join(", ", roles);
    }

    public String acr() {
        return JwtClaims.string(accessToken, "acr", "1");
    }

    public String amrDisplay() {
        return JwtClaims.joinedStringList(accessToken, "amr", "(none)");
    }
}
