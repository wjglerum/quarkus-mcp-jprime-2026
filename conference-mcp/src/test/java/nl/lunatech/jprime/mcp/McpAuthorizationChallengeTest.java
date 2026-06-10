package nl.lunatech.jprime.mcp;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the authorization challenges emitted by the {@code quarkus-mcp-server-oidc}
 * extension once the MCP endpoint is gated at the HTTP layer.
 *
 * <ul>
 *   <li>No token: a 401 whose {@code WWW-Authenticate} header carries the
 *       {@code resource_metadata} pointer (RFC 9728) clients follow to discover the
 *       authorization server.</li>
 *   <li>Valid token without the required scope: a 403 with a spec-compliant
 *       {@code error="insufficient_scope"} challenge listing the required scope and the
 *       resource metadata URL.</li>
 *   <li>Valid token with the scope: the connection succeeds and tools are listed.</li>
 * </ul>
 */
@QuarkusTest
@QuarkusTestResource(OidcWiremockTestResource.class)
class McpAuthorizationChallengeTest {

    @Test
    void unauthenticatedRequestIsChallengedWithResourceMetadata() {
        McpAssured.newStreamableClient()
                .setExpectConnectFailure(response -> {
                    assertEquals(401, response.statusCode());
                    String wwwAuthenticate = response.firstHeader("www-authenticate");
                    assertNotNull(wwwAuthenticate, "expected a WWW-Authenticate challenge");
                    assertTrue(wwwAuthenticate.contains("resource_metadata="),
                            "expected a resource_metadata pointer, got: " + wwwAuthenticate);
                })
                .build()
                .connect();
    }

    @Test
    void authenticatedRequestWithoutScopeGetsInsufficientScopeChallenge() {
        // A valid token, but with no roles, so the attendee scope is missing.
        McpTestClients.authenticatedClient("scopeless", Set.of())
                .setExpectConnectFailure(response -> {
                    assertEquals(403, response.statusCode());
                    String wwwAuthenticate = response.firstHeader("www-authenticate");
                    assertNotNull(wwwAuthenticate, "expected a WWW-Authenticate challenge");
                    assertTrue(wwwAuthenticate.contains("error=\"insufficient_scope\""),
                            "expected an insufficient_scope error, got: " + wwwAuthenticate);
                    assertTrue(wwwAuthenticate.contains("scope=\"attendee\""),
                            "expected the required scope, got: " + wwwAuthenticate);
                    assertTrue(wwwAuthenticate.contains("resource_metadata="),
                            "expected a resource_metadata pointer, got: " + wwwAuthenticate);
                })
                .build()
                .connect();
    }

    @Test
    void attendeeTokenCanConnectAndListTools() {
        McpStreamableTestClient client = McpTestClients.connectAs("attendee", Set.of("attendee"));

        client.when()
                .toolsList(page -> assertNotNull(page.findByName("list_sessions")))
                .thenAssertResults();

        client.disconnect();
    }
}
