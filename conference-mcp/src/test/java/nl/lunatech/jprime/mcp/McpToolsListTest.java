package nl.lunatech.jprime.mcp;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Drives the conference MCP server over its streamable HTTP transport with McpAssured and
 * verifies the advertised tool catalogue: the public tools every client can see, plus
 * the role-gated attendee, speaker, and step-up tools. This is the contract the chat
 * client and any external MCP host depend on, so it is worth asserting explicitly.
 */
@QuarkusTest
class McpToolsListTest {

    @Test
    void advertisesThePublicConferenceTools() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsList(page -> {
                    assertNotNull(page.findByName("list_sessions"));
                    assertNotNull(page.findByName("get_session"));
                    assertNotNull(page.findByName("whats_on_now"));
                    assertNotNull(page.findByName("whats_next"));

                    assertTrue(page.findByName("list_sessions").description()
                            .contains("jPrime 2026 conference schedule"));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void advertisesTheRoleGatedTools() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsList(page -> {
                    // Attendee agenda tools.
                    assertNotNull(page.findByName("bookmark_session"));
                    assertNotNull(page.findByName("unbookmark_session"));
                    assertNotNull(page.findByName("my_agenda"));
                    assertNotNull(page.findByName("my_conflicts"));
                    assertNotNull(page.findByName("rate_session"));
                    assertNotNull(page.findByName("my_ratings"));

                    // Speaker tools, including the MFA step-up ones.
                    assertNotNull(page.findByName("my_session_feedback"));
                    assertNotNull(page.findByName("view_session_attendees"));
                    assertNotNull(page.findByName("cancel_my_session"));
                })
                .thenAssertResults();

        client.disconnect();
    }
}
