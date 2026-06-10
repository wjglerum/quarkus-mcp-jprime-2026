package nl.lunatech.jprime.mcp;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import io.vertx.core.MultiMap;

import java.util.Set;

/**
 * Builds streamable MCP test clients that pass the HTTP-layer endpoint gate with a
 * wiremock-issued bearer token for the given user and realm roles.
 */
final class McpTestClients {

    private McpTestClients() {
    }

    static McpStreamableTestClient.Builder authenticatedClient(String user, Set<String> roles) {
        String token = OidcWiremockTestResource.getAccessToken(user, roles);
        return McpAssured.newStreamableClient()
                .setAdditionalHeaders(message -> MultiMap.caseInsensitiveMultiMap()
                        .add("Authorization", "Bearer " + token));
    }

    static McpStreamableTestClient connectAs(String user, Set<String> roles) {
        return authenticatedClient(user, roles).build().connect();
    }
}
