# conference-chat

AI chat client for the jPrime 2026 "Practical MCP Security in Action" demo. A
Quarkus app on port 8082 that:

- Logs the user in via OIDC web-app flow with PKCE S256 (shared Keycloak Dev
  Services realm).
- Connects to `conference-mcp` over `quarkus-langchain4j-mcp` and propagates
  the user's bearer token on every MCP call.
- Routes each prompt through Anthropic Claude: a LangChain4j `ChatModel`
  decides which MCP tool to call, then `conference-chat` executes it against
  `conference-mcp`.
- Renders each turn as `mcp.tool_call` + `mcp.tool_result` cards styled to the
  talk deck (slide 14).

## Run

```shell
./mvnw quarkus:dev
```

The app waits for the shared Keycloak Dev Services container to come up, then
listens on `http://localhost:8082/`. Anonymous requests redirect to Keycloak;
log in as `attendee / attendee` or `willem.jan / willem.jan`. Pre-seeded
clients and users live in the monorepo-root `keycloak-realm.json`.

Set `ANTHROPIC_API_KEY` so the model can run; without it the tool planner
returns an error turn rather than a tool call.

`conference-mcp` (port 8081) and `conference-api` (port 8080) must be running
for tool calls to succeed; the readiness probe at `/q/health/ready` is UP iff
MCP tool discovery returns at least one tool.

## Endpoints

| Path | Auth | Purpose |
|------|------|---------|
| `GET /` | authenticated | Server-rendered chat shell (Qute). |
| `GET /api/chat/me` | authenticated | Current identity claims. |
| `GET /api/chat/quick-prompts` | authenticated | Ten demo-ready prompts with tier. |
| `POST /api/chat/send` | authenticated | Run a prompt; Claude picks an MCP tool and the rendered tool call and result are returned. |
| `GET /q/health/ready` | public | Includes a `mcp-conference` check. |

Static assets at `/chat.css`, `/chat.js` are public.

## Tests

```shell
./mvnw test
```

- `WiringSmokeTest` -- `@QuarkusTest` verifying CDI wiring (the tool
  dispatcher and planner resolve, the single Anthropic `ChatModel` bean
  resolves, and the quick-prompt list is intact).
- `ChatPlaywrightTest` -- `@WithPlaywright` browser test exercising the OIDC
  redirect and the Qute-rendered shell. Best-effort: it requires Chromium,
  which the Playwright driver downloads on first run.

The test profile disables OIDC, Keycloak Dev Services, and the MCP HTTP
client (it spawns a dummy stdio process via `sleep 600` so the
`ToolProvider` bean still resolves) so the unit tests boot in seconds. The
Playwright class re-enables OIDC and Keycloak through a `QuarkusTestProfile`.

## Extensions

| Extension | Purpose |
|-----------|---------|
| `quarkus-rest` + `quarkus-rest-jackson` | REST endpoints under `/api/chat`. |
| `quarkus-oidc` | OIDC web-app flow with PKCE S256. |
| `quarkus-qute` + `quarkus-rest-qute` | Server-rendered chat shell. |
| `quarkus-langchain4j-mcp` | MCP client to `conference-mcp`. |
| `quarkus-oidc-client` | Service-account token for MCP calls outside a user request; the custom `McpAuthProvider` forwards the user's bearer token when one is in flight. |
| `quarkus-langchain4j-anthropic` | Anthropic Claude provider. |
| `quarkus-smallrye-health` | Liveness + readiness. |

The LangChain4j extensions come from the standalone Quarkiverse BOM
(`io.quarkiverse.langchain4j:quarkus-langchain4j-bom:1.10.0`) imported next
to the Quarkus platform BOM.

## Configuration knobs

| Env var | Purpose | Default |
|---------|---------|---------|
| `CONFERENCE_MCP_URL` | MCP endpoint (streamable HTTP). | `http://localhost:8081/mcp` |
| `ANTHROPIC_API_KEY` | Set as `quarkus.langchain4j.anthropic.api-key` to enable Claude. | unset |
| `DEMO_NOW` | Override the demo clock used by `whats_on_now`. | `2026-06-03T10:45:00+03:00` in `%dev` |

## Quarkus guides

- <https://quarkus.io/guides/security-openid-connect-web-authentication>
- <https://quarkus.io/guides/qute>
- <https://docs.quarkiverse.io/quarkus-langchain4j/dev/mcp.html>
- <https://docs.quarkiverse.io/quarkus-langchain4j/dev/anthropic-chat-models.html>
