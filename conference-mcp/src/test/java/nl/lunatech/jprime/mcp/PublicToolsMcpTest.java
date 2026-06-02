package nl.lunatech.jprime.mcp;

import io.quarkiverse.mcp.server.ToolResponse;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import nl.lunatech.jprime.mcp.api.PublicConferenceApi;
import nl.lunatech.jprime.mcp.dto.SessionDto;
import nl.lunatech.jprime.mcp.dto.SpeakerRef;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Exercises the public conference tools end to end over the MCP transport with McpAssured.
 * The downstream conference API is mocked so the assertions stay focused on the MCP layer:
 * argument binding, the REST call the tool makes, and the serialized tool result.
 */
@QuarkusTest
class PublicToolsMcpTest {

    @InjectMock
    @RestClient
    PublicConferenceApi api;

    private static final OffsetDateTime START = OffsetDateTime.parse("2026-06-03T10:45:00+03:00");
    private static final OffsetDateTime END = OffsetDateTime.parse("2026-06-03T11:30:00+03:00");

    /** Concatenates the text of every content item in a tool response. */
    private static String allText(ToolResponse response) {
        return response.content().stream()
                .map(content -> content.asText().text())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private static SessionDto session(long id, String title) {
        return new SessionDto(id, title, "An abstract for " + title, "Sofia Hall",
                START, END, false, null, new SpeakerRef(7L, "Ada Lovelace"));
    }

    @Test
    void listSessionsPassesTheQueryThroughAndReturnsMatches() {
        when(api.listSessions(null, null, "MCP"))
                .thenReturn(List.of(session(1L, "MCP Security in Practice")));

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("list_sessions", Map.of("query", "MCP"), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("MCP Security in Practice"));
                    assertTrue(text.contains("Sofia Hall"));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getSessionResolvesByQueryWhenNoIdIsGiven() {
        // The tool first resolves the title to an id via listSessions, then fetches it.
        when(api.listSessions(null, null, "Security"))
                .thenReturn(List.of(session(42L, "MCP Security in Practice")));
        when(api.getSession(42L))
                .thenReturn(session(42L, "MCP Security in Practice"));

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("get_session", Map.of("session_query", "Security"), response -> {
                    assertFalse(response.isError());
                    String text = response.firstContent().asText().text();
                    assertTrue(text.contains("MCP Security in Practice"));
                    assertTrue(text.contains("Sofia Hall"));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void getSessionReturnsAToolErrorWhenTheQueryMatchesNothing() {
        when(api.listSessions(null, null, "does-not-exist"))
                .thenReturn(List.of());

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("get_session", Map.of("session_query", "does-not-exist"), response -> {
                    assertTrue(response.isError());
                    assertTrue(response.firstContent().asText().text().contains("not_found"));
                })
                .thenAssertResults();

        client.disconnect();
    }

    @Test
    void whatsNextForwardsTheLimitToTheConferenceApi() {
        when(api.nextSessions(any(), anyInt()))
                .thenReturn(List.of(session(2L, "Next Up: Loom"), session(3L, "Then: Valhalla")));

        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();

        client.when()
                .toolsCall("whats_next", Map.of("limit", 2), response -> {
                    assertFalse(response.isError());
                    // A list result is returned as one content item per element.
                    String text = allText(response);
                    assertTrue(text.contains("Next Up: Loom"));
                    assertTrue(text.contains("Then: Valhalla"));
                })
                .thenAssertResults();

        // The MCP `limit` argument must be forwarded verbatim to the conference API.
        verify(api).nextSessions(any(), eq(2));

        client.disconnect();
    }
}
