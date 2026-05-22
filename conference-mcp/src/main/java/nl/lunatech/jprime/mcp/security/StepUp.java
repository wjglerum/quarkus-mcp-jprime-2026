package nl.lunatech.jprime.mcp.security;

import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

@ApplicationScoped
public class StepUp {

    @Inject
    JsonWebToken jwt;

    public void require() {
        Object acr = jwt.getClaim("acr");
        if ("2".equals(String.valueOf(acr))
                || "urn:mace:incommon:iap:silver".equals(String.valueOf(acr))) return;
        Object amr = jwt.getClaim("amr");
        if (amr instanceof Iterable<?> it) {
            for (Object o : it) {
                String s = String.valueOf(o);
                if ("mfa".equals(s) || "otp".equals(s)) return;
            }
        }
        throw new ToolCallException(
                "insufficient_user_authentication: this tool requires step-up MFA. "
                        + "Re-authenticate with acr_values=urn:mace:incommon:iap:silver and retry.");
    }
}
