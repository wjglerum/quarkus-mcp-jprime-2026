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
        // "2" covers a realm without the acr.loa.map alias
        if ("urn:jprime:mfa".equals(acrStr) || "2".equals(acrStr)) {
            return;
        }
        throw new ToolCallException(
                "insufficient_user_authentication: this tool requires step-up MFA. "
                        + "Re-authenticate with acr_values=urn:jprime:mfa and retry.");
    }
}
