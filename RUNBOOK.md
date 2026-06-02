# Runbook: Practical MCP Security in Action

The stage-side runbook for the three live demos at jPrime 2026.

## Stage assumptions

- Laptop with Docker (for Dev Services), Java 25, Maven.
- Three terminal windows: `conference-api` (:8080), `conference-mcp` (:8081), `conference-chat` (:8082).
- Second monitor showing the **live audit dashboard** at `http://localhost:8080/audit-live/`.
- One browser window on the chat client at `http://localhost:8082/`.
- One backup video per demo at 1.5x speed in case the live run misbehaves.

## Pre-flight: step-up demo prerequisite

`willem.jan` is **not** TOTP-enrolled in the seeded realm. The step-up demo surfaces as a 401 + `WWW-Authenticate: Bearer error="insufficient_user_authentication"` response from `conference-api` and a `ToolCallException("insufficient_user_authentication: ...")` from `conference-mcp`. Show that response on the second screen.

Do **not** attempt to complete TOTP on stage unless you have enrolled it manually via the Keycloak account console first. That enrollment is a manual pre-flight step the speaker performs ahead of the talk, not something the realm ships.

## Env vars

| Variable | Purpose | Demo default |
|----------|---------|--------------|
| `DEMO_NOW` | Override the "current time" used by `whats_on_now`, `whats_next`, and the rating cutoff. | `2026-06-03T10:45:00+03:00` |

`OIDC_AUTH_SERVER_URL`, `CONFERENCE_API_URL`, `CONFERENCE_MCP_URL`, and database credentials are only needed in `%prod`. In dev mode Quarkus Dev Services wires everything automatically.

## Boot order

```bash
# Terminal 1 - starts Postgres + Keycloak Dev Services on first boot
cd conference-api && DEMO_NOW=2026-06-03T10:45:00+03:00 ./mvnw quarkus:dev

# Terminal 2 - joins the shared Keycloak container
cd conference-mcp && DEMO_NOW=2026-06-03T10:45:00+03:00 ./mvnw quarkus:dev

# Terminal 3 - chat client, joins the same Keycloak container
cd conference-chat && DEMO_NOW=2026-06-03T10:45:00+03:00 ./mvnw quarkus:dev
```

The first start pulls the Postgres and Keycloak images. Allow 30 to 60 seconds on a fresh machine; subsequent restarts reuse the containers.

Open `http://localhost:8080/audit-live/` on the second monitor.

## Pre-flight check (before going on stage)

1. `curl http://localhost:8080/api/v1/sessions | jq '.[0]'` returns a real session.
2. The audit dashboard shows "Waiting for the first event..." (clean state) or a recent rehearsal event.
3. Open the Dev UI at `http://localhost:8080/q/dev/` and click the **Keycloak** tile. Confirm realm `jprime` is imported with `attendee`, `willem.jan`, and `admin-demo`.
4. From the Dev UI Keycloak tile, copy the realm URL. Paste `http://<dev-keycloak-url>/.well-known/openid-configuration` into a browser tab and confirm 200.
5. Hit `http://localhost:8082/` in a browser; log in as `attendee / attendee`. The Qute shell renders with the quick prompts.

## MCP Inspector and Dynamic Client Registration (DCR)

Point MCP Inspector at the streamable HTTP endpoint `http://localhost:8081/mcp` (not `/mcp/sse`; the SSE transport is deprecated in current Inspector builds). Use the **Guided** or **Quick OAuth Flow** in Inspector's Auth panel. The flow runs entirely off discovery: Inspector reads `/.well-known/oauth-protected-resource` from `conference-mcp`, finds the Keycloak realm, registers a fresh client via DCR, then runs PKCE and sends you to the Keycloak login.

The `jprime` realm is deliberately configured for **open anonymous DCR** so Inspector can register with no pre-shared credentials. Keycloak ships four anonymous client-registration policies that each block or cripple a DCR client; all four have been cleared in `keycloak-realm.json`:

| Removed/changed policy | Why it had to go |
|------------------------|------------------|
| Allowed Client Scopes | Rejected the `openid` scope Inspector always requests. |
| Trusted Hosts | Rejected Inspector's hardcoded `github.com` `client_uri`, and the Docker gateway sender IP. |
| Full Scope Disabled | Set `fullScopeAllowed=false`, stripping realm roles from the token (caused 403 on tool calls). |
| Consent Required | Forced a consent prompt on every freshly registered client. |

On top of that, the `basic` client scope carries `realm-roles` and `preferred_username` mappers. DCR clients only retain the `basic` scope, so without these the token would be claimless: no roles (tool authorization fails) and no username (audit events attributed to the `sub` UUID).

Talking point: **"the AI got a token" is not "the AI got a useful token".** Keycloak strips scopes, roles, and identity from dynamically registered clients by default. Every claim the agent acts on has to be consciously engineered in.

**Reconnect after any reload.** MCP sessions live in the `conference-mcp` process. Whenever it hot-reloads or restarts, Inspector's session id goes stale and every POST fails with `MCP error -32099: Streamable HTTP error: Error POSTing to endpoint (HTTP 404)`. Fix: click **Disconnect** then **Connect** in Inspector to re-run `initialize`. The OAuth token stays valid, so no re-login.

## Demo 1: Public schedule lookup (~8 min)

1. Open the MCP client: the conference-chat browser at `http://localhost:8082/`, or MCP Inspector pointed at `http://localhost:8081/mcp`.
2. Log in as **attendee / attendee** (chat) or run DCR (Inspector).
3. Ask: *"What's happening at jPrime right now?"* (calls `whats_on_now`).
4. Ask: *"What should I see after the keynote on day 2?"* (calls `whats_next`).
5. Highlight PKCE: the `conference-chat` terminal logs a line on every login, e.g.
   ```
   PKCE on outbound authorization request: code_challenge_method=S256 code_challenge=E9Me...
   ```
   Point at it on the terminal (it survives the redirect, unlike the URL bar). Then show the access token decoded in the inspector.

Talking points: **PKCE, DCR, why these matter for AI clients.**

The audit dashboard shows nothing yet (read-only tools do not audit). Tell the audience: "Read tools do not show up. That is on purpose. The audit log records intent, not curiosity."

## Demo 2: Personal agenda with token propagation (~10 min)

1. Stay logged in, or re-login as **willem.jan / willem.jan** for the speaker side later.
2. Ask: *"Bookmark the JSpecify talk for me."* (calls `bookmark_session`). A blue `BOOKMARK_ADD` event flies onto the audit dashboard.
3. Ask: *"Show me my agenda."* (calls `my_agenda`).
4. Ask: *"Also add Concurrency Crossroads."* Then *"Do I have any conflicts?"* (calls `my_conflicts`).
5. Ask: *"Rate the MCP Security talk 5 stars with the comment 'great use of caffeine'."* (calls `rate_session`). A `RATE_SESSION` event lands, with `attendee_subject=willem.jan`.
6. Turn to the second monitor and read the event aloud:
   ```
   audit_event
     action               RATE_SESSION
     target               session:110 (Practical MCP Security)
     attendee_subject     willem.jan
     token_acr            1
     ...
   ```
7. **Punchline:** not "AI rated it 5 stars" but "Willem Jan rated it 5 stars, executed by an AI on his behalf, with his token, fully auditable."

## Demo 3: Step-up auth (~8 min)

1. Stay logged in as **willem.jan**, no MFA (acr=1).
2. Ask: *"Show me the feedback on my MCP talk."* (calls `my_session_feedback`) returns seeded ratings.
3. Ask: *"Who signed up to attend my Concurrency Crossroads deep dive?"* (calls `view_session_attendees`).
4. Server returns `insufficient_user_authentication`. Show the 401 + `WWW-Authenticate` response on the second screen (the chat surfaces the same as an amber step-up card). Talk through what a real client would do next: re-auth with `acr_values=urn:mace:incommon:iap:silver`, retry the tool call.
5. (Optional, only if TOTP was enrolled in pre-flight) Re-authenticate at the higher ACR and retry. The audit dashboard will show an amber `view_session_attendees` event with `token_acr=urn:mace:incommon:iap:silver`.

Talking points: **step-up is the spec-level answer to "OAuth is for humans". Same protocol, different acr requirement, server-driven.**

## Recovery

| Symptom | Fix |
|---------|-----|
| Inspector: `-32099 ... Error POSTing to endpoint (HTTP 404)` | Stale MCP session after a `conference-mcp` reload. Disconnect then Connect in Inspector to get a fresh session. |
| Inspector DCR fails (rejected policy, 403 on tools, or claimless token) | The running Keycloak is using a stale realm. Recycle it so the realm re-imports (see "Reload the realm" below). |
| `whats_on_now` returns nothing | Confirm `DEMO_NOW` env var is set, then `q` and restart `conference-api`. |
| Audit dashboard frozen | Hard refresh the browser tab; the poll is every 2 seconds. |
| Audit log shows stale rehearsal data | `docker rm -f` the Dev Services Postgres container and restart `conference-api`. Hibernate `drop-and-create` plus the seeders give you a clean slate. |
| Step-up flow does not prompt for TOTP | Expected: the realm does not ship a custom step-up flow. Show the 401 + `WWW-Authenticate` response and explain what a compliant client does next. |

### Reload the realm

Editing `keycloak-realm.json` has no effect until Keycloak re-imports it, and Dev Services **reuses** the shared Keycloak container across app restarts. To force a re-import, the container must be gone before any app starts:

```bash
# 1. Ctrl-C all three quarkus:dev processes.
# 2. Remove the shared Keycloak container so Dev Services cannot reuse it:
docker rm -f $(docker ps -aq --filter "label=quarkus-dev-service-keycloak=jprime-keycloak")
# 3. Restart conference-api FIRST (it creates Keycloak and imports the realm), then mcp, then chat.
```

After this, reconnect Inspector so it registers a fresh DCR client against the new realm (the previously cached client id no longer exists).

## After the talk

```bash
# Ctrl-C all three quarkus:dev processes.
# Dev Services containers stay running between sessions; remove them explicitly with:
docker ps --filter "label=quarkus-dev-service" -q | xargs -r docker rm -f
```
