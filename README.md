# Practical MCP Security in Action
### jPrime 2026 / Conference Companion / Demo backend

> "It wasn't the AI. It was me."
>
> Three demos, three tiers. All against the same Quarkus MCP server and the same audit log.

## Layout

| Path | What it is |
|------|------------|
| `conference-api/`   | Quarkus REST + Panache + Postgres. Owns the schedule, attendee agendas, ratings, and the audit log. Serves the live audit dashboard at `/audit-live`. |
| `conference-mcp/`   | Quarkus MCP server (SSE). Exposes tools to AI clients and propagates the user OAuth token to `conference-api`. |
| `conference-chat/`  | Quarkus chat client. OIDC web-app login, LangChain4j MCP client to `conference-mcp`, Qute-rendered shell. |
| `keycloak-realm.json` | Single shared realm export at the monorepo root. All three apps reference it via `../keycloak-realm.json`. |
| `SPEC.md`           | Original build spec for the demo (canonical reference). |
| `RUNBOOK.md`        | Stage-side runbook with the three-demo flow and the env vars to set. |

No Docker compose file: every backing service is auto-provisioned by **Quarkus Dev Services**.

## Run

```bash
# Terminal 1 - conference-api on :8080
cd conference-api && ./mvnw quarkus:dev

# Terminal 2 - conference-mcp on :8081
cd conference-mcp && ./mvnw quarkus:dev

# Terminal 3 - conference-chat on :8082
cd conference-chat && ./mvnw quarkus:dev
```

On first start Dev Services boots:

- a **Postgres 16** container for `conference-api` (schema owned by Hibernate via `drop-and-create`, auto-seeded with the schedule and demo data)
- a **Keycloak 26** container with the `jprime` realm pre-imported, shared between all three apps via `service-name=jprime-keycloak`

The realm export lives once at the monorepo root (`./keycloak-realm.json`). All three apps reference it via `quarkus.keycloak.devservices.realm-path=../keycloak-realm.json`.

Dev UI for each app lives at `http://localhost:8080/q/dev/`, `http://localhost:8081/q/dev/`, and `http://localhost:8082/q/dev/`.

Playwright end-to-end tests live inside `conference-chat`: run them with `cd conference-chat && ./mvnw test`.

## Demo dashboard

`http://localhost:8080/audit-live/` shows the second-screen view used in demos 2 and 3. The styling matches the talk deck: dark surface, brand blue for normal events, amber for step-up tools, red for destructive actions, mono font for `audit_event` rows.

Open the page on a secondary monitor and run the demos. New events appear within two seconds of every tool call.

## Demos

See [`RUNBOOK.md`](RUNBOOK.md) for the moment-by-moment script of the three live demos:

1. **Public** browse the schedule. PKCE + DCR + Authorization Code Flow.
2. **Personal** agenda, ratings, conflicts. Token propagation, full audit.
3. **Sensitive** speaker-only tools with step-up MFA. Server-driven challenge.

## Brand palette (lifted from the deck)

| Role | Hex | Where it shows up |
|------|-----|-------------------|
| Background    | `#0E1116` | Audit dashboard surface |
| Surface       | `#171B22` | Cards, top bar |
| Border        | `#2A313C` | Card outlines |
| Primary text  | `#F5F7FA` | Headings, key values |
| Muted text    | `#9AA4B2` | Subtitles, field labels |
| Brand         | `#0088D3` | Normal actions, links, accents |
| Amber         | `#F2A65A` | Step-up / sensitive actions, identity highlights |
| Body type     | Calibri / system sans | Headings, prose |
| Mono type     | Consolas / JetBrains Mono | `audit_event`, identifiers |

## Conventions

- No em dashes anywhere in code, comments, or docs (Lunatech house style).
- Tests run against Quarkus Dev Services (Testcontainers under the hood); no manual infra needed for `mvn test`.
- Hibernate owns the schema (`drop-and-create` in dev/test). No Flyway.
