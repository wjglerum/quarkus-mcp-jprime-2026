# jPrime 2026 Conference Companion: MCP Demo Spec

## Talk context

- **Event**: jPrime 2026, Sofia Tech Park, 3-4 June 2026
- **Session**: "Practical MCP Security in Action", Hall B, day 1, 10:00 to 10:50
- **Speaker**: Willem Jan Glerum (Lunatech)
- **Audience level**: BEGINNER
- **Slot**: 50 minutes total, including 3 live demos
- **Talk thesis**: MCP authorization is OAuth 2.1 done right. Quarkus makes it tractable. Governance matters more than the token format.

## Purpose of this document

A self-contained build spec for the demo. A fresh agent should be able to read this file and rebuild the whole stack without referring to the original conversation. It defines three Quarkus applications:

1. **conference-api**: stores the jPrime schedule, attendee data, ratings, and audit log. Exposes REST endpoints under `/api/v1/` and serves the second-screen audit dashboard at `/audit-live/`.
2. **conference-mcp**: Quarkus MCP server. Thin protocol adapter that exposes tools to AI clients, secured with OAuth 2.1 and token propagation to conference-api.
3. **conference-chat**: AI chat interface for the live demo. Logs the speaker in via OIDC web-app flow, lets the user (or a swappable LLM) call MCP tools through `conference-mcp`, and renders tool calls in a deck-styled UI.

All three apps share one Keycloak Dev Service. There is no `docker compose` file: every backing service is auto-provisioned by Quarkus Dev Services.

## Non-goals

- No full conference management system. Read-mostly schedule plus a small mutable surface for agendas, ratings, and (reversible) cancellations.
- No real MFA. Step-up auth uses Keycloak's built-in ACR levels with a configured TOTP factor.
- No real PII. All attendees are seeded fixtures.
- No deployment topology beyond `mvn quarkus:dev`. Production config exists but is not the focus.

---

## Architecture

```
[ Browser (Speaker or audience member on stage) ]
            |
            | OIDC web-app flow (auth code + PKCE), session cookie
            v
[ conference-chat (Quarkus, port 8082)
   - SPA chat UI styled to the talk deck
   - LangChain4j MCP client to conference-mcp
   - Scripted intent matcher (deterministic, default)
   - LangChain4j chat model: Anthropic / OpenAI / Ollama (swappable)
]
            |
            | MCP over SSE (Authorization: Bearer <user token>)
            v
[ conference-mcp (Quarkus MCP Server, port 8081)
   - quarkus-mcp-server-sse
   - quarkus-oidc bearer-only validation
   - rest-client-oidc-token-propagation outbound
   - readiness probe hits conference-api
]
            |
            | REST (Authorization: Bearer <user token>)
            v
[ conference-api (Quarkus REST + Panache + Postgres, port 8080)
   - quarkus-oidc bearer-only
   - flyway migration + idempotent seed
   - serves /audit-live/ second-screen dashboard
   - /api/v1/audit/recent open feed
]
            |
            v
[ Postgres ]  <- Quarkus Dev Services
[ Keycloak ]  <- Quarkus Dev Services, realm pre-imported, shared across all three apps

[ MCP Inspector ]  optional, can hit conference-mcp directly to demo DCR
```

Sequential ports keep the demo URLs trivial to read on stage: `8080` is the API, `8081` is MCP, `8082` is the chat client.

Three Quarkus apps on purpose:

- The data API is reusable. The speaker can hit it directly on stage to prove "the data is real, not the AI making things up".
- The MCP server is a thin protocol adapter. Any MCP client (Inspector, Claude Desktop, the chat app) can use it.
- The chat client is the user-facing demo surface. It is also what the audience sees on the big screen.

---

## Data model (conference-api)

Keep it simple. Six tables, no enums, no join tables, no live import. **No Flyway.** Hibernate generates the schema from the JPA entities on boot (`quarkus.hibernate-orm.database.generation=drop-and-create` in dev/test; `none` in prod). The `session` table is named `conference_session` via `@Table(name = "conference_session")` to dodge reserved-word ambiguity. No manual sequence DDL: Hibernate picks the strategy on its own when it owns the schema.

### Speaker
- id (long, pk)
- name (string)
- bio (text, nullable)

### Session (table: `conference_session`)
- id (long, pk)
- title (string)
- abstract_text (text)
- room (string, plain text -- eg "Hall A", "Hall B", "Workshop Room")
- starts_at (timestamp with timezone, Europe/Sofia)
- ends_at (timestamp with timezone)
- speaker_id (fk to Speaker; **one speaker per session**)
- cancelled (boolean, default false; toggle for demo 3)
- cancellation_reason (text, nullable)

One speaker per session is the simplifying constraint. If a real talk has co-speakers, credit the primary speaker only; this keeps the join model out of the demo.

### Attendee
- id (long, pk)
- subject (string, the OIDC sub claim, unique)
- display_name (string)
- speaker_id (fk to Speaker, nullable; non-null means "is a speaker")

Just-in-time provisioning: the first authenticated call from a new subject creates the Attendee row. The seed shortcut for subject `willem.jan` always links to the Willem Jan speaker fixture so the speaker demos work with a clean DB.

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
- attendee_subject (string, not null)
- action (string, eg `RATE_SESSION`, `CANCEL_SESSION`, `BOOKMARK_ADD`, `CANCEL_SESSION_ATTEMPTED`, `RATE_SESSION_REJECTED_NOT_STARTED`)
- target (string, eg `session:253`)
- token_acr (string, nullable)
- token_amr (string, comma-joined, nullable)
- created_at (timestamp)
- detail (text, nullable)

The AuditEvent table is the heart of demos 2 and 3 and drives `/audit-live/`.

### What is **not** in the model

Explicitly rejected to keep the demo lean:
- No `track` / `level` enums. A `room` string is enough.
- No `external_id` columns. Internal ids only.
- No `day` field on Session. Derive from `starts_at` if a tool ever needs it.
- No multi-speaker join table.
- No `email` / `company` / `twitter_handle` columns. The realm has the email; the demo does not need company affiliations.
- No `/rooms` endpoint, no `/speakers/{id}` endpoint.

---

## Data ingestion

One thing only: a hand-curated **static schedule seeder** plus a demo-data seeder, both idempotent and applied on startup. **No live jprime.io scrape, no jsoup dependency.** The demo never depends on the internet for schedule data.

1. **Static schedule seeder**: if `Session.count() == 0`, install a hand-curated jPrime 2026 baseline of about 12 sessions across 6 speakers including Willem Jan.
2. **Demo data seeder**: if `Attendee.count() == 0`, create about 10 fake attendees, around 15 bookmarks, and around 25 ratings, with at least 5 ratings on Willem Jan's sessions for the speaker feedback demo.

A dev-only admin endpoint `POST /api/v1/admin/reseed-demo` wipes user-generated rows (bookmarks, ratings, audit, attendees) and re-runs the demo-data seeder. The top-level `DEMO_RESET.sh` hits it.

---

## conference-api: REST endpoints

All endpoints under `/api/v1/`. JSON in, JSON out. OpenAPI generated. Small surface area on purpose; the MCP server is what wraps these for the AI side.

### Public (no auth)
- `GET /sessions` -- list, supports `?speaker_id=`, `?q=` (substring on title and abstract).
- `GET /sessions/{id}`
- `GET /sessions/current` -- sessions running now. Accepts `?at=` (ISO timestamp) for demo determinism.
- `GET /sessions/next` -- accepts `?at=`, `?limit=` (default 3, max 20).
- `GET /speakers` -- flat list, including each speaker's sessions inline. No `/speakers/{id}` endpoint; the list is small.
- `GET /audit/recent` -- the last N events across all subjects, open for the second-screen dashboard. **The only mutable data this exposes is what already lands in the audit log; no PII beyond display names of seeded fixtures.**

### Attendee (role `attendee`)
- `GET /me` -- attendee profile. JIT provisioning on first call.
- `GET /me/agenda` -- my bookmarks, ordered by session start time.
- `POST /me/agenda` -- body `{ "sessionId": 253 }`.
- `DELETE /me/agenda/{sessionId}` -- 204 on success or no-op.
- `GET /me/conflicts` -- bookmarked sessions that overlap in time.
- `POST /sessions/{id}/ratings` -- body `{ "stars": 1-5, "comment": "..." }`. Audited. Refuses to rate a session that has not started yet, returns 422 with `{"error":"session_not_started"}`.
- `GET /me/ratings`.

### Speaker (role `speaker`)
- `GET /me/sessions/feedback` -- aggregate + individual ratings on my sessions.

### Speaker, step-up required (acr=`urn:mace:incommon:iap:silver` or amr containing `mfa`/`otp`)
- `GET /sessions/{id}/attendees` -- display names of attendees who bookmarked the session. Returns 401 with `WWW-Authenticate: Bearer error="insufficient_user_authentication", acr_values="urn:mace:incommon:iap:silver"` if step-up not satisfied. 403 if caller is not the speaker on the session (audited as `CANCEL_SESSION_ATTEMPTED`).
- `POST /sessions/{id}/cancel` -- body `{ "reason": "..." }`. Reversible toggle. Audited as `CANCEL_SESSION` / `CANCEL_SESSION_UNDONE`.

### Dev-only
- `POST /api/v1/admin/reseed-demo` -- forbidden in `%prod`. Used by `DEMO_RESET.sh`.

---

## /audit-live/ dashboard (conference-api)

The second-screen UI that lands every audit event in real time. Static SPA under `src/main/resources/META-INF/resources/audit-live/`. Polls `GET /api/v1/audit/recent?limit=30` every 2 seconds and renders events as cards in the "slide 14" struct layout (action / target / attendee_subject / token_acr / token_amr / detail).

Color rules (deck palette):
- Brand blue (`#0088D3`) left border: normal action.
- Amber (`#F2A65A`) left border: step-up tool (view_session_attendees, RATE_SESSION_REJECTED_NOT_STARTED).
- Red (`#E5645A`) left border: destructive (CANCEL_SESSION).

No build tooling: vanilla HTML/CSS/JS so the dashboard hot-reloads with the Quarkus dev mode.

---

## conference-mcp: MCP tools

Use `io.quarkiverse.mcp:quarkus-mcp-server-sse`. SSE preferred because the wire is visible during the talk. Tools map closely to conference-api endpoints but are described in natural language for the LLM.

Each tool has a name, an LLM-tuned description, an annotated parameter schema, and an implementation that calls conference-api through a REST client annotated with `@AccessToken` (so the user's bearer token rides along).

### Public tier tools
- `list_sessions(query?, speaker_name?)`
- `get_session(session_id)`
- `whats_on_now()`
- `whats_next(limit? = 3)`
- `find_speaker(name)`

### Attendee tier (role `attendee`)
- `bookmark_session(session_id)`
- `unbookmark_session(session_id)`
- `my_agenda()`
- `my_conflicts()`
- `rate_session(session_id, stars, comment?)`
- `my_ratings()`

### Speaker tier (role `speaker`)
- `my_session_feedback()`

### Speaker tier, step-up required
- `view_session_attendees(session_id)`
- `cancel_my_session(session_id, reason)`

Authorization checks live in a small `McpSecurity` bean that exposes `requireAuthenticated()`, `requireRole(role)`, and `requireStepUp()`. Step-up failure raises `io.quarkiverse.mcp.server.ToolCallException("insufficient_user_authentication: ...")` so the MCP client can surface a re-auth prompt.

Defense in depth: conference-api re-checks roles and acr too. The MCP server is a thin door; the lock is in the data API.

---

## conference-chat: AI chat client

The user-facing surface on stage. Quarkus app on port 8084 that combines a browser-loginable web-app, an MCP client to `conference-mcp`, and a switchable LLM.

### Responsibilities

1. **Authentication**: OIDC web-app flow with **PKCE S256**. Hitting `/` redirects to Keycloak. After login the user has a session cookie. The user's bearer token is reachable for outbound calls.
2. **MCP client (real wire protocol)**: connects to `conference-mcp` at `http://localhost:8081/mcp/sse` via `quarkus-langchain4j-mcp`. Tool discovery happens at startup and every reconnect. **No direct REST calls to conference-api from the chat backend.** Every tool invocation is an MCP call so the protocol story on stage is honest.
3. **Tool dispatch with two modes**:
   - **Scripted** (default): a deterministic `IntentMatcher` maps user prompt to one MCP tool with arguments. Bulletproof for stage even without an API key.
   - **LLM**: a LangChain4j `AiService` with the MCP tool provider attached. The selected chat model decides which MCP tool to call.
4. **Swappable LLM providers**, controlled by a single config property `chat.llm.provider` and a `/api/chat/provider` runtime toggle that picks from a registered set. Supported providers in the demo (configure all so the switch never blocks):
   - `anthropic` -- `quarkus-langchain4j-anthropic`, default model `claude-haiku-4-5-20251001`. Needs `ANTHROPIC_API_KEY`.
   - `openai` -- `quarkus-langchain4j-openai`, default model `gpt-4o-mini`. Needs `OPENAI_API_KEY`.
   - `ollama` -- `quarkus-langchain4j-ollama`, default model `llama3.1:8b`. Uses Quarkus Dev Services to boot Ollama locally; no API key. Acts as the safety net when the venue Wi-Fi is hostile.
   - `bedrock` (optional) -- `quarkus-langchain4j-bedrock`, default model `anthropic.claude-haiku`. Needs AWS credentials. Off by default.
5. **Audit-friendly UI**: every assistant turn renders a `tool-card` that shows the MCP request (`mcp.tool_call`) and the MCP response (`mcp.tool_result`) verbatim. The audience can match what they see on stage with what lands in the audit log on the second screen.

### Look and feel

Match the talk deck end to end. Palette and typography (lifted directly from the pptx):

| Token | Hex | Use |
|-------|-----|-----|
| `--bg`        | `#0E1116` | App background |
| `--surface`   | `#171B22` | Top bar, sidebar, cards |
| `--surface-2` | `#1F2530` | Quick-prompt buttons, mode track |
| `--border`    | `#2A313C` | Card outlines |
| `--fg`        | `#F5F7FA` | Primary text |
| `--fg-muted`  | `#9AA4B2` | Subtitles, field labels |
| `--fg-dim`    | `#5F6B7A` | Footers, time stamps |
| `--brand`     | `#0088D3` | Normal actions, links, accent |
| `--brand-2`   | `#2DA1E2` | Tool-name highlights |
| `--amber`     | `#F2A65A` | Step-up / sensitive tools, identity highlights |
| `--green`     | `#66D19E` | Liveness pulse |
| `--red`       | `#E5645A` | Destructive tools |
| Body type     | system sans / Calibri | Headings, prose |
| Mono type     | JetBrains Mono / Consolas | Tool cards, identifiers, audit rows |

A shield SVG icon (extracted from the deck) appears in both the chat top bar and the audit dashboard.

### Routes (served on port 8082)

- `GET /` -- serves `chat.html`. Authenticated; redirects to Keycloak when unauthenticated.
- `GET /chat.css`, `GET /chat.js`, `GET /favicon.ico` -- public assets.
- `GET /q/*` -- public (Dev UI, health, OIDC callback).
- `GET /api/chat/me` -- returns the current user's `subject`, `name`, `roles`, `acr`, `amr`, and a `provider`/`llmAvailable` hint for the UI.
- `GET /api/chat/quick-prompts` -- list of demo-ready prompts with their suggested tool and tier (`public`, `attendee`, `speaker`, `step-up`).
- `GET /api/chat/providers` -- list of configured LLM providers with their availability.
- `POST /api/chat/provider` -- body `{ "provider": "anthropic|openai|ollama|bedrock|scripted" }`. Switches the active provider. `scripted` means do not call any LLM.
- `POST /api/chat/send` -- body `{ "prompt": "...", "mode": "scripted|llm" }`. Returns a `ChatTurn` with the rendered tool call and result.

### Quick prompts

Pinned in the sidebar, drive the three demos. Each carries a `tier` so the UI can color-code it:

1. "What's happening right now?" -- `whats_on_now`, public.
2. "What's coming up next?" -- `whats_next`, public.
3. "Find the Practical MCP talk" -- `list_sessions`, public.
4. "Bookmark the JSpecify talk for me" -- `bookmark_session`, attendee.
5. "Show me my agenda" -- `my_agenda`, attendee.
6. "Do I have any conflicts?" -- `my_conflicts`, attendee.
7. "Rate the MCP Security talk 5 stars with comment 'great use of caffeine'" -- `rate_session`, attendee.
8. "Show feedback on my sessions" -- `my_session_feedback`, speaker.
9. "Who signed up for my Concurrency Crossroads deep dive?" -- `view_session_attendees`, step-up.
10. "Cancel my deep dive, reason is I want to go home early" -- `cancel_my_session`, step-up.

### Scripted intent matcher

Deterministic, regex-driven mapping prompt -> MCP tool with extracted arguments. Lives in `IntentMatcher`. Must:

- Handle the ten quick prompts verbatim and their natural rewordings.
- Extract `stars` (1 to 5) from phrases like "rate 5 stars".
- Extract `comment` from `comment "..."` patterns.
- Extract `session_query` from "the X talk" / "my Y deep dive" patterns and resolve it to a session_id via `list_sessions`.
- Recognise "cancel" + a "reason" tail.
- Fall back to a friendly "I'm not sure which tool to use" message instead of guessing.

### LLM mode (LangChain4j)

A LangChain4j `AiService` with the MCP tool provider attached. System prompt is short and stage-appropriate. Required behavior:

- Only the tools exposed by `conference-mcp` are callable. No invented tools.
- The selected provider is picked at request time from `chat.llm.provider` or the runtime override.
- If the active provider is unconfigured (no API key, Ollama not booted, etc.) the chat backend returns a structured `{"error":"provider_unavailable"}` and the UI offers to switch back to scripted.
- Tool call results are passed back to the model so it can summarise them in plain English in the assistant bubble. The raw tool call and result are also rendered verbatim in a `tool-card`.
- Streaming is optional. If implemented, stream into the assistant bubble; otherwise wait for the full turn.

### Health

- `GET /q/health/live` -- default smallrye liveness.
- `GET /q/health/ready` -- adds a check that the MCP client can reach `conference-mcp` and successfully list tools.

---

## Auth implementation details

### Keycloak setup

- Realm: `jprime`
- Clients (all confidential except where noted):
  - `conference-api` -- bearer-only, audience `conference-api`.
  - `conference-mcp` -- confidential, used for service-to-service calls and as the OIDC client for the Dev UI login on port 8081. **PKCE method `S256` required on the auth code flow.** Token exchange enabled.
  - `conference-chat` -- confidential, dedicated client for the chat web-app on port 8082. PKCE `S256` required. Redirect URIs include `http://localhost:8082/*` plus the Dev UI callback paths on 8080 and 8081.
  - `mcp-clients` -- public, allows Dynamic Client Registration. Used by MCP Inspector and any other MCP client.
- Realm roles: `attendee`, `speaker`.
- Client scopes: `attendee`, `speaker`.
- Users (seeded in the realm export):
  - `attendee1` / `attendee1` -- role `attendee`.
  - `willem.jan` / `willem.jan` -- roles `attendee` + `speaker`, linked to the Willem Jan speaker fixture, TOTP enabled with a known seed for rehearsal.
  - `admin-demo` / `admin` -- roles `attendee` + `speaker`, full demo operator account.
- Authentication flow: a custom "browser-step-up" flow that adds a conditional OTP execution gated on `acr=2` (mapped to `urn:mace:incommon:iap:silver` via `acr.loa.map`). The conditional OTP authenticator must match Keycloak's LoA expectation (`acr.loa.map` keys are ACR strings, values are integer levels).
- Token exchange enabled (`--features=token-exchange`).
- Realm `frontendUrl` is **not** hardcoded. Dev Services maps Keycloak to a dynamic port; if `frontendUrl` is hardcoded, OIDC discovery breaks.

Quarkus Dev Services boots Keycloak with this realm. All three apps configure the same `service-name=jprime-keycloak` so they share a single container.

### Single canonical realm file

There is **exactly one `keycloak-realm.json`** in the repository, at the monorepo root: `./keycloak-realm.json`. The three apps reference it through a filesystem path:

```
quarkus.keycloak.devservices.realm-path=../keycloak-realm.json
```

Quarkus Dev Services resolves `realm-path` first as a classpath resource, then as a filesystem path; the leading `../` lands on the shared file regardless of which app's working directory triggered the boot.

**No app may copy the realm file into its own `src/main/resources/`.** If a subagent needs a new client, role, or redirect URI, it edits the single shared file. The coordinator owns this file's lineage and reviews diffs from subagents before they land. This is non-negotiable: previous builds drifted into three out-of-sync copies and that path is closed.

### Quarkus security config

- **conference-api**: `quarkus-oidc` in `service` mode (bearer-only), audience `conference-api`, roles from claim path `realm_access/roles`.
- **conference-mcp**: `quarkus-oidc` in `service` mode. Validates incoming MCP-client tokens. REST clients into conference-api are annotated `@AccessToken` (via `quarkus-rest-client-oidc-token-propagation`) so the user's bearer token rides along. For step-up tools, the `McpSecurity` bean inspects `acr` and `amr` and raises `ToolCallException` on insufficiency.
- **conference-chat**: `quarkus-oidc` in `web-app` mode. `pkce-required=true`. Configured client is `conference-chat`. After login, the access token is available to the LangChain4j MCP client (set as the outbound `Authorization` header on every MCP call).

### PKCE notes (lessons from the first build)

- If the Keycloak client has `pkce.code.challenge.method=S256`, Quarkus **must** enable PKCE on its side (`quarkus.oidc.authentication.pkce-required=true`). Otherwise the auth code request lacks `code_challenge_method` and Keycloak rejects it with "Missing parameter: code_challenge_method".
- Either enforce PKCE on both sides for a given client, or neither. Mixing is the source of confusing 400s.
- Public clients (`mcp-clients`) are PKCE by definition because they hold no secret.

### Required ACR check

Step-up tools look for `acr=urn:mace:incommon:iap:silver` or `acr=2` or `amr` containing `mfa` / `otp`. On insufficiency:

- conference-api returns 401 with `WWW-Authenticate: Bearer error="insufficient_user_authentication", acr_values="urn:mace:incommon:iap:silver"`.
- conference-mcp surfaces a `ToolCallException` whose message starts with `insufficient_user_authentication:` so the chat client can recognise it and offer the re-auth path.
- conference-chat shows an amber step-up card with instructions to re-login at a higher ACR.

---

## Look and feel

Lifted from `practical-mcp-security.pptx`. The audit dashboard and chat UI both:

- Use the dark palette in the table above.
- Render a small shield SVG in the top bar with the brand title "Practical MCP Security in Action / jPrime 2026".
- Format tool calls / audit events as monospace struct cards with key/value rows in two columns.
- Use a pulsing green dot to indicate "live".
- Use the slide 14 pull quote "It wasn't the AI. It was me." as the chat hero subtitle and as the audit dashboard hero.
- Color-code by sensitivity: brand blue (normal), amber (step-up), red (destructive).

No emoji. No em dashes. Body font is system sans (Calibri fallback). Monospace is JetBrains Mono with a Consolas fallback so it works on Windows.

---

## Demo flow on stage

### Demo 1: Public schedule lookup (~8 min)

1. Open `http://localhost:8082/`. Get redirected to Keycloak. Log in as `attendee1 / attendee1`.
2. Land in the chat UI. Click "What's happening right now?". The chat backend invokes `whats_on_now` on `conference-mcp` over real MCP. The result card shows the tool call and the response.
3. Click "What's coming up next?".
4. Open a second tab on `http://localhost:6274/` (MCP Inspector) and walk through Dynamic Client Registration against `http://localhost:8081/mcp/sse`. Show the audience the PKCE code in the URL bar.

Talking points: PKCE, DCR, why these matter for AI clients.

The audit dashboard stays mostly quiet (read-only tools do not audit).

### Demo 2: Personal agenda with token propagation (~10 min)

1. Log in (or re-login) as `willem.jan / willem.jan`.
2. Click "Bookmark the JSpecify talk for me". A blue `BOOKMARK_ADD` event flies onto `/audit-live/` within 2 seconds.
3. Click "Show me my agenda".
4. Click "Do I have any conflicts?" after bookmarking a second overlapping session.
5. Click "Rate the MCP Security talk 5 stars with comment 'great use of caffeine'". A `RATE_SESSION` event appears with `attendee_subject=willem.jan` and `token_acr=1`.
6. Turn to the second screen, read the event aloud.

Punchline: not "AI rated it 5 stars" but "Willem Jan rated it 5 stars, executed by an AI on his behalf, with his token, fully auditable".

### Demo 3: Step-up auth (~8 min)

1. Stay as `willem.jan`, no MFA yet (`acr=1`).
2. Click "Show feedback on my sessions". Works.
3. Click "Who signed up for my Concurrency Crossroads deep dive?". The MCP tool returns `insufficient_user_authentication`. The chat UI shows an amber step-up card with the next step.
4. Log out and back in, this time satisfying TOTP. `acr=urn:mace:incommon:iap:silver`. Retry the prompt. The attendee list comes back.
5. Click "Cancel my deep dive, reason is I want to go home early". The chat UI shows a red destructive card. The audit log shows `CANCEL_SESSION` with the strong acr.
6. Click the prompt again to reverse the cancellation, showing the toggle behavior and that the action is reversible. Audit log shows `CANCEL_SESSION_UNDONE`.

Talking points: step-up is the spec-level answer to "OAuth is for humans". Same protocol, different acr requirement, server-driven.

### Safety nets

- Pre-recorded video of each demo at 1.5x speed, on a fallback browser tab.
- All demos work offline against the seeded database. No external dependency at demo time except optional LLM providers; the scripted mode is always available.
- Seed data includes a "well-known" current time override (env var `DEMO_NOW=2026-06-03T10:45:00+03:00`) so `whats_on_now` always returns something interesting regardless of when the talk is rehearsed.
- The LLM provider switcher in the chat UI lets the speaker hot-swap from Anthropic to Ollama if the venue blocks outbound traffic.

---

## Tech stack and conventions

- **Java**: 25 (or the most-current LTS Quarkus 3.x supports cleanly; confirm with the latest Quarkus release before scaffolding).
- **Quarkus**: 3.x latest stable. Use the platform BOM.
- **Build**: Maven.
- **Tests**: JUnit 5, REST Assured, Quarkus Dev Services (Testcontainers for Postgres, Keycloak Dev Services for OIDC). Tests disable OIDC with `%test.quarkus.oidc.tenant-enabled=false` so they use `@TestSecurity`.
- **Code style**: no em dashes in any generated text, comments, or documentation.
- **Repo layout**: monorepo with `conference-api/`, `conference-mcp/`, `conference-chat/`, `keycloak-realm.json` (the single shared realm export), `SPEC.md`, `RUNBOOK.md`, `DEMO_RESET.sh`. **No** `infra/` directory; everything is auto-provisioned by Dev Services. The realm file lives once at the monorepo root and is referenced by all three apps via `quarkus.keycloak.devservices.realm-path=../keycloak-realm.json`.

### Extensions per app

**conference-api**
- `quarkus-rest`, `quarkus-rest-jackson`
- `quarkus-hibernate-orm-panache`
- `quarkus-jdbc-postgresql`
- `quarkus-oidc`
- `quarkus-smallrye-openapi`
- `quarkus-smallrye-health`
- `quarkus-hibernate-validator`

No Flyway. Hibernate owns the schema in dev and test (`drop-and-create`). In prod the schema is `none` and would be applied out of band; that is out of scope here.

**conference-mcp**
- `quarkus-rest-client-jackson`
- `quarkus-rest-client-oidc-token-propagation`
- `quarkus-oidc`
- `quarkiverse-mcp-server-sse` (the `io.quarkiverse.mcp:quarkus-mcp-server-sse` artifact)
- `quarkus-hibernate-validator`
- `quarkus-smallrye-health`

**conference-chat**
- `quarkus-rest`, `quarkus-rest-jackson`
- `quarkus-rest-client-jackson`, `quarkus-rest-client-oidc-token-propagation` (only used for direct fallback calls and audit-log reads, not for tool invocation)
- `quarkus-oidc`
- `quarkus-smallrye-health`
- `quarkus-langchain4j-mcp` (the MCP client)
- `quarkus-langchain4j-anthropic`
- `quarkus-langchain4j-openai`
- `quarkus-langchain4j-ollama`
- `quarkus-langchain4j-bedrock` (optional, off by default)

### Configuration knobs

| Env var | Purpose | Default |
|---------|---------|---------|
| `DEMO_NOW` | Override the demo clock used by `whats_on_now`/`whats_next` and the rating cutoff. ISO with `+03:00` zone. | unset |
| `CONFERENCE_API_URL` | Where conference-mcp / conference-chat find conference-api. | `http://localhost:8080` |
| `CONFERENCE_MCP_URL` | Where conference-chat finds the MCP SSE endpoint. | `http://localhost:8081/mcp/sse` |
| `ANTHROPIC_API_KEY` | Anthropic provider key. | unset (provider disabled) |
| `OPENAI_API_KEY` | OpenAI provider key. | unset (provider disabled) |
| `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY` | Bedrock credentials. | unset (provider disabled) |
| `CHAT_LLM_PROVIDER` | Initial provider on boot: `scripted`, `anthropic`, `openai`, `ollama`, `bedrock`. | `scripted` |

---

## Build process: one subagent per app

The three apps are independent enough that they should be built by **three separate subagents** working in parallel, with a fourth coordinator pass at the end to wire them together. This keeps the main agent's context lean and makes each piece independently verifiable.

### Subagent assignments

1. **`conference-api` subagent**: data model, Flyway migrations, REST endpoints, seeders, importer, audit-live dashboard, integration tests.
2. **`conference-mcp` subagent**: MCP tools, security checks, REST clients with token propagation, health checks, wiring tests.
3. **`conference-chat` subagent**: OIDC web-app, LangChain4j MCP client, provider registry, intent matcher, chat SPA, wiring tests.
4. **Coordinator pass** (main agent or a fourth subagent): Keycloak realm export with all four clients (the three apps plus `mcp-clients`), shared Dev Services config, RUNBOOK, DEMO_RESET, top-level README.

Each subagent must be briefed with this SPEC.md and a short pointer to its app subtree. The subagent owns everything under `<app>/` and the corresponding section of the realm export.

### Definition of done (per subagent)

A subagent's work is **only complete** when both of the following pass for its app, in this order:

1. **`./mvnw test` is green.** All tests pass, no errors, no flaky skips. Failure summaries go back to the subagent for one fix-and-verify cycle before escalating.
2. **`./mvnw quarkus:dev` boots and responds.** The subagent starts the app in dev mode (or asks the harness to via `quarkus_start`), waits for the "started in Ns" log line, and then hits the app's primary endpoints with curl to confirm a 2xx or expected 4xx:
   - conference-api (port 8080): `GET /api/v1/sessions` returns the seeded schedule; `GET /audit-live/` returns the dashboard HTML; `GET /q/health/ready` is UP.
   - conference-mcp (port 8081): `GET /q/health/ready` is UP and its check confirms conference-api is reachable; `GET /mcp/sse` accepts an SSE connection.
   - conference-chat (port 8082): `GET /` redirects to Keycloak (302), `GET /api/chat/quick-prompts` returns 401 without a session and 200 with one, `GET /q/health/ready` reports that MCP tool listing succeeded.

Subagents should report back with:
- The test summary (test counts per class, any failures).
- The dev-mode smoke output (which endpoints returned what).
- Any deviations from the spec, with a one-line justification each.

A subagent that cannot get both green flags must surface the blocker rather than silently mark the work done. Do not skip tests or hide failures behind `-DskipTests`.

### Coordination

- Subagents run in parallel but **only the coordinator edits `./keycloak-realm.json`**. There is exactly one realm file at the monorepo root; no per-app copies. A subagent that needs a new client / role / redirect URI submits the diff to the coordinator rather than editing in isolation.
- Shared Dev Services `service-name=jprime-keycloak` and `shared=true` are mandatory so only one Keycloak container boots.
- When all three subagents declare done, the coordinator does an integration pass:
  - Start all three apps with `quarkus_start`.
  - Drive each demo flow manually (or via curl scripts) against the running stack.
  - Confirm `/audit-live/` updates within 2 seconds of each tool call.
  - Confirm the chat client can switch LLM providers without restart.

## What Claude Code should produce

1. **Three Quarkus apps** as Maven projects, all buildable with `./mvnw quarkus:dev`.
2. **No `docker compose`** anywhere. All infra via Quarkus Dev Services. Shared Keycloak container across all three apps via `service-name=jprime-keycloak`.
3. **One** Keycloak realm export at the monorepo root (`./keycloak-realm.json`), referenced by all three apps via `../keycloak-realm.json`. Includes the `conference-chat` confidential client with PKCE S256.
4. **Schedule importer + SQL/Java seed** as described above. Static schedule must include sessions co-located with Willem Jan as a speaker.
5. **Full REST API** in conference-api with OpenAPI annotations, plus the `/audit-live/` second-screen dashboard.
6. **MCP tools** in conference-mcp with LLM-tuned descriptions and `McpSecurity` enforcement.
7. **conference-chat** app:
   - OIDC web-app login with PKCE S256.
   - LangChain4j MCP client to conference-mcp (real MCP wire, no shortcuts to conference-api).
   - Provider registry with at minimum `anthropic`, `openai`, `ollama` configured.
   - Scripted intent matcher as the default mode.
   - Chat SPA matching the deck palette and typography.
   - Quick-prompt sidebar covering all three demos.
   - Mode toggle (Scripted / LLM) plus a provider dropdown.
8. **Health checks** on every app. conference-mcp's readiness verifies it can reach conference-api. conference-chat's readiness verifies it can list MCP tools.
9. **Integration tests** for conference-api endpoints with `@QuarkusTest` and `@TestSecurity`. Smoke test for conference-mcp wiring. Smoke test for conference-chat (intent matcher unit tests plus a wiring test).
10. **READMEs** per app and a top-level `RUNBOOK.md` with the exact demo steps and env vars.
11. **`DEMO_RESET.sh`** that hits the conference-api dev admin endpoint to wipe and re-seed.

## Open questions

1. The latest MCP authorization spec details for step-up challenges: confirm the exact wire format Quarkus MCP server emits once the upstream test lands. Default to following Quarkus' lead.
2. Whether to ship a Bedrock provider in the default build. Currently optional, off by default.
3. Whether to stream LLM responses into the assistant bubble. Currently sync; streaming is a nice-to-have.

## Out of scope

- Production deployment topology.
- Real TOTP enrollment UX. The demo uses a known seed.
- A Bedrock or Vertex AI provider beyond the optional langchain4j-bedrock module.
- Real DCR for the chat client. The chat client uses a static confidential Keycloak client; only `mcp-clients` is DCR-enabled for the MCP Inspector flow.
