package nl.lunatech.jprime.chat.security;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableInstance;
import io.quarkus.arc.ManagedContext;
import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.runtime.TokensHelper;
import io.quarkus.security.credential.TokenCredential;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;

/**
 * Supplies the bearer token for outbound MCP calls to conference-mcp, whose endpoint requires
 * the attendee scope.
 *
 * <p>When a user request is in flight, the user's access token is propagated so the action is
 * recorded and audited under their identity. Outside any request, for example the readiness
 * probe that lists the MCP tools, there is no user token, so a service-account token from the
 * client-credentials grant is used instead. The service account carries the attendee role in
 * the realm, so it satisfies the endpoint gate.
 */
@ApplicationScoped
public class McpAuthProvider implements McpClientAuthProvider {

    @Inject
    OidcClient oidcClient;

    // Caches the service-account token and only re-runs the grant when it is about to expire.
    private final TokensHelper tokensHelper = new TokensHelper();

    @Override
    public String getAuthorization(Input input) {
        String userToken = currentUserToken();
        if (userToken != null) {
            return "Bearer " + userToken;
        }
        return "Bearer " + tokensHelper.getTokens(oidcClient)
                .await().atMost(Duration.ofSeconds(10))
                .getAccessToken();
    }

    private static String currentUserToken() {
        ManagedContext requestContext = Arc.container().requestContext();
        if (!requestContext.isActive()) {
            return null;
        }
        InjectableInstance<TokenCredential> credential = Arc.container().select(TokenCredential.class);
        return credential.isResolvable() ? credential.get().getToken() : null;
    }
}
