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
        String acrStr = String.valueOf(acr);
        if ("urn:mace:incommon:iap:silver".equals(acrStr) || "2".equals(acrStr)) {
            return;
        }
        Object amr = jwt.getClaim("amr");
        if (amr instanceof Iterable<?> values) {
            for (Object value : values) {
                String s = String.valueOf(value);
                if ("mfa".equals(s) || "otp".equals(s)) {
                    return;
                }
            }
        }
        throw new ToolCallException(
                "insufficient_user_authentication: this tool requires step-up MFA. "
                        + "Re-authenticate with acr_values=urn:mace:incommon:iap:silver and retry.");
    }
}
