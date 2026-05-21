# Runbook: jPrime 2026 MCP Demo

The exact steps to rehearse and run the three demos on stage.

## Stage assumptions

- Laptop with Docker, Java 25, Maven.
- Two terminal windows visible (or tmux split): `conference-api` on the left, audit tail on the right.
- One browser window with the MCP client (MCP Inspector or a small SPA pointing at the MCP server).
- One backup video per demo at 1.5x speed in case the live run misbehaves.

## Env vars

| Variable | Purpose | Demo default |
|----------|---------|--------------|
| `DEMO_NOW` | Override the "current time" used by `whats_on_now`, `whats_next`, and rating cutoff. | `2026-06-03T10:45:00+03:00` |
| `JPRIME_IMPORTER_ENABLED` | Whether to attempt the live jprime.io scrape on startup. Set to `false` if Wi-Fi is unreliable. | `false` for rehearsals, `true` otherwise |
| `OIDC_AUTH_SERVER_URL` | Realm URL Quarkus validates tokens against. | `http://localhost:8081/realms/jprime` |
| `CONFERENCE_API_URL` | URL the MCP server uses to reach the data API. | `http://localhost:8080` |

Source `infra/demo.env` (create your own; not checked in with secrets) before launching.

## Boot order

```bash
docker compose -f infra/compose.yml up -d
cd conference-api && DEMO_NOW=2026-06-03T10:45:00+03:00 ./mvnw quarkus:dev
# new terminal
cd conference-mcp && DEMO_NOW=2026-06-03T10:45:00+03:00 ./mvnw quarkus:dev
```

Wait for both logs to show `started in <time>` and the audit tail to be quiet.

## Pre-flight check (do this before going on stage)

1. `curl http://localhost:8080/api/v1/sessions | jq '.[0]'` returns a real session.
2. Open `http://localhost:8081/realms/jprime/.well-known/openid-configuration` and confirm the realm is up.
3. In the MCP client, paste `http://localhost:8082/mcp/sse` as the server URL, click register. You should see Dynamic Client Registration succeed.
4. Run the MCP tool `whats_on_now`. It should return the "Practical MCP Security in Action" session.
5. Tail the audit log query in the second screen: `curl -H 'Authorization: Bearer $TOKEN' http://localhost:8080/api/v1/audit?limit=5`.

If any of the above fails, run `./DEMO_RESET.sh` and try again.

## Demo 1: Public schedule lookup (~8 min)

1. Open MCP client. Click "Register MCP server" and paste `http://localhost:8082/mcp/sse`.
2. DCR flow runs. Browser opens for Keycloak login. Use **attendee1 / attendee1**.
3. Ask: *"What's happening at jPrime right now?"* (calls `whats_on_now`).
4. Ask: *"What should I see after the keynote on day 2?"* (calls `whats_next` with `day=2`).
5. Show the URL bar mid-flow to highlight PKCE, then show the access token decoded in the inspector.

Talking points: **PKCE, DCR, why these matter for AI clients.**

## Demo 2: Personal agenda with token propagation (~10 min)

1. Stay logged in as attendee1, or re-login as **willem.jan / willem.jan** for the speaker side.
2. Ask: *"Bookmark the JSpecify talk for me."* (calls `bookmark_session`).
3. Ask: *"Show me my agenda."* (calls `my_agenda`).
4. Ask: *"Also add the Concurrency Crossroads talk."* Then *"Do I have any conflicts?"* (calls `my_conflicts`).
5. Ask: *"Rate the MCP Security talk 5 stars with the comment 'great use of caffeine'."* (calls `rate_session`).
6. Switch to the second screen and `curl /api/v1/audit | jq`. Point at the entry:
   ```
   {"attendee_subject":"willem.jan","action":"RATE_SESSION","target":"session:110", ...}
   ```
   **Punchline:** not "AI rated it 5 stars" but "Willem Jan rated it 5 stars, executed by an AI on his behalf, with his token, fully auditable."

## Demo 3: Step-up auth (~8 min)

1. Logged in as **willem.jan** (no MFA yet, acr=1).
2. Ask: *"Show me the feedback on my MCP talk."* (calls `my_session_feedback`) -> works, returns seeded ratings.
3. Ask: *"Who signed up to attend my Concurrency Crossroads deep dive?"* (calls `view_session_attendees`).
4. Server returns `insufficient_user_authentication`. The MCP client surfaces it; the browser prompts for TOTP. Enter the code.
5. Tool call retries automatically (or you re-prompt the AI). It succeeds and returns names/emails.
6. Ask: *"Cancel my deep dive, the reason is I want to go home early."* (calls `cancel_my_session`). Step-up already satisfied, so it goes through. The session is marked cancelled.
7. Open the second screen and show the audit log entry, with `token_acr=urn:mace:incommon:iap:silver`.
8. Re-issue the same command to reverse the cancel (the cancel tool is a toggle for the demo).

Talking points: **step-up is the spec-level answer to the "OAuth is for humans" critique. Same protocol, different acr requirement, server-driven.**

## Recovery

| Symptom | Fix |
|---------|-----|
| MCP client cannot register | `docker compose restart keycloak`, then refresh DCR endpoint URL. |
| `whats_on_now` returns nothing | Confirm `DEMO_NOW` env var is set; restart `conference-api`. |
| Audit log shows stale data | Run `./DEMO_RESET.sh`. |
| Live jprime.io scrape gone wrong | Set `JPRIME_IMPORTER_ENABLED=false` and rely on the baked-in schedule. |
| Step-up flow does not prompt for TOTP | Open Keycloak admin, switch realm browser flow to `browser-step-up`, log out and back in. |

## After the talk

```bash
docker compose -f infra/compose.yml down -v
```
