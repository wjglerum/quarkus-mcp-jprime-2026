package nl.lunatech.jprime.mcp.security;

import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
public class McpSecurity {

    @Inject
    SecurityIdentity identity;

    @Inject
    JsonWebToken jwt;

    public void requireAuthenticated() {
        if (identity == null || identity.isAnonymous()) {
            throw new ToolCallException(
                    "authentication_required: this tool requires the user to be logged in via OAuth");
        }
    }

    public void requireRole(String role) {
        requireAuthenticated();
        if (!identity.getRoles().contains(role)) {
            throw new ToolCallException(
                    "insufficient_scope: this tool requires the '" + role + "' role");
        }
    }

    public void requireStepUp() {
        requireAuthenticated();
        Object acr = jwt.getClaim("acr");
        if (acr != null && ("2".equals(acr.toString())
                || "urn:mace:incommon:iap:silver".equals(acr.toString()))) {
            return;
        }
        Object amr = jwt.getClaim("amr");
        if (amr instanceof Iterable<?> it) {
            for (Object o : it) {
                if ("mfa".equals(String.valueOf(o)) || "otp".equals(String.valueOf(o))) {
                    return;
                }
            }
        }
        throw new ToolCallException(
                "insufficient_user_authentication: this tool requires recent MFA-backed "
                        + "authentication (acr=urn:mace:incommon:iap:silver). "
                        + "Please re-authenticate with a higher acr value before retrying.");
    }
}
