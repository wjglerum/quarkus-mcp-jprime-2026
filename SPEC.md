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
   - quarkus-mcp-server-http
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
- action (string, eg `RATE_SESSION`, `CANCEL_SESSION`, `CANCEL_SESSION_UNDONE`, `BOOKMARK_ADD`, `BOOKMARK_REMOVE`, `CANCEL_SESSION_ATTEMPTED`, `RATE_SESSION_REJECTED_NOT_STARTED`)
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

Two seeders, both idempotent, both applied on startup. **No live jprime.io scrape at runtime, no jsoup dependency.** The demo never depends on the internet for schedule data.

1. **Static schedule seeder**: if `Session.count() == 0`, load `seed/jprime-2026-agenda.json` from the classpath and persist every speaker and session listed there. The JSON is a one-time **snapshot of the real jprime.io agenda** for jPrime 2026 (3-4 June, Sofia Tech Park): ~32 talks across two days, ~27 speakers, including Willem Jan on the MCP talk (Hall B, day 1 10:00) and on the Concurrency Crossroads deep dive (Workshops & Deep Dives, day 1 15:20). Breaks, registration, raffle, and networking blocks are excluded -- only sessions with a speaker. Co-speakers are reduced to the primary speaker to fit the one-speaker-per-session model. Refreshing the schedule is a manual step: re-scrape jprime.io and overwrite the JSON.
2. **Demo data seeder**: if `Attendee.count() == 0`, create about 10 fake attendees, around 15 bookmarks, and around 25 ratings, with at least 5 ratings on Willem Jan's sessions for the speaker feedback demo.

The JSON shape is:

```json
{
  "year": 2026,
  "timezone": "Europe/Sofia",
  "speakers": [
    { "name": "Willem Jan Glerum", "bio": "..." }
  ],
  "sessions": [
    {
      "title": "Practical MCP Security in Action",
      "abstract": "optional; falls back to title when missing",
      "speaker": "Willem Jan Glerum",
      "room": "Hall B",
      "startsAt": "2026-06-03T10:00:00+03:00",
      "endsAt":   "2026-06-03T10:50:00+03:00"
    }
  ]
}
```

If the JSON is missing, malformed, or empty the seeder logs a warning and persists nothing; the app still boots. A session that references a speaker name not present in the `speakers` array auto-creates the Speaker row so a JSON typo never silently drops a talk.

No DEMO_RESET script and no admin reseed endpoint. Between rehearsals, restart `conference-api` (or wipe the Dev Services Postgres container with `docker rm -f $(docker ps --filter "label=quarkus-dev-service.postgresql" -q)`). Dev Services hands out a fresh container on the next boot, both seeders re-run, and the demo starts clean. The schema is owned by Hibernate (`drop-and-create`), so no migrations need to be re-applied.

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

No admin reseed endpoint. Resetting demo state is a Dev Services container restart (see "Data ingestion").

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

Use `io.quarkiverse.mcp:quarkus-mcp-server-http`. SSE preferred because the wire is visible during the talk. Tools map closely to conference-api endpoints but are described in natural language for the LLM.

Each tool has a name, an LLM-tuned description, an annotated parameter schema, and an implementation that calls conference-api through a REST client annotated with `@AccessToken` (so the user's bearer token rides along).

### Public tier tools
- `list_sessions(query?, speaker_name?)`
- `get_session(session_id)`
- `whats_on_now()`
- `whats_next(limit? = 3)`

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

Authorization uses **standard Jakarta annotations** wherever possible:

- `@jakarta.annotation.security.RolesAllowed("attendee")` on attendee-tier tools.
- `@jakarta.annotation.security.RolesAllowed("speaker")` on speaker-tier tools.
- `@io.quarkus.security.Authenticated` on tools that need any logged-in user but no specific role.

These are processed by `quarkus-security` via CDI interceptors. A missing role surfaces as a `ForbiddenException` which the MCP server maps to a structured error.

**Step-up** is the only check that needs custom code, because no standard annotation models the `acr` / `amr` claims, and the MCP wire wants the failure as a `ToolCallException("insufficient_user_authentication: ...")` so the client recognises it. Implement it as a single tiny helper:

```java
@ApplicationScoped
public class StepUp {
    @Inject JsonWebToken jwt;

    public void require() {
        Object acr = jwt.getClaim("acr");
        if ("2".equals(String.valueOf(acr))
                || "urn:mace:incommon:iap:silver".equals(String.valueOf(acr))) return;
        Object amr = jwt.getClaim("amr");
        if (amr instanceof Iterable<?> it) {
            for (Object o : it) {
                String s = String.valueOf(o);
                if ("mfa".equals(s) || "otp".equals(s)) return;
            }
        }
        throw new ToolCallException(
                "insufficient_user_authentication: this tool requires step-up MFA. "
                        + "Re-authenticate with acr_values=urn:mace:incommon:iap:silver and retry.");
    }
}
```

Call `stepUp.require()` at the top of `view_session_attendees` and `cancel_my_session`. Do not build an `McpSecurity` umbrella bean or a `@StepUp` annotation/interceptor: with only two step-up tools, the programmatic helper is the smallest reasonable surface.

Defense in depth: conference-api re-checks roles and acr too. The MCP server is a thin door; the lock is in the data API.

---

## conference-chat: AI chat client

The user-facing surface on stage. Quarkus app on port 8082 that combines a browser-loginable web-app, an MCP client to `conference-mcp`, and a switchable LLM.

### Responsibilities

1. **Authentication**: OIDC web-app flow with **PKCE S256**. Hitting `/` redirects to Keycloak. After login the user has a session cookie. The user's bearer token is reachable for outbound calls.
2. **MCP client (real wire protocol)**: connects to `conference-mcp` at `http://localhost:8081/mcp/sse` via `quarkus-langchain4j-mcp`. Tool discovery happens at startup and every reconnect. **No direct REST calls to conference-api from the chat backend.** Every tool invocation is an MCP call so the protocol story on stage is honest.

   The chat backend resolves `session_query` to `session_id` over the MCP wire when an intent extracts a free-text session reference but the target tool (`bookmark_session`, `rate_session`, `view_session_attendees`, `cancel_my_session`, etc) needs a numeric id. The dispatcher first calls the MCP `list_sessions` tool with the query, takes the first hit's `id`, swaps it into args, then calls the target tool. The chain stays on MCP end-to-end. `session_query = "current"` short-circuits to `whats_on_now` first.

   Use the langchain4j **1.14+ API** when reading the `ToolProviderResult`: `aiServiceTools()` returns `List<AiServiceTool>` with `name()`, `toolSpecification()`, and `toolExecutor()` per entry. The older `tools()` / `toolExecutorByName(String)` accessors are deprecated and will be removed in a future release.
3. **Tool dispatch with two modes**:
   - **Scripted** (default): a deterministic `IntentMatcher` maps user prompt to one MCP tool with arguments. Bulletproof for stage even without an API key.
   - **LLM**: a LangChain4j `AiService` with the MCP tool provider attached. The selected chat model decides which MCP tool to call.
4. **Swappable LLM providers**, controlled by a single config property `chat.llm.provider` and a `/api/chat/provider` runtime toggle that picks from a registered set. Three providers, configure all so the switch never blocks:
   - `anthropic` -- `quarkus-langchain4j-anthropic`, default model `claude-haiku-4-5-20251001`. Needs `ANTHROPIC_API_KEY`.
   - `openai` -- `quarkus-langchain4j-openai`, default model `gpt-4o-mini`. Needs `OPENAI_API_KEY`.
   - `ollama` -- `quarkus-langchain4j-ollama`, default model `llama3.1:8b`. Uses Quarkus Dev Services to boot Ollama locally; no API key. Acts as the safety net when the venue Wi-Fi is hostile.
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

A shield SVG icon (extracted from the deck) appears in both the chat top bar and the audit dashboard. The full visual reference, mapping each slide to a UI component, lives in the **Visual reference** section near the end of this spec; treat that section as the design authority when building or polishing the SPAs.

### Templating: Qute for the shell, lean JS for interactivity

The chat SPA is rendered with **Qute templates** server-side for everything that can be known at request time, and a small amount of vanilla JS for the chat send / transcript updates. No Node toolchain, no React, no htmx.

- **Add both `quarkus-qute` and `quarkus-rest-qute` to the conference-chat extension list.** Without `quarkus-rest-qute` the JAX-RS layer has no message-body writer for `TemplateInstance` and serializes the impl class' `toString()` ("`InjectableTemplateInstanceImpl@...`") into the response.
- The HTML shell (`chat.html`) lives under `src/main/resources/templates/ChatPageResource/chat.html` (Qute's `@CheckedTemplate` convention places templates under the enclosing class name). It is rendered by a Qute-backed REST resource on `GET /` and receives:
  - `me` -- the resolved `Me` record (subject, name, roles, acr, amr).
  - `quickPrompts` -- the list of `QuickPrompt` records (label, suggestedTool, tier).
  - `providers` -- the configured LLM providers and their availability.
  - `activeProvider` and `activeMode` -- the current selection.
- The identity panel, the quick-prompt buttons, the provider dropdown, and the mode pill are all rendered by Qute. The browser receives a fully formed page on first load -- no flash-of-empty UI while JS fetches `/api/chat/me` and `/api/chat/quick-prompts`.
- Vanilla JS (still under `META-INF/resources/chat.js`) only handles:
  - Submitting the composer form to `POST /api/chat/send`.
  - Appending the user bubble and the assistant bubble (cloned from `<template>` elements in the Qute output).
  - Toggling the mode pill class and posting to `POST /api/chat/provider` when the dropdown changes.
- `/api/chat/quick-prompts` and `/api/chat/providers` remain available for completeness (the JS still uses them to refresh after a provider change), but they are no longer the source of truth for the initial render.
- Use Qute's type-safe templates where reasonable: declare a `Templates` interface with method-per-page binding so the IDE catches missing parameters.

The audit-live dashboard stays vanilla static HTML for now (no dynamic shell content needed; the JS poll renders everything).

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
- The raw tool call and result are rendered verbatim in a `tool-card` so the audience can match what they see on stage with the audit log. Feeding the tool result back to the model for a plain-English summary is optional; the demo's honesty comes from the tool card, not from a natural-language wrapper.
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
- Realm roles: `attendee`, `speaker`. (No custom `attendee` / `speaker` client scopes; roles flow through the realm-default `roles` scope into `realm_access.roles`.)
- Users (seeded in the realm export):
  - `attendee1` / `attendee1` -- role `attendee`.
  - `willem.jan` / `willem.jan` -- roles `attendee` + `speaker`, linked to the Willem Jan speaker fixture. **Not TOTP-enrolled in the seeded realm.**
  - `admin-demo` / `admin` -- roles `attendee` + `speaker`, full demo operator account.
- Authentication flow: the realm uses Keycloak's default `browser` flow. There is no custom `browser-step-up` flow shipped with the realm. The step-up demo surfaces as a 401 + `WWW-Authenticate: Bearer error=insufficient_user_authentication, acr_values=urn:mace:incommon:iap:silver` from `conference-api` and a `ToolCallException("insufficient_user_authentication: ...")` from `conference-mcp`. The realm ships only the `acr.loa.map` entry (`urn:mace:incommon:iap:silver` -> level 2); wiring an actual ACR-gated OTP execution **and** enrolling TOTP on `willem.jan` is a manual pre-flight step the speaker performs via the Keycloak account console before the talk, not something the realm ships.
- Token exchange is **not** enabled. No client uses service-account or direct-access grants.
- Realm `frontendUrl` is **not** hardcoded. Dev Services maps Keycloak to a dynamic port; if `frontendUrl` is hardcoded, OIDC discovery breaks.

Quarkus Dev Services boots Keycloak with this realm. All three apps configure the same `service-name=jprime-keycloak` **and the same `image-name=quay.io/keycloak/keycloak:26.5.7`** so they share a single container. Without the pinned image the apps default to whatever Keycloak version their Quarkus platform pins, and the conference-chat (whose `quarkus-langchain4j-bom` may pin a different Quarkus version than the other two) ends up on a different Keycloak version, so `shared=true` silently spawns two containers.

### Building `keycloak-realm.json` from the Dev Services default

The realm export is **derived from Dev Services' own default `quarkus` realm**, not hand-rolled. The default realm comes with the full canonical scope set (`basic`, `profile`, `email`, `roles`, `web-origins`, `acr`, `microprofile-jwt`, etc.) and the protocol mappers that emit `sub`, `preferred_username`, `name`, and `email`. Hand-rolling a realm and forgetting any of those mappers means access tokens come back missing `sub` and the audit pipeline silently writes `null` for `attendee_subject`.

Procedure to (re)build the file:

1. Temporarily comment out `quarkus.keycloak.devservices.realm-path` in conference-api's `application.properties` and set `quarkus.keycloak.devservices.realm-name=quarkus`. Start conference-api. Dev Services boots Keycloak with its own default realm.
2. Use the Keycloak admin REST API (`admin` / `admin` on the master realm) to call `POST /admin/realms/quarkus/partial-export?exportClients=true&exportGroupsAndRoles=true`. Save the response.
3. Transform the export to our needs: rename `realm` to `jprime`, strip composites from `default-roles-quarkus` and rename it to `default-roles-jprime` (the originals reference Keycloak-internal client roles you may or may not be bringing over), drop the `id` fields, keep all 14 default client scopes, replace the auto-created `conference-mcp` service-account client with our four real clients, replace users with `attendee1` / `willem.jan` / `admin-demo`, add the `attendee` and `speaker` scopes as **optional** scopes on each app client.
4. Set realm-level defaults explicitly: `defaultDefaultClientScopes = [basic, profile, email, roles, web-origins, acr]` and `defaultOptionalClientScopes = [offline_access, microprofile-jwt, attendee, speaker]`.
5. Save as `keycloak-realm.json` at the monorepo root. Restore the realm-path config.

A repeatable Python script lives outside the repo (one-shot scaffolding tool). The output of step 5 is the canonical source.

### Scope-config rules learned the hard way

- **Do NOT set explicit `defaultClientScopes` on a client to just `["profile", "email"]`.** That overrides the realm-level defaults and strips `basic` along with it, which kills the `sub` claim mapper. Either include the full standard set or omit `defaultClientScopes` entirely so realm defaults apply.
- **In Quarkus `quarkus.oidc.authentication.scopes`, only list the client's optional scopes.** Quarkus always prepends `openid`; the realm defaults (profile, email, basic, roles, web-origins, acr) are applied by Keycloak automatically. Listing `openid,profile,email,attendee,speaker` here causes Keycloak to reject the auth request with `Invalid scopes: openid openid profile email attendee speaker` (note the duplicated `openid` and the `profile`/`email` that are default-but-not-optional). The correct setting in `conference-chat/application.properties` is:
  ```
  quarkus.oidc.authentication.scopes=attendee,speaker
  ```
- **Realm `frontendUrl` must not be hardcoded.** Dev Services maps Keycloak to a random host port; a hardcoded `frontendUrl: http://localhost:8081` makes the realm emit token URLs that don't resolve.
- **Imported `acr.loa.map` keys are ACR strings, values are integer levels.** `"urn:mace:incommon:iap:silver": "2"` is correct; the reverse blows up at first authentication with `Cannot deserialize value of type Integer from String`.

### Single canonical realm file

There is **exactly one `keycloak-realm.json`** in the repository, at the monorepo root: `./keycloak-realm.json`. The three apps reference it through a filesystem path:

```
quarkus.keycloak.devservices.realm-path=../keycloak-realm.json
```

Quarkus Dev Services resolves `realm-path` first as a classpath resource, then as a filesystem path; the leading `../` lands on the shared file regardless of which app's working directory triggered the boot.

**No app may copy the realm file into its own `src/main/resources/`.** If a subagent needs a new client, role, or redirect URI, it edits the single shared file. The coordinator owns this file's lineage and reviews diffs from subagents before they land. This is non-negotiable: previous builds drifted into three out-of-sync copies and that path is closed.

### Quarkus security config

- **conference-api**: `quarkus-oidc` in `service` mode (bearer-only), audience `conference-api`, roles from claim path `realm_access/roles`.
- **conference-mcp**: `quarkus-oidc` in `service` mode. Validates incoming MCP-client tokens. REST clients into conference-api are annotated `@AccessToken` (via `quarkus-rest-client-oidc-token-propagation`) so the user's bearer token rides along. For step-up tools, the tiny `StepUp.require()` helper inspects `acr` and `amr` and raises `ToolCallException("insufficient_user_authentication: ...")` on insufficiency.
- **conference-chat**: `quarkus-oidc` in `web-app` mode. `pkce-required=true`. Configured client is `conference-chat`. After login, the access token is available to the LangChain4j MCP client (set as the outbound `Authorization` header on every MCP call).

### PKCE notes (lessons from the first build)

- If the Keycloak client has `pkce.code.challenge.method=S256`, Quarkus **must** enable PKCE on its side (`quarkus.oidc.authentication.pkce-required=true`). Otherwise the auth code request lacks `code_challenge_method` and Keycloak rejects it with "Missing parameter: code_challenge_method".
- Either enforce PKCE on both sides for a given client, or neither. Mixing is the source of confusing 400s.
- Public clients (`mcp-clients`) are PKCE by definition because they hold no secret.

### OIDC discovery cache after Keycloak restarts

`quarkus-oidc` caches the resolved issuer URL and JWKS endpoint at startup. When the Keycloak Dev Services container restarts onto a new host port (eg after a realm rebuild), the running apps still validate tokens against the old URL and every authenticated request returns 401. **Always restart all three apps after the Keycloak container is replaced.** Use `quarkus_restart` (or `quarkus_stop` + `quarkus_start`) per app. Do not try to keep the apps running across a Keycloak swap.

### Reading JWT claims safely

`JsonWebToken.getClaim(String)` has a generic return type `<T>` that the compiler infers from context. Passing the result straight into `String.valueOf(...)` makes the compiler pick `String.valueOf(char[])` because it is more specific than `String.valueOf(Object)`, and the runtime ClassCastException ("class java.lang.String cannot be cast to class [C") is incomprehensible the first time you see it.

Two rules:

1. Never call `String.valueOf(jwt.getClaim(name))`. Either assign to an explicitly typed local first (`Object raw = jwt.getClaim(name); String s = String.valueOf(raw);`) or use a type witness (`jwt.<String>getClaim(name)`).
2. Prefer the `Optional`-returning `JsonWebToken.<Object>claim(name)` over `getClaim`. Wrap that in a tiny app-local helper:

   ```java
   public final class JwtClaims {
       public static String string(JsonWebToken jwt, String name, String orElse) {
           return jwt.<Object>claim(name).map(Object::toString)
                   .filter(s -> !s.isBlank()).orElse(orElse);
       }
       public static List<String> stringList(JsonWebToken jwt, String name) { /* ... */ }
   }
   ```

   The helper lives at `conference-chat/src/main/java/nl/lunatech/jprime/chat/security/JwtClaims.java`. `ChatPageResource` and `ChatResource` both use it for `acr` and `amr`. The conference-api / conference-mcp apps read claims via `SecurityIdentity.getPrincipal().getName()` and `JsonWebToken.getSubject()` directly, so they don't need the helper. Add it there too if you ever start formatting raw claims for display.

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
- **Quarkus**: 3.x latest stable. Use the platform BOM (`io.quarkus.platform:quarkus-bom`) for everything Quarkus-managed.
- **Standalone Quarkiverse BOMs, not platform-aligned ones**: both conference-chat (langchain4j) and conference-mcp (mcp-server) import directly from the Quarkiverse, not from `io.quarkus.platform`. The platform BOMs lag the standalone releases (eg the platform `quarkus-langchain4j-bom:3.35.4` pinned `quarkus-langchain4j-*` to 1.9.2 while the standalone was already on 1.10.0). Bump these BOMs independently of Quarkus core:
  ```xml
  <!-- conference-chat -->
  <dependency>
      <groupId>io.quarkiverse.langchain4j</groupId>
      <artifactId>quarkus-langchain4j-bom</artifactId>
      <version>1.10.0</version>
      <type>pom</type>
      <scope>import</scope>
  </dependency>

  <!-- conference-mcp -->
  <dependency>
      <groupId>io.quarkiverse.mcp</groupId>
      <artifactId>quarkus-mcp-server-bom</artifactId>
      <version>1.12.1</version>
      <type>pom</type>
      <scope>import</scope>
  </dependency>
  ```
  Keep the Quarkus core platform BOM imported in the same `dependencyManagement`; the two are complementary.

- **MCP server artifact rename**: in `quarkus-mcp-server` 1.12.x, `quarkus-mcp-server-sse` was relocated to **`quarkus-mcp-server-http`** (HTTP transports consolidated). Use the new name in `conference-mcp/pom.xml`; the SSE endpoint stays at `/mcp/sse`.
- **Build**: Maven.
- **Tests**: JUnit 5, REST Assured, Quarkus Dev Services (Testcontainers for Postgres, Keycloak Dev Services for OIDC). Tests disable OIDC with `%test.quarkus.oidc.tenant-enabled=false` so they use `@TestSecurity`.
- **Code style**: no em dashes in any generated text, comments, or documentation.
- **DTOs**: each REST/MCP DTO is a **top-level class (or record) in its own file**, inside a dedicated `dto` package per app:
  - conference-api: `nl.lunatech.jprime.api.dto`
  - conference-mcp: `nl.lunatech.jprime.mcp.dto`
  - conference-chat: `nl.lunatech.jprime.chat.dto`
  Do not nest DTOs as inner classes under a `Dtos` umbrella holder. One file per DTO. Static `of(Entity)` factory methods stay on the DTO itself.
- **Repo layout**: monorepo with `conference-api/`, `conference-mcp/`, `conference-chat/`, a thin aggregator `pom.xml` at the root (lists the three apps as `<modules>` so `./mvnw verify` from the root builds and tests everything), `keycloak-realm.json` (the single shared realm export), `SPEC.md`, `RUNBOOK.md`. **No** `infra/` directory and **no** `DEMO_RESET.sh`; everything is auto-provisioned by Dev Services, and resetting state is a Dev Services container restart. The realm file lives once at the monorepo root and is referenced by all three apps via `quarkus.keycloak.devservices.realm-path=../keycloak-realm.json`. The aggregator pom adds no dependencyManagement; each child stays self-contained on its own Quarkus / Quarkiverse BOMs.

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
- `quarkiverse-mcp-server-sse` (the `io.quarkiverse.mcp:quarkus-mcp-server-http` artifact)
- `quarkus-hibernate-validator`
- `quarkus-smallrye-health`

**conference-chat**
- `quarkus-rest`, `quarkus-rest-jackson`
- `quarkus-rest-client-jackson`, `quarkus-rest-client-oidc-token-propagation` (only used for direct fallback calls and audit-log reads, not for tool invocation)
- `quarkus-oidc`
- `quarkus-qute` (server-renders the chat shell)
- `quarkus-rest-qute` (JAX-RS bridge -- required, otherwise `TemplateInstance` returned from a resource gets `toString()`-rendered into the response body)
- `quarkus-smallrye-health`
- `quarkus-langchain4j-mcp` (the MCP client)
- `quarkus-langchain4j-anthropic`
- `quarkus-langchain4j-openai`
- `quarkus-langchain4j-ollama`

### Configuration knobs

| Env var | Purpose | Default |
|---------|---------|---------|
| `DEMO_NOW` | Override the demo clock used by `whats_on_now`/`whats_next` and the rating cutoff. ISO with `+03:00` zone. In `%dev` the default is **`2026-06-03T10:45:00+03:00`** (mid-demo Wednesday morning) so ratings work without env setup. Override only when rehearsing other times. | `2026-06-03T10:45:00+03:00` in dev, unset elsewhere |
| `CONFERENCE_API_URL` | Where conference-mcp / conference-chat find conference-api. | `http://localhost:8080` |
| `CONFERENCE_MCP_URL` | Where conference-chat finds the MCP SSE endpoint. | `http://localhost:8081/mcp/sse` |
| `ANTHROPIC_API_KEY` | Anthropic provider key. | unset (provider disabled) |
| `OPENAI_API_KEY` | OpenAI provider key. | unset (provider disabled) |
| `CHAT_LLM_PROVIDER` | Initial provider on boot: `scripted`, `anthropic`, `openai`, `ollama`. | `scripted` |

---

## End-to-end tests (Playwright)

Browser-driven e2e lives **inside `conference-chat`'s test phase** via `io.quarkiverse.playwright:quarkus-playwright`. No separate Node module, no separate Maven module. A single Quarkus test class boots the chat in test mode, starts Keycloak Dev Services with the imported realm, and drives a real Chromium through the OIDC redirect + Qute-rendered shell.

Layout:

```
conference-chat/
  pom.xml                                  -- adds quarkus-playwright in test scope
  src/test/java/.../playwright/
    ChatPlaywrightTest.java                -- @QuarkusTest + @WithPlaywright
```

Required test-scoped dependency:

```xml
<dependency>
    <groupId>io.quarkiverse.playwright</groupId>
    <artifactId>quarkus-playwright</artifactId>
    <version>2.3.4</version>
    <scope>test</scope>
</dependency>
```

The test class declares an inline `QuarkusTestProfile` that overrides the default `%test` config to re-enable OIDC and Keycloak Dev Services (which are off in the other test profiles to keep unit tests fast):

```java
@QuarkusTest
@TestProfile(ChatPlaywrightTest.LiveOidc.class)
@WithPlaywright
class ChatPlaywrightTest {
    public static class LiveOidc implements QuarkusTestProfile {
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                "quarkus.oidc.tenant-enabled", "true",
                "quarkus.keycloak.devservices.enabled", "true");
        }
    }

    @InjectPlaywright BrowserContext context;
    @TestHTTPResource("/") URL chatBase;
}
```

`@TestHTTPResource("/")` provides the chat's URL on the random test port; `@InjectPlaywright BrowserContext` provides a fresh browser context per test method.

The realm export's `conference-chat` client declares `redirectUris = ["http://localhost:8082/*", "http://localhost:*/*"]` and `webOrigins = ["+"]` so the OIDC redirect succeeds on whatever random port Quarkus picks for the test server.

Scenarios that must pass:

1. Anonymous request to `/` redirects to Keycloak; `#username` and `#kc-login` are visible.
2. Logging in as `willem.jan / willem.jan` lands on the Qute-rendered shell. The identity panel shows `willem.jan` + `attendee` + `speaker`. The slide-19 hero shows three rows: `Who.`, `What.`, `Provable.`
3. Logging in as `attendee1` shows the server-rendered quick-prompt list with at least one `data-tier="step-up"` button.

Run with `./mvnw test` inside `conference-chat`. The first run downloads Chromium via Playwright; subsequent runs reuse the cache.

Tests beyond the chat surface (bookmark + audit-recent lands a row in `audit_event`, step-up tool returns `insufficient_user_authentication`, etc.) need conference-api and conference-mcp running too. They are not part of this Quarkus test class because a single `@QuarkusTest` only boots its own module. Drive those flows interactively against the running stack, or add a separate full-stack e2e run later.

The CI matrix runs `./mvnw verify` per module, which includes this test for conference-chat. Allow extra boot time on the CI runner for Playwright to fetch its Chromium binary on the first run.

## Continuous integration and dependency hygiene

Two GitHub-side files under `.github/` are mandatory.

- **`.github/workflows/build.yml`**: matrix build that runs `./mvnw verify` for each of the three apps on push and pull request to `main`. Java 25 via `actions/setup-java@v4` with the temurin distribution and the maven cache. `fail-fast: false` so a regression in one module does not hide failures in the others.

  ```yaml
  jobs:
    build:
      name: ${{ matrix.app }}
      runs-on: ubuntu-latest
      strategy:
        fail-fast: false
        matrix:
          app: [conference-api, conference-mcp, conference-chat]
      steps:
        - uses: actions/checkout@v4
        - uses: actions/setup-java@v4
          with:
            distribution: temurin
            java-version: '25'
            cache: maven
        - working-directory: ${{ matrix.app }}
          run: ./mvnw --batch-mode --no-transfer-progress verify
  ```

- **`.github/dependabot.yml`**: weekly updates. One Maven block covers all three apps via the `directories:` (plural) field, plus one block for GitHub Actions at the repo root. No artifact group filters; let Dependabot update everything it sees.

## Development tooling: Quarkus Agent and skills

All work on this repo (initial build, rebuilds, polish passes) happens through the **Quarkus Agent MCP** plugin. It is the source of truth for how to scaffold, run, and reason about Quarkus apps in this monorepo. Every subagent and the coordinator MUST use it; do not run `mvn quarkus:dev` or `mvn` directly when an equivalent Quarkus Agent tool exists.

The agent's key tools (invoked as `quarkus_*` MCP calls):

- `quarkus_create` -- scaffold a new Quarkus app with the chosen extensions. Used once per module (already done for the three apps in this repo).
- `quarkus_update` -- check whether a module is on the latest Quarkus release and run the upgrade dry-run.
- `quarkus_start` / `quarkus_stop` / `quarkus_status` / `quarkus_restart` -- manage dev-mode lifecycle without leaving a Maven process attached to the agent's terminal.
- `quarkus_logs` -- pull the most recent Quarkus log lines for a managed app.
- `quarkus_skills` -- **mandatory before writing or editing any extension-specific code.** Returns extension-specific patterns, testing guidance, and pitfalls. Skills exist for `quarkus-arc`, `quarkus-hibernate-orm-panache`, `quarkus-oidc`, `quarkus-rest`, `quarkus-smallrye-openapi`, `quarkus-security`, etc.
- `quarkus_searchDocs` -- prefer this over generic web search for any Quarkus question; it is version-aware.
- `quarkus_searchTools` + `quarkus_callTool` -- list and invoke the Dev MCP tools the running app exposes (testing, configuration, OpenAPI schema retrieval, scheduler jobs). The tool list is dynamic per extension set, so re-list after adding or removing an extension.

### Mandatory workflow steps for every subagent

1. Manage app lifecycles with `quarkus_start` / `quarkus_stop` / `quarkus_restart` / `quarkus_status` / `quarkus_list`. Never `pkill`, never `mvn quarkus:dev &`. The Quarkus Agent owns the dev process and its log file; killing it out from under the agent leaves zombie listeners on ports 8080-8082 and breaks subsequent starts.
2. Call `quarkus_update` (via a subagent or directly) when returning to a module. Acts as a sanity check that the module is on the expected Quarkus version. **This is the ONLY supported way to check for updates** -- do not curl Maven Central or eyeball BOM versions by hand.

   `quarkus_update` reports the current vs latest Quarkus platform version and runs `quarkus update --dry-run` to produce a patch. **Apply the patch.** It correctly handles artifact renames the team performs over time, including `quarkus-junit5` -> `quarkus-junit`. The only recipe steps to **reject** are:
   - Swapping our standalone `io.quarkiverse.langchain4j:quarkus-langchain4j-bom` for the platform-aligned `io.quarkus.platform:quarkus-langchain4j-bom`. The platform variant lags releases. See "Standalone Quarkiverse BOMs" above.
   - Swapping `io.quarkiverse.mcp:quarkus-mcp-server-bom` for the platform-aligned variant for the same reason.

   When the Quarkiverse extensions themselves ship a new minor (eg `quarkus-langchain4j-bom` 1.10.0 -> 1.11.0, `quarkus-mcp-server-bom` 1.12.1 -> 1.13.0), bump the version literal in the relevant pom and restart dev mode. There is no platform release to wait for.
2. Call `quarkus_skills` before writing code for an extension. Skipping this is a violation; the skills file documents the right pattern and prevents the obvious mistakes.
3. Use `quarkus_searchDocs` instead of generic doc search.
4. Run tests via `quarkus_callTool` with `devui-testing_runTests` (or `devui-testing_runTest` for a single class). Do not run `mvn test` directly when the Dev MCP testing tool is available.
5. After pom.xml or build.gradle changes, **full `quarkus_stop` + `quarkus_start`**. A force-restart only recompiles source files; it does not re-resolve dependencies.

### Per-project skills

Per-project agent guidance lives under `<app>/.quarkus/skills/<extension-name>/SKILL.md`. Use these to override or enhance the JAR-bundled skill content with project-specific decisions. Layer order (each enhances or overrides the previous):

1. JAR defaults (built into the extension).
2. Global `~/.quarkus/skills/<extension-name>/SKILL.md`.
3. Project `<app>/.quarkus/skills/<extension-name>/SKILL.md`.

Examples to keep in mind when this monorepo gets larger:

- `conference-api/.quarkus/skills/quarkus-hibernate-orm-panache/SKILL.md` documenting the "no Flyway, drop-and-create, one Session entity, one Speaker FK per Session" rule.
- `conference-mcp/.quarkus/skills/quarkus-mcp-server-http/SKILL.md` documenting "use `@RolesAllowed` on tool methods; use the `StepUp.require()` helper for acr checks; throw `ToolCallException` with `insufficient_user_authentication:` prefix".
- `conference-chat/.quarkus/skills/quarkus-langchain4j-mcp/SKILL.md` documenting "MCP tool calls always go through the `ToolProvider` resolved by the registered provider; never call conference-api REST clients directly from the chat backend".

Skills are optional during the first build, but every subagent must read the relevant SKILL files (JAR or local) via `quarkus_skills` before touching extension-specific code.

### `AGENTS.md` per app

Each scaffolded Quarkus app already ships an `AGENTS.md` (and a `CLAUDE.md` pointer to it) generated by `quarkus_create`. Keep it. It enforces:

- The extension-first rule (no hand-rolled features when a Quarkus extension fits).
- The "load skills before writing code" rule.
- Test discipline (`@QuarkusTest`, Dev Services for backing services, no `-DskipTests`).
- README maintenance after every change.

If an `AGENTS.md` directive conflicts with this SPEC.md, **SPEC.md wins** for the demo's particulars (sequential ports, single shared realm, no Flyway, no DEMO_RESET.sh, etc.). Flag any conflict and update SPEC.md instead of silently diverging.

---

## Build process: one subagent per app

The three apps are independent enough that they should be built by **three separate subagents** working in parallel, with a fourth coordinator pass at the end to wire them together. This keeps the main agent's context lean and makes each piece independently verifiable.

### Subagent assignments

1. **`conference-api` subagent**: data model, Flyway migrations, REST endpoints, seeders, importer, audit-live dashboard, integration tests.
2. **`conference-mcp` subagent**: MCP tools, security checks, REST clients with token propagation, health checks, wiring tests.
3. **`conference-chat` subagent**: OIDC web-app, LangChain4j MCP client, provider registry, intent matcher, chat SPA, wiring tests.
4. **Coordinator pass** (main agent or a fourth subagent): Keycloak realm export with all four clients (the three apps plus `mcp-clients`), shared Dev Services config, RUNBOOK, top-level README.

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
6. **MCP tools** in conference-mcp with LLM-tuned descriptions, standard `@RolesAllowed` annotations, and the `StepUp.require()` helper on the two step-up tools.
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
11. No demo-reset script. Restart `conference-api` (or remove the Dev Services Postgres container) for a clean slate.

## Open questions

1. The latest MCP authorization spec details for step-up challenges: confirm the exact wire format Quarkus MCP server emits once the upstream test lands. Default to following Quarkus' lead.
2. Whether to ship a Bedrock provider in the default build. Currently optional, off by default.
3. Whether to stream LLM responses into the assistant bubble. Currently sync; streaming is a nice-to-have.

## Visual reference (deck to UI mapping)

The single source of design truth is `practical-mcp-security.pdf` (and the underlying `.pptx`). Both SPAs (chat at `:8082` and audit-live at `:8080/audit-live/`) must read as **two more slides** in that deck, not as generic web pages.

### Recurring motifs lifted from the deck

- **Vertical brand-blue stripe** down the left edge of slide 1 (about 12px wide, full height). Echoes the talk's identity. Apply as a fixed 8 to 10px stripe on the chat client's left edge and on the audit dashboard's hero.
- **Footer bar** on most slides: small muted text, format `Practical MCP Security in Action  /  jPrime 2026` left-aligned, `N / 20` right-aligned, color `--fg-dim` (`#5F6B7A`), monospace. Both SPAs reuse the left-half text; the right-half becomes the app name (`conference-chat` or `conference-api`) and the active mode or event count.
- **DEMO transition slides** are full-bleed colored (brand blue for demos 1 and 2, amber for demo 3). The chat's mode toggle should echo this: scripted mode pill is blue, llm mode pill is amber.
- **Slide 5 tier legend**: three side-by-side cards with a 4px colored top stripe -- blue for Demo 1 (public), blue for Demo 2 (personal), amber for Demo 3 (sensitive). This is the **canonical tier color legend**. Quick-prompt buttons must use these top-stripe colors (blue for public/attendee/speaker, amber for step-up).
- **Slide 9 pull quote**: dark card with a 4px amber left border, italic muted caption underneath ("a frequent objection in the MCP community" style). Reuse for step-up error cards.
- **Slide 11 pipeline**: four icon boxes connected by 24px brand-blue arrows. Reuse in the chat sidebar to draw the chain `User -> Chat -> MCP -> API` as a small pictogram.
- **Slide 14 audit_event card**: the canonical struct card layout (see below). Every tool call in the chat and every row in the audit dashboard renders in this exact style.
- **Slide 18 / 19 closing**: huge brand-blue word ("Who.", "What.", "Provable.") with a short paragraph beside it. Reuse for the chat's empty-state intro: three rows.

### Color semantics (deck-derived, non-negotiable)

| Use | Color | Where |
|-----|-------|-------|
| Public / read-only tools | `--brand` `#0088D3` | quick prompt stripe, tool card border |
| Attendee tools (write under my identity) | `--brand` `#0088D3` | same |
| Speaker tools (normal) | `--brand-2` `#2DA1E2` | speaker tier accent |
| Step-up / sensitive | `--amber` `#F2A65A` | quick prompt stripe, tool card border, step-up cards |
| Destructive (CANCEL_SESSION) | `--red` `#E5645A` | tool card border in transcript, audit card border |
| Identity highlight (subject, name) | `--amber` `#F2A65A` | `attendee_subject` value in audit card; the brand-amber pill in the chat top bar |
| Success / live indicator | `--green` `#66D19E` | pulsing dot in the top bar |

### Slide 14 audit_event card -- exact spec

This is the gold-standard layout. Slide 14 shows it; the chat tool cards and the audit dashboard cards must match it pixel-for-pixel up to responsive constraints.

```
+--------------------------------------------------------+
| audit_event                              hh:mm:ss      |  <- header row, muted blue label left, muted timestamp right
+--------------------------------------------------------+
| action               RATE_SESSION                      |  <- key (muted, mono) | value: action in --brand
| target               session:253 (Practical MCP Security)
| attendee_subject     willem.jan                        |  <- value in --amber for identity
| executed_by_client   claude-desktop (registered via DCR)
| token_iss            http://localhost:.../realms/jprime
| created_at           2026-06-03T10:46:14+03:00         |
+--------------------------------------------------------+

   The AI didn't rate the talk. I did. Auditable. Reversible. Attributable.   <- italic --brand caption below
```

- Card background: `--surface` (`#171B22`).
- Card border: 1px `--border` (`#2A313C`) on three sides; **3px tier color on the left**.
- Card padding: 16px vertical, 22px horizontal.
- Header row: monospace `--brand-2` for the type label (`audit_event`, `mcp.tool_call`, `mcp.tool_result`), muted dim timestamp right-aligned.
- Body grid: two columns, key column ~200px wide in `--fg-muted`, value column flexes.
- Each row uses monospace 13px.
- Below the card: optional italic `--brand` (or `--amber` for step-up) caption, 14px, slide 14 voice.

The chat client renders **two cards per tool call**: one labeled `mcp.tool_call` showing the outbound payload, one labeled `mcp.tool_result` showing the response. Stack vertically, 8px gap. Step-up rejection becomes a single amber card with `error: insufficient_user_authentication` and the slide 9 pull-quote treatment underneath.

### Component-by-component checklist

**Chat top bar (port 8082)**
- 8px brand-blue vertical stripe along the absolute left edge of the page.
- Top bar `--surface` background, 56px tall, 28px horizontal padding.
- Left: shield SVG (18 to 20px, fill `--brand`), then `Practical MCP Security in Action` bold white, ` / ` muted, `jPrime 2026 Conference Companion` muted.
- Right: green pulsing dot, the logged-in name in `--amber`, separator, mode pill (`scripted` blue or `llm` amber), separator, provider pill (`anthropic` / `openai` / `ollama` / `bedrock` in the brand text color of the active provider).

**Chat sidebar**
- 280px wide, `--surface` background, 1px `--border` right edge.
- Sections: `Quick prompts`, `Mode`, `Provider`, `Identity`, `Companion screens`. Section labels: 11px uppercase, letter-spacing 0.08em, `--fg-dim`.
- Quick-prompt buttons: slide-5 card style. Background `--surface-2`, 4px top stripe in the tier color (blue/blue-2/amber). Label in body sans, tier hint in mono `--fg-dim` underneath the label.
- Provider dropdown: dark `--surface-2` chip with the active provider in `--brand` text. Clicking opens a small list of available providers; greyed-out for any provider missing config.
- Identity dl: `subject` and `name` values in `--amber`, others in `--fg`.

**Chat transcript**
- 32px / 40px padding.
- User bubble: align-self right, background `--brand`, text near-black (`#051018`), bottom-right radius 2px (tail style).
- Assistant bubble: align-self left, background `--surface`, 1px `--border`, bottom-left radius 2px.
- Hero intro (empty state): three rows in the slide 19 style. Big `--brand` word ("Who.", "What.", "Provable.") + one-sentence description in `--fg-muted`, separated by 20px vertical gap.
- Tool cards inside the assistant bubble follow the slide 14 spec above. Two cards per turn (call + result), 8px gap.

**Composer**
- 16px / 40px padding, top border 1px `--border`, `--surface` background.
- Text input `--bg` background, 1px `--border`, focus border `--brand`.
- Send button 44px square, `--brand` background, paper-plane SVG, hover `--brand-2`.

**Audit-live dashboard (port 8080)**
- 8px brand-blue vertical stripe on the absolute left edge (matches the chat).
- Same top bar treatment, status line "Live audit stream / N events".
- Hero block under the top bar: 56px headline "It wasn't the AI." white, then "It was me." `--brand`. Sub-headline in `--fg-muted` (1 to 2 lines) describing what the audience is about to see.
- Event list: a vertical stack of slide-14 audit_event cards. New events animate in (`opacity 0 + translateY(-6px)` to `1 + 0`, 320ms ease-out).
- Empty state: dashed-border placeholder "Waiting for the first event..." in mono `--fg-dim`.
- Below each card, optional italic caption "It wasn't the AI. It was me." (only on the first card per session to keep it from looking shouty).

**Step-up error rendering (both apps)**
- Slide 9 treatment: amber 4px left border, italic body in `--amber`, muted "Server says: this tool needs MFA. Client re-authenticates. Done." caption underneath.
- Always include the literal `insufficient_user_authentication` token in monospace so the audience recognises the wire-level error.

### Typography contract

- **Body**: system sans / Calibri stack. 15px base. Slide titles 36 to 56px bold. Hero pull-quote 32 to 40px bold.
- **Mono**: JetBrains Mono / Fira Code / Consolas. Used everywhere `audit_event` appears, every key/value in tool cards, every tier hint, the green dot status line, the footer.
- **Italic muted accents** are a recurring deck pattern. Use sparingly in the SPAs: the chat empty-state subtitle, the audit caption, the "Server says..." step-up caption.
- **No em dashes** in any text. ASCII hyphens `-` only.

### What deck slides map to what UI elements

| Slide | Element in UI |
|-------|---------------|
| 1 (title) | Vertical blue stripe, shield + title in both top bars |
| 5 (three demos) | Quick-prompt color-coding (blue/blue/amber stripes) |
| 9 (objection pull quote) | Step-up error card |
| 11 (pipeline) | Optional sidebar diagram (`User -> Chat -> MCP -> API`) |
| 12 (Demo 2 icons) | Inline icons next to quick prompts (bookmark blue, star amber, search blue) |
| 14 (audit_event) | Tool cards in transcript + cards in audit dashboard |
| 18 / 19 (Who / What / Provable) | Chat empty-state hero rows |
| Footer bar | Footer of both SPAs |

A subagent or polish pass that diverges from any of the above must justify the deviation in writing, slide reference included.

---

## Operational notes (lessons from rehearsals)

A short list of things that bit us during the build. Documented so a fresh agent does not retrace the debugging.

- **Pin the Keycloak image** across all three apps (`quarkus.keycloak.devservices.image-name=quay.io/keycloak/keycloak:26.5.7`). Without this, Quarkus 3.27.x apps default to Keycloak 26.3.4 and Quarkus 3.35.4 apps default to 26.5.7, and `shared=true` silently spawns two containers.
- **All three apps on the same Quarkus version.** Mixed Quarkus platforms (eg 3.27 from a stale langchain4j BOM vs 3.35 from the platform BOM) cause subtle wiring drift. `quarkus_update` catches this.
- **Restart the apps after rebuilding the realm.** OIDC caches the issuer URL on first discovery; if Keycloak comes back on a different port, the running apps return 401 on every token until restarted.
- **Pin `demo.now`** in dev so the rating cutoff and `whats_on_now` are deterministic. Without it, the wall clock reads "today" and every seeded 2026-06-03 session looks future-dated, so `rate_session` returns 422 `session_not_started`.
- **No code comments explaining "why we did X"**. The rationale belongs in this spec or in a commit message, not in the source. Keep code comments to the minimum needed to read the code in isolation. Avoid javadoc that recapitulates what the method signature already says.
- **No artifact ids in code comments**. We re-renamed `quarkus-mcp-server-sse` to `quarkus-mcp-server-http`; the pom is the source of truth, the spec documents the rename, no comment needed in the pom block.

## Out of scope

- Production deployment topology.
- Real TOTP enrollment UX. The demo uses a known seed.
- A Bedrock or Vertex AI provider beyond the optional langchain4j-bedrock module.
- Real DCR for the chat client. The chat client uses a static confidential Keycloak client; only `mcp-clients` is DCR-enabled for the MCP Inspector flow.
