# conference-mcp

Quarkus MCP server that exposes the jPrime 2026 schedule, agenda, and speaker tools to AI clients. Speaks the Model Context Protocol over SSE and propagates the user OAuth token to `conference-api` for every authenticated tool call.

## Run

```bash
./mvnw quarkus:dev
```

Listens on `:8081`. MCP endpoint: `http://localhost:8081/mcp/sse`.

Built on `io.quarkiverse.mcp:quarkus-mcp-server-http` (renamed from `quarkus-mcp-server-sse` in 1.12.x; the SSE endpoint path is unchanged).

The `conference-api` URL is read from `CONFERENCE_API_URL` (default `http://localhost:8080`). In dev mode OIDC is disabled so the inspector can connect without DCR.

## Tools

### Public (any registered MCP client)
- `list_sessions` filter by day/track/query/speaker_name.
- `get_session`
- `whats_on_now` uses the `DEMO_NOW` env var when set so rehearsals are deterministic.
- `whats_next`

### Attendee (requires role `attendee`)
- `bookmark_session`, `unbookmark_session`, `my_agenda`, `my_conflicts`
- `rate_session` the server-side check rejects ratings on sessions that have not started yet.
- `my_ratings`

### Speaker (requires role `speaker`)
- `my_session_feedback`

### Speaker, step-up required (acr=`urn:mace:incommon:iap:silver` or amr=`mfa`/`otp`)
- `view_session_attendees` emits `insufficient_user_authentication` if the token is too weak.
- `cancel_my_session` reversible toggle. Audited.

Tool descriptions are written for an LLM. When you add a tool, optimize the wording so the model can pick it confidently.

## Auth model

1. The MCP client registers with Keycloak via Dynamic Client Registration.
2. The user logs in via the authorization code flow with PKCE.
3. The MCP client sends the access token in `Authorization: Bearer` on the SSE channel.
4. `quarkus-oidc` validates the token and exposes its claims to tool methods.
5. For tools that hit `conference-api`, the `MeConferenceApi` REST client is annotated with `@AccessToken`, so `quarkus-rest-client-oidc-token-propagation` attaches the same bearer token outbound. Defense in depth: `conference-api` re-checks the token against its own audience and re-evaluates roles + acr.

Authorization uses standard Jakarta annotations on tool classes: `@RolesAllowed("attendee")` on `AttendeeTools`, `@RolesAllowed("speaker")` on `SpeakerTools` and `StepUpTools`. The two step-up tools additionally call the small `StepUp.require()` helper to inspect `acr`/`amr` and raise `ToolCallException("insufficient_user_authentication: ...")` when the token is too weak.

## Testing

```bash
./mvnw test
```

`ToolsSmokeTest` verifies the tool beans are wired by CDI. Heavier end-to-end tests are out of scope here; the data API has its own integration coverage.

## Related guides

- [Quarkiverse MCP Server](https://docs.quarkiverse.io/quarkus-mcp-server/dev/)
- [OpenID Connect](https://quarkus.io/guides/security-openid-connect)
- [REST Client OIDC Token Propagation](https://quarkus.io/guides/security-openid-connect-client-reference)
