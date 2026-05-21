# jPrime 2026 Conference Companion (MCP Demo)

Two Quarkus apps that back the live demos for the jPrime 2026 talk **Practical MCP Security in Action** (Hall B, day 1, 10:00 to 10:50).

## Layout

| Path | What it is |
|------|------------|
| `conference-api/`   | Quarkus REST + Panache + Postgres. Owns the schedule data, attendee agendas, ratings, and audit log. |
| `conference-mcp/`   | Quarkus MCP server (SSE). Thin protocol adapter that exposes tools to AI clients and propagates the user OAuth token to `conference-api`. |
| `infra/`            | `compose.yml` for Postgres + Keycloak and the `keycloak-realm.json` export with all clients, roles, and the step-up flow. |
| `docs/`             | Demo notes and runbook (see `RUNBOOK.md`). |
| `DEMO_RESET.sh`     | Wipes user-generated rows (bookmarks, ratings, audit, attendees) and re-seeds the demo set for between rehearsal runs. |

## Quick start (dev)

```bash
# 1. Boot Keycloak + Postgres
docker compose -f infra/compose.yml up -d

# 2. In one terminal, run the data API on :8080
cd conference-api && ./mvnw quarkus:dev

# 3. In a second terminal, run the MCP server on :8082
cd conference-mcp && ./mvnw quarkus:dev
```

In dev mode both apps disable OIDC enforcement so you can poke around with `curl`. Production mode (`%prod`) wires the apps to the Keycloak realm imported by `infra/compose.yml`.

## Demo flow

See [`RUNBOOK.md`](RUNBOOK.md) for the rehearsal-by-rehearsal flow, the env vars to set, and the recovery steps if something goes sideways on stage.

## Tech stack

- Java 25, Quarkus 3.35
- Hibernate ORM with Panache, Flyway, Postgres
- Quarkus OIDC bearer-only on `conference-api`, OIDC + REST client token propagation on `conference-mcp`
- `quarkiverse-mcp-server-sse` for the MCP protocol
- Keycloak 26 with the realm export under `infra/keycloak-realm.json`
- jsoup importer that fetches the live jprime.io agenda and falls back to a baked-in baseline if the page is unreachable

## Conventions

- No em dashes anywhere in code, comments, or docs (Lunatech house style).
- Tests run against Quarkus Dev Services (Testcontainers under the hood); no external infra needed for `mvn test`.
