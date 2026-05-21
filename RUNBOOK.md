# Runbook: Practical MCP Security in Action

The stage-side runbook for the three live demos at jPrime 2026.

## Stage assumptions

- Laptop with Docker (for Dev Services), Java 25, Maven.
- Two terminal windows: `conference-api` left, `conference-mcp` right.
- Second monitor showing the **live audit dashboard** at `http://localhost:8080/audit-live/`.
- One browser window with the MCP client (MCP Inspector or a small SPA).
- One backup video per demo at 1.5x speed in case the live run misbehaves.

## Env vars

| Variable | Purpose | Demo default |
|----------|---------|--------------|
| `DEMO_NOW` | Override the "current time" used by `whats_on_now`, `whats_next`, and the rating cutoff. | `2026-06-03T10:45:00+03:00` |
| `JPRIME_IMPORTER_ENABLED` | Whether to attempt the live jprime.io scrape on startup. Set to `false` if Wi-Fi is unreliable. | `false` for rehearsals, `true` otherwise |

`OIDC_AUTH_SERVER_URL`, `CONFERENCE_API_URL`, and database credentials are only needed in `%prod`. In dev mode Quarkus Dev Services wires everything automatically.

## Boot order

```bash
# Terminal 1 -- starts Postgres + Keycloak Dev Services on first boot
cd conference-api && DEMO_NOW=2026-06-03T10:45:00+03:00 ./mvnw quarkus:dev

# Terminal 2 -- joins the shared Keycloak container
cd conference-mcp && DEMO_NOW=2026-06-03T10:45:00+03:00 ./mvnw quarkus:dev
```

The first start pulls the Postgres and Keycloak images. Allow 30 to 60 seconds on a fresh machine; subsequent restarts reuse the containers.

Open `http://localhost:8080/audit-live/` on the second monitor.

## Pre-flight check (before going on stage)

1. `curl http://localhost:8080/api/v1/sessions | jq '.[0]'` returns a real session.
2. The audit dashboard shows "Waiting for the first event..." (clean state) or a recent rehearsal event.
3. Open the Dev UI at `http://localhost:8080/q/dev/` and click the **Keycloak** tile. Confirm realm `jprime` is imported with `attendee1`, `willem.jan`, and `admin-demo`.
4. From the Dev UI Keycloak tile, copy the realm URL. Paste `http://<dev-keycloak-url>/.well-known/openid-configuration` into a browser tab and confirm 200.
5. In the MCP client, paste `http://localhost:8082/mcp/sse` as the server URL and register. DCR should succeed.
6. Run the MCP tool `whats_on_now`. It returns the "Practical MCP Security in Action" session.

If any of the above fails, run `./DEMO_RESET.sh` and try again.

## Demo 1: Public schedule lookup (~8 min)

1. Open the MCP client. Click **Register MCP server** and paste `http://localhost:8082/mcp/sse`.
2. DCR runs. Browser opens for Keycloak login. Use **attendee1 / attendee1**.
3. Ask: *"What's happening at jPrime right now?"* (calls `whats_on_now`).
4. Ask: *"What should I see after the keynote on day 2?"* (calls `whats_next`).
5. Show the URL bar mid-flow to highlight PKCE, then show the access token decoded in the inspector.

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

1. Stay logged in as **willem.jan**, no MFA yet (acr=1).
2. Ask: *"Show me the feedback on my MCP talk."* (calls `my_session_feedback`) returns seeded ratings.
3. Ask: *"Who signed up to attend my Concurrency Crossroads deep dive?"* (calls `view_session_attendees`).
4. Server returns `insufficient_user_authentication`. The MCP client surfaces it; the browser prompts for TOTP. Enter the code.
5. The tool call retries automatically. It succeeds and returns names/emails.
6. Ask: *"Cancel my deep dive, the reason is I want to go home early."* (calls `cancel_my_session`). Step-up already satisfied, so it goes through.
7. The audit dashboard now shows an amber `view_session_attendees` event and a red `CANCEL_SESSION` event, both with `token_acr=urn:mace:incommon:iap:silver`.
8. Re-issue the same cancel command to reverse it (the tool is a toggle for the demo).

Talking points: **step-up is the spec-level answer to "OAuth is for humans". Same protocol, different acr requirement, server-driven.**

## Recovery

| Symptom | Fix |
|---------|-----|
| MCP client cannot register | In the Dev UI Keycloak tile, click "Restart"; refresh the DCR endpoint URL. |
| `whats_on_now` returns nothing | Confirm `DEMO_NOW` env var is set, then `q` and restart `conference-api`. |
| Audit dashboard frozen | Hard refresh the browser tab; the poll is every 2 seconds. |
| Audit log shows stale rehearsal data | Run `./DEMO_RESET.sh`. |
| Live jprime.io scrape gone wrong | Set `JPRIME_IMPORTER_ENABLED=false` and rely on the baked-in schedule. |
| Step-up flow does not prompt for TOTP | Open the Dev UI Keycloak tile, switch realm browser flow to `browser-step-up`, log out and back in. |

## After the talk

```bash
# Ctrl-C both quarkus:dev processes.
# Dev Services containers stay running between sessions; remove them explicitly with:
docker ps --filter "label=quarkus-dev-service" -q | xargs -r docker rm -f
```
