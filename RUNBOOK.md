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

## MCP Inspector and Client ID Metadata Documents (CIMD)

CIMD is the spec-preferred successor to DCR, introduced in the MCP 2025-11-25 authorization spec (SEP-991). Instead of registering a client by POSTing to a writeable registration endpoint, the client presents an HTTPS URL as its `client_id`. The authorization server fetches the client metadata document at that URL, validates the redirect URIs, and runs PKCE as normal. There is no registration record to create, store, or police.

This demo ships a static client metadata document for MCP Inspector at:

```
conference-mcp/src/main/resources/META-INF/resources/cimd/mcp-inspector.json
```

served at `http://host.docker.internal:8081/cimd/mcp-inspector.json`. That same URL is the `client_id` value inside the document, so the two match exactly as the spec requires.

### Why host.docker.internal

Keycloak runs inside a Dev Services container and must fetch the metadata document itself. From inside the container the host is reachable as `host.docker.internal`. For the URL to resolve identically on the host where Inspector runs, add this line to `/etc/hosts` once:

```
127.0.0.1 host.docker.internal
```

### Enable CIMD on Keycloak

CIMD is an experimental Keycloak feature (verified present in the Dev Services image, Keycloak 26.6.1), off by default. It is switched on through Dev Services with:

```
quarkus.keycloak.devservices.features=cimd
```

already present in all three `application.properties` files. Because Dev Services reuses the shared Keycloak container, the feature only takes effect after the container is recreated. If the running container predates this setting, recycle it with the "Reload the realm" procedure below, then restart the apps.

Confirm the feature is live from the Keycloak startup log:

```bash
docker logs $(docker ps -q --filter "label=quarkus-dev-service-keycloak=jprime-keycloak") 2>&1 \
  | grep -i "features enabled"
# expect a line listing cimd, e.g. "Experimental features enabled: cimd:v1"
```

### Drive it from Inspector

Point Inspector at `http://localhost:8081/mcp` as usual. In a CIMD-capable build, set the client id to the metadata URL rather than running DCR. Inspector sends the URL as `client_id`, Keycloak fetches the document (visible in the Keycloak log as an outbound GET for `/cimd/mcp-inspector.json`), then runs PKCE and the normal login.

If the Inspector build on hand does not yet emit a URL `client_id`, drive the contrast manually and show the authorization request on the second screen:

```bash
KC=$(curl -s http://localhost:8081/.well-known/oauth-protected-resource | jq -r '.authorization_servers[0]')
open "$KC/protocol/openid-connect/auth?response_type=code\
&client_id=http://host.docker.internal:8081/cimd/mcp-inspector.json\
&redirect_uri=http://localhost:6274/oauth/callback\
&scope=openid&code_challenge=E9Me...&code_challenge_method=S256"
```

Watch the Keycloak log: it fetches the metadata document before rendering the login page.

### DCR vs CIMD, the punchline

The DCR section above had to clear four anonymous client-registration policies just to let Inspector register. None of them apply under CIMD, because nothing is being written:

| Concern under DCR | Under CIMD |
|-------------------|------------|
| Allowed Client Scopes policy rejected the `openid` scope | No registration step, scope is requested at authorization time as normal |
| Trusted Hosts policy rejected the `client_uri` and the sender IP | No registration call to police, the client identity is a document the server reads |
| Full Scope Disabled stripped realm roles from the token | No DCR-specific stripping, the realm role and username mappers apply as usual |
| Consent Required forced a prompt on each freshly registered client | No per-registration client object to attach consent to |
| The open anonymous registration endpoint is a standing write surface | There is no writeable registration endpoint at all |

Talking point: **DCR makes the authorization server keep a writeable door open, then asks you to bolt four locks onto it. CIMD removes the door. The client introduces itself with a URL the server reads, nothing is stored, and there is no registration surface to attack.**

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

6. **Registration contrast.** Show how the Inspector got its client identity two ways. First DCR: the four policies you had to clear so Keycloak would accept a dynamically registered client (see the DCR section above). Then CIMD: the same Inspector identified by a URL `client_id`, with Keycloak fetching the metadata document and zero policy surgery (see the CIMD section above). Land the punchline: DCR keeps a writeable registration door open and asks you to lock it down, CIMD removes the door.

Talking points: **PKCE, DCR, and CIMD as the spec-preferred successor, why client identity matters for AI clients.**

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
| CIMD: Keycloak does not fetch the metadata document, or `invalid_client` | The `cimd` feature is not active on the running container, or the container predates the `features=cimd` setting. Recycle Keycloak (see "Reload the realm"), then check the startup log for `features enabled: cimd`. |
| CIMD: Keycloak cannot reach the metadata URL | `host.docker.internal` is not resolvable. Add `127.0.0.1 host.docker.internal` to `/etc/hosts` and confirm `curl http://host.docker.internal:8081/cimd/mcp-inspector.json` returns the document. |
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

After this, reconnect Inspector so it registers a fresh DCR client against the new realm (the previously cached client id no longer exists). This recycle is also what activates the `cimd` feature on a container that predates the `features=cimd` setting.

## After the talk

```bash
# Ctrl-C all three quarkus:dev processes.
# Dev Services containers stay running between sessions; remove them explicitly with:
docker ps --filter "label=quarkus-dev-service" -q | xargs -r docker rm -f
```
