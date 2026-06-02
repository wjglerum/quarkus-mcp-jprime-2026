package nl.lunatech.jprime.chat.security;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import io.quarkus.arc.Unremovable;
import io.quarkus.oidc.OidcRedirectFilter;
import io.quarkus.oidc.Redirect;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Logs the PKCE parameters Quarkus attaches to the outbound authorization-code redirect, so the
 * proof key for code exchange is visible on stage instead of flashing past in the URL bar. By the
 * time a redirect filter runs, Quarkus has already built the full authorization URL including
 * {@code code_challenge} and {@code code_challenge_method=S256} (PKCE is enabled via
 * {@code quarkus.oidc.authentication.pkce-required=true}), so we just read them back off the
 * redirect URI and print them. Read-only: this filter never mutates the request.
 */
@ApplicationScoped
@Unremovable
@Redirect(Redirect.Location.OIDC_AUTHORIZATION)
public class PkceLogFilter implements OidcRedirectFilter {

    private static final Logger LOG = Logger.getLogger(PkceLogFilter.class);

    @Override
    public void filter(OidcRedirectFilter.OidcRedirectContext context) {
        Map<String, String> query = parseQuery(context.redirectUri());
        String challenge = query.get("code_challenge");
        if (challenge == null) {
            return;
        }
        LOG.infof("PKCE on outbound authorization request: code_challenge_method=%s code_challenge=%s",
                query.getOrDefault("code_challenge_method", "(none)"), challenge);
    }

    private static Map<String, String> parseQuery(String redirectUri) {
        Map<String, String> params = new LinkedHashMap<>();
        if (redirectUri == null) {
            return params;
        }
        int q = redirectUri.indexOf('?');
        if (q < 0) {
            return params;
        }
        for (String pair : redirectUri.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }
}
