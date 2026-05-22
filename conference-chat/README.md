# conference-chat

The user-facing chat client for the jPrime 2026 "Practical MCP Security in
Action" demo. Runs as a Quarkus app on port **8082** and:

1. Logs the user in via OIDC web-app flow (PKCE S256) against the shared
   Keycloak realm using the `conference-chat` confidential client.
2. Connects to **conference-mcp** at `http://localhost:8081/mcp/sse` over the
   real MCP wire (langchain4j-mcp). Every tool invocation is an MCP call.
3. Propagates the authenticated user's access token on every outbound MCP call
   via `quarkus-langchain4j-oidc-mcp-auth-provider`.
4. Offers two interaction modes:
   - **Scripted** (default, no LLM): the deterministic `IntentMatcher` maps
     the prompt to one MCP tool and arguments.
   - **LLM**: a one-shot LangChain4j chat completion with the MCP tools
     attached as tool specifications. The model picks one tool to call.
5. Lets the speaker hot-swap between LLM providers from the sidebar:
   `scripted`, `anthropic`, `openai`, `ollama`. Availability is computed at
   request time from the configured credentials.

## Running

```shell script
mvn quarkus:dev
```

Quarkus Dev Services boots a shared Keycloak container (`jprime-keycloak`)
imported from `../keycloak-realm.json`. The chat app does not have its own
realm copy.

Hit <http://localhost:8082/> in a browser. You will be redirected to Keycloak.
Demo accounts: `attendee1 / attendee1`, `willem.jan / willem.jan`,
`admin-demo / admin`.

## Environment variables

| Name | Purpose | Default |
|------|---------|---------|
| `CONFERENCE_MCP_URL` | MCP SSE endpoint of conference-mcp. | `http://localhost:8081/mcp/sse` |
| `CHAT_LLM_PROVIDER` | Initial provider on boot: `scripted`, `anthropic`, `openai`, `ollama`. | `scripted` |
| `OLLAMA_MODEL` | Ollama model id used by the `ollama` provider. | `llama3.1:8b` |
| `QUARKUS_LANGCHAIN4J_ANTHROPIC_CLAUDE_API_KEY` | Anthropic API key. | unset |
| `QUARKUS_LANGCHAIN4J_OPENAI_GPT_API_KEY` | OpenAI API key. | unset |
| `DEMO_NOW` | Override the demo clock. | unset |

Setting an API key flips the provider's "available" flag and the sidebar
dropdown enables it.

## Endpoints

- `GET /` serves the chat SPA, redirects to Keycloak when unauthenticated.
- `GET /api/chat/me` current user identity (subject, name, roles, acr, amr).
- `GET /api/chat/quick-prompts` the ten demo-ready prompts with tier hints.
- `GET /api/chat/providers` registered providers, availability, active flag.
- `POST /api/chat/provider` body `{"provider":"scripted|anthropic|openai|ollama"}`,
  switches the active provider at runtime.
- `POST /api/chat/send` body `{"prompt":"...","mode":"scripted|llm"}`,
  returns a `ChatTurn` with the rendered MCP tool call and result.
- `GET /q/health/ready` includes a check that the MCP tool listing succeeded
  (means conference-mcp is reachable on the wire).

## Tests

```shell script
mvn test
```

The test suite covers:

- `IntentMatcherTest` unit tests for each quick prompt mapping.
- `WiringSmokeTest` `@QuarkusTest` smoke test that the bean graph resolves
  and the provider registry reports the scripted provider as available.

Tests run with OIDC, Keycloak Dev Services, and the live MCP wire all
disabled via `%test.` profile overrides.
