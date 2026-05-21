# jPrime 2026 Conference Companion: MCP Demo Spec

## Talk context

- **Event**: jPrime 2026, Sofia Tech Park, 3-4 June 2026
- **Session**: "Practical MCP Security in Action", Hall B, day 1, 10:00 to 10:50
- **Speaker**: Willem Jan Glerum
- **Audience level**: BEGINNER
- **Slot**: 50 minutes total, including 3 live demos
- **Talk thesis**: MCP authorization is OAuth 2.1 done right. Quarkus makes it tractable. Governance matters more than the token format.

## Purpose of this document

This is a build spec for the demo backend, intended for handoff to Claude Code. It defines two Quarkus applications:

1. **conference-api**: stores jPrime schedule data, exposes REST endpoints for the MCP server to consume
2. **conference-mcp**: Quarkus MCP server that exposes tools to AI clients, secured with OAuth 2.1

The talk will also use a Keycloak instance and an MCP-capable client (MCP Inspector or a small SPA). Those are out of scope for this document but referenced where relevant.

## Non-goals

- Do not build a full conference management system. The data is read-mostly with a small mutable surface for user agendas and ratings.
- Do not implement real MFA. Step-up auth uses Keycloak's built-in ACR levels with a configured second factor (TOTP).
- Do not handle real PII. Speaker accounts in this demo are seeded fixtures, not real attendee data.

---

## Architecture

```
[ MCP Client (Inspector/SPA) ]
            |
            | OAuth 2.1 auth code + PKCE, DCR, token exchange, step-up
            v
[ conference-mcp  (Quarkus MCP Server) ]
            |
            | REST, service-to-service auth (client credentials)
            v
[ conference-api  (Quarkus REST + Panache + Postgres) ]
            |
            v
[ Postgres ]

[ Keycloak ]  authorizes everything above
```

Two separate Quarkus apps on purpose: the MCP server is a thin protocol adapter, the data API is reusable and can be hit directly during the talk to show "see, the data is real".

---

## Data model (conference-api)

All entities live in Postgres, managed via Hibernate ORM with Panache.

### Speaker
- id (long, pk)
- external_id (string, the jprime.io agenda speaker reference if available, nullable)
- name (string)
- bio (text)
- company (string, nullable)
- twitter_handle (string, nullable)

### Session
- id (long, pk)
- external_id (string, the jprime.io agenda id, eg "253")
- title (string)
- abstract (text)
- track (enum: HALL_A, HALL_B, WORKSHOP)
- room (string, eg "Hall A")
- level (enum: BEGINNER, INTERMEDIATE, ADVANCED, nullable)
- starts_at (timestamp with timezone, Europe/Sofia)
- ends_at (timestamp with timezone)
- day (int, 1 or 2)

### SessionSpeaker (join table)
- session_id (fk)
- speaker_id (fk)

### Attendee
- id (long, pk)
- subject (string, the OIDC sub claim, unique)
- display_name (string)
- email (string, nullable)
- is_speaker (boolean, links to a Speaker by matching email or explicit seed)
- speaker_id (fk to Speaker, nullable)

### Bookmark
- id (long, pk)
- attendee_id (fk)
- session_id (fk)
- created_at (timestamp)
- unique (attendee_id, session_id)

### Rating
- id (long, pk)
- attendee_id (fk)
- session_id (fk)
- stars (int, 1 to 5)
- comment (text, nullable)
- created_at (timestamp)
- unique (attendee_id, session_id)

### AuditEvent
- id (long, pk)
- attendee_subject (string)
- action (string, eg "RATE_SESSION", "CANCEL_SESSION_ATTEMPTED")
- target (string, eg "session:253")
- token_acr (string, the acr claim at the time of the action, nullable)
- token_amr (string array, the amr claim at the time of the action, nullable)
- created_at (timestamp)
- detail (text, nullable, json blob)

The AuditEvent table is critical: the talk demonstrates governance by showing real audit lines on screen.

---

## Data ingestion

A one-shot Quarkus command-mode runner OR a `@Startup` bean that:

1. Fetches `https://jprime.io/agenda` once at build time or on first startup.
2. Parses the HTML (jsoup) and extracts sessions, times, speakers, rooms, days.
3. For each session link `https://jprime.io/agenda/{id}`, fetches the detail page and pulls the abstract and speaker bios.
4. Persists everything.
5. Logs a summary: "Imported X sessions, Y speakers".

Add a second seed step that creates:
- ~10 fake `Attendee` rows including one with subject matching the demo speaker login (Willem Jan).
- ~30 fake `Rating` rows distributed across sessions, with a mix of stars and comments. Make at least 5 of them target the speaker's own sessions so the speaker-feedback demo has something to show.
- ~20 fake `Bookmark` rows.

Keep the seed data in a versioned SQL file under `src/main/resources/seed/` so it can be re-applied easily.

**Failure mode to design for**: jprime.io may be down or the page structure may change. The importer must be idempotent and the app must boot from already-seeded data if the import fails. Log a loud warning, do not crash.

---

## conference-api: REST endpoints

All endpoints under `/api/v1/`. JSON in, JSON out. OpenAPI generated.

### Public (no auth)
- `GET /sessions` — list, supports `?day=1`, `?track=HALL_A`, `?speaker_id=`, `?level=`, `?q=` (full text on title and abstract)
- `GET /sessions/{id}`
- `GET /sessions/current` — sessions happening right now (server clock or `?at=` for demo determinism)
- `GET /sessions/next` — next sessions starting after now
- `GET /speakers`
- `GET /speakers/{id}`
- `GET /speakers/{id}/sessions`
- `GET /rooms` — distinct list of rooms with their current session

### Attendee (requires authenticated user, scope `attendee`)
- `GET /me` — returns attendee profile, creates one on first call if missing (just-in-time provisioning from JWT claims)
- `GET /me/agenda` — my bookmarks, ordered by session start time
- `POST /me/agenda` — body `{ "session_id": 253 }`
- `DELETE /me/agenda/{session_id}`
- `GET /me/conflicts` — my bookmarks that overlap in time
- `POST /sessions/{id}/ratings` — body `{ "stars": 1-5, "comment": "..." }`. Creates an AuditEvent. Refuses to rate a session that hasn't started yet, returns 422.
- `GET /me/ratings`

### Speaker (requires authenticated user with `is_speaker=true` and the scope `speaker`)
- `GET /me/sessions/feedback` — all ratings for sessions I'm speaking on, aggregated and individual

### Speaker, step-up required (requires acr=`urn:mace:incommon:iap:silver` or amr containing `mfa`)
- `GET /sessions/{id}/attendees` — list attendees who bookmarked this session, returns names and emails. **Server returns 401 with WWW-Authenticate header indicating insufficient_user_authentication if step-up not satisfied.** Only allowed if the caller is a speaker on this session.
- `POST /sessions/{id}/cancel` — body `{ "reason": "..." }`. Marks session as cancelled but is reversible. Audited with extreme prejudice. Only allowed if caller is a speaker on this session.

Note: even though conference-api enforces these rules, the MCP server is what the AI client talks to. The MCP server enforces them too, in a defense-in-depth pattern. We document both layers.

---

## conference-mcp: MCP tools

Use the official Quarkus MCP server extension (`quarkus-mcp-server-sse` or `quarkus-mcp-server-stdio`, prefer SSE for the live demo because it's network-visible and shows the OAuth dance cleanly).

Tools map closely to the REST endpoints but are described in natural language for the AI's benefit. Each tool has:
- a name
- a description the LLM reads to decide when to call it
- a parameter schema
- an implementation that calls conference-api with the propagated user token

### Public tier tools (no user auth, just MCP client registered via DCR)

- `list_sessions`
  - description: "Search the jPrime 2026 schedule. Use this to find talks by topic, track, day, or speaker."
  - parameters: `day?`, `track?`, `query?`, `speaker_name?`
- `get_session`
  - description: "Get full details for one session including abstract and speakers."
  - parameters: `session_id`
- `whats_on_now`
  - description: "Find out which sessions are happening right now at jPrime."
  - parameters: none
- `whats_next`
  - description: "Find out which sessions are starting next."
  - parameters: `limit?` (default 3)
- `find_speaker`
  - description: "Look up a speaker by name and see what they're presenting."
  - parameters: `name`

### Attendee tier tools (require user OAuth, scope `attendee`)

- `bookmark_session`
  - description: "Add a session to my personal agenda."
  - parameters: `session_id`
- `unbookmark_session`
- `my_agenda`
- `my_conflicts`
  - description: "Show me sessions I've bookmarked that overlap in time."
- `rate_session`
  - description: "Submit a 1 to 5 star rating with an optional comment for a session I attended. The rating is recorded under MY identity and is auditable."
  - parameters: `session_id`, `stars`, `comment?`

### Speaker tier tools (require scope `speaker`)

- `my_session_feedback`
  - description: "As a speaker, see the ratings and comments attendees have left for my sessions."

### Speaker tier, step-up required

- `view_session_attendees`
  - description: "As a speaker, see the list of attendees who bookmarked my session. This contains personal data and requires recent strong authentication."
  - parameters: `session_id`
- `cancel_my_session`
  - description: "As a speaker, mark one of my own sessions as cancelled. Highly destructive, requires recent strong authentication. The action is fully audited."
  - parameters: `session_id`, `reason`

---

## Auth implementation details

### Keycloak setup
- Realm: `jprime`
- Clients:
  - `conference-mcp` (confidential, for service-to-service calls to conference-api)
  - `mcp-clients` (public, configured to allow Dynamic Client Registration so MCP clients can register themselves)
- Realm roles: `attendee`, `speaker`
- Client scopes: `attendee`, `speaker`
- Users (seeded via Keycloak realm export json):
  - `attendee1` / `attendee1` — role `attendee`
  - `willem.jan` / `willem.jan` — roles `attendee` + `speaker`, linked to the Willem Jan speaker fixture, TOTP enrolled
  - `admin` — administrative login for the demo operator
- Authentication flow: configure a "step-up" flow that requires TOTP when acr=2 is requested (`urn:mace:incommon:iap:silver` or similar).
- Token exchange enabled on the realm (Keycloak feature flag).

Provide a `compose.yml` that boots Postgres + Keycloak with this realm imported.

### Quarkus security config
- conference-api: `quarkus-oidc` in bearer-only mode, audience `conference-api`, validates tokens issued by the local Keycloak.
- conference-mcp: `quarkus-oidc` configured to:
  - Validate incoming MCP-client tokens.
  - When a tool is invoked, propagate the user token to conference-api using `quarkus-oidc-token-propagation` (RFC 8693 exchange where needed, otherwise straight propagation).
  - For step-up tools, check the incoming token's `acr` claim. If insufficient, return an MCP error with the WWW-Authenticate-style hint so the client knows to re-authenticate.

### Required ACR check
The step-up tools should look for `acr=2` (or the named ACR that maps to MFA in the Keycloak flow). On insufficient acr, return a structured error that the demo client can act on. This is the heart of demo 3 and needs to work cleanly. **Confirm with Sergej which exact format the Quarkus MCP server will emit for the step-up challenge once his test lands.**

---

## Demo flow on stage

### Demo 1: Public schedule lookup (~8 min)
- Open MCP client (Inspector or SPA).
- Click "register MCP server", paste the conference-mcp URL.
- DCR happens, browser opens for Keycloak login (attendee1).
- Ask: "What's happening at jPrime right now?" then "What should I see after the keynote on day 2?"
- Show the OAuth flow in the URL bar, show the token in the inspector.
- Key talking points: PKCE, DCR, why these matter for AI clients.

### Demo 2: Personal agenda with token propagation (~10 min)
- Ask: "Bookmark the JSpecify talk for me", "Show me my agenda", "Do I have any conflicts?"
- Trigger a conflict deliberately by bookmarking two overlapping sessions.
- Ask: "Rate my last session 5 stars with comment 'great use of caffeine'".
- **Show the audit log on a second screen**: the entry shows `attendee_subject=willem.jan` and the action. Make the point: not "AI rated it 5 stars", but "Willem Jan rated it 5 stars, executed by an AI on his behalf, with his token, auditable".

### Demo 3: Step-up auth (~8 min)
- Log in as willem.jan (speaker).
- Ask: "Show me the feedback on my MCP talk." Works, returns the seeded ratings.
- Ask: "Who's signed up to attend my Concurrency Crossroads deep dive?"
- Step-up challenge fires. Client prompts for TOTP. Enter code. Retry succeeds.
- Then: "Cancel my deep dive, the reason is I want to go home early." Step-up satisfied from previous step, action audited but reversible. Reverse it live to show governance.
- Talking points: step-up is the spec-level answer to the "OAuth is for humans" critique. Same protocol, different acr requirement, server-driven.

### Safety nets
- Pre-recorded video of each demo at 1.5x, on a fallback browser tab.
- All demos work offline against the seeded database. No external dependency at demo time.
- Seed data includes a "well-known" current time override (env var `DEMO_NOW=2026-06-03T10:45:00+03:00`) so `whats_on_now` always returns something interesting regardless of when the talk is rehearsed.

---

## Tech stack and conventions

- Java 25 (or whatever LTS Quarkus 3.x supports cleanly at build time, confirm before scaffolding)
- Quarkus 3.x latest stable
- Extensions: `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-oidc`, `quarkus-oidc-token-propagation`, `quarkus-mcp-server-sse`, `quarkus-smallrye-openapi`, `quarkus-flyway`, `quarkus-jsoup` (or pull in jsoup as a plain dependency)
- Build: Maven (matches Lunatech conventions and the Darva tooling context).
- Test: JUnit 5, RestAssured, Testcontainers for Postgres and Keycloak.
- Code style: no em dashes in any generated text, comments, or documentation.
- Repo layout: monorepo with `conference-api/`, `conference-mcp/`, `infra/compose.yml`, `infra/keycloak-realm.json`, `docs/`.

## What Claude Code should produce

1. The two Quarkus apps as Maven projects, both buildable with `./mvnw quarkus:dev`.
2. The compose file that boots Postgres and Keycloak with the realm pre-imported.
3. The schedule importer (one-shot) and the SQL seed file.
4. The full REST API in conference-api, with OpenAPI annotations.
5. The full set of MCP tools in conference-mcp, with descriptions tuned for an LLM.
6. Integration tests for each REST endpoint with Testcontainers.
7. A `README.md` per app and a top-level `RUNBOOK.md` with the exact demo steps and the env vars to set.
8. A `DEMO_RESET.sh` script that wipes user-generated data (bookmarks, ratings, audit) and re-seeds the demo set, for between rehearsal runs.

## Open questions to resolve before scaffolding

1. The latest MCP authorization spec details for step-up challenges: confirm the exact wire format Quarkus MCP server will use once Sergej's test lands. Default to following Sergej's lead.
2. Whether to use a SPA or MCP Inspector as the demo client. Recommendation: MCP Inspector for demo 1 (fastest to show DCR), Inspector or a tiny SPA for demos 2 and 3. Out of scope here.
3. Whether to embed the LLM call live (Claude / OpenAI) or to use the Inspector's "fake assistant" mode. Recommendation: live for engagement, with a recorded fallback. Decision affects ops, not the spec.

