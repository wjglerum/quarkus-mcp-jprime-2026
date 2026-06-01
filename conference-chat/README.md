# conference-chat

AI chat client for the jPrime 2026 "Practical MCP Security in Action" demo. A
Quarkus app on port 8082 that:

- Logs the user in via OIDC web-app flow with PKCE S256 (shared Keycloak Dev
  Services realm).
- Connects to `conference-mcp` over `quarkus-langchain4j-mcp` and propagates
  the user's bearer token on every MCP call.
- Routes each prompt through one of two paths:
  - **Scripted** (default): deterministic `IntentMatcher` -> MCP tool call.
  - **LLM**: a LangChain4j `ChatModel` decides which MCP tool to call.
- Renders each turn as `mcp.tool_call` + `mcp.tool_result` cards styled to the
  talk deck (slide 14).

## Run

```shell
./mvnw quarkus:dev
```

The app waits for the shared Keycloak Dev Services container to come up, then
listens on `http://localhost:8082/`. Anonymous requests redirect to Keycloak;
log in as `attendee1 / attendee1` or `willem.jan / willem.jan`. Pre-seeded
clients and users live in the monorepo-root `keycloak-realm.json`.

`conference-mcp` (port 8081) and `conference-api` (port 8080) must be running
for tool calls to succeed; the readiness probe at `/q/health/ready` is UP iff
MCP tool discovery returns at least one tool.

## Endpoints

| Path | Auth | Purpose |
|------|------|---------|
| `GET /` | authenticated | Server-rendered chat shell (Qute). |
| `GET /api/chat/me` | authenticated | Current identity claims + provider hint. |
| `GET /api/chat/quick-prompts` | authenticated | Ten demo-ready prompts with tier. |
| `GET /api/chat/providers` | authenticated | Configured LLM providers + availability. |
| `POST /api/chat/provider` | authenticated | Switch active provider (`scripted` / `anthropic` / `openai` / `ollama`). |
| `POST /api/chat/send` | authenticated | Run a prompt; returns the rendered tool call and result. 503 when LLM mode is selected and no provider resolves. |
| `GET /q/health/ready` | public | Includes a `mcp-conference` check. |

Static assets at `/chat.css`, `/chat.js` are public.

## Tests

```shell
./mvnw test
```

- `IntentMatcherTest` -- pure JUnit unit tests for the scripted matcher.
- `WiringSmokeTest` -- `@QuarkusTest` verifying CDI wiring (the scripted
  provider is always available, switching to scripted always succeeds,
  unknown providers are rejected).
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
| `quarkus-langchain4j-oidc-mcp-auth-provider` | Forwards the user's bearer token on MCP calls. |
| `quarkus-langchain4j-anthropic` | Anthropic Claude provider. |
| `quarkus-langchain4j-openai` | OpenAI GPT provider. |
| `quarkus-langchain4j-ollama` | Local Ollama provider (Dev Services). |
| `quarkus-smallrye-health` | Liveness + readiness. |

The LangChain4j extensions come from the standalone Quarkiverse BOM
(`io.quarkiverse.langchain4j:quarkus-langchain4j-bom:1.10.0`) imported next
to the Quarkus platform BOM.

## Configuration knobs

| Env var | Purpose | Default |
|---------|---------|---------|
| `CONFERENCE_MCP_URL` | MCP SSE endpoint. | `http://localhost:8081/mcp/sse` |
| `CHAT_LLM_PROVIDER` | Initial active provider. | `scripted` |
| `ANTHROPIC_API_KEY` | Set as `quarkus.langchain4j.anthropic.claude.api-key` to enable Anthropic. | unset |
| `OPENAI_API_KEY` | Set as `quarkus.langchain4j.openai.gpt.api-key` to enable OpenAI. | unset |
| `OLLAMA_MODEL` | Ollama model id. | `llama3.1:8b` |
| `DEMO_NOW` | Override the demo clock used by `whats_on_now`. | `2026-06-03T10:45:00+03:00` in `%dev` |

## Quarkus guides

- <https://quarkus.io/guides/security-openid-connect-web-authentication>
- <https://quarkus.io/guides/qute>
- <https://docs.quarkiverse.io/quarkus-langchain4j/dev/mcp.html>
- <https://docs.quarkiverse.io/quarkus-langchain4j/dev/anthropic-chat-models.html>
- <https://docs.quarkiverse.io/quarkus-langchain4j/dev/openai-chat-models.html>
- <https://docs.quarkiverse.io/quarkus-langchain4j/dev/ollama.html>
