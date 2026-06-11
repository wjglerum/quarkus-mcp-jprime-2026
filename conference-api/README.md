# conference-api

Backing data API for the jPrime 2026 MCP demo. Exposes the schedule, attendee
agendas, ratings, and an open audit feed under `/api/v1/`, and serves the
second-screen audit dashboard at `/audit-live/`.

Part of a three-app monorepo. See `../SPEC.md` for the demo's source of truth
and `../AGENTS.md` plus `AGENTS.md` for working conventions.

## Stack

- Quarkus 3.x on Java 25
- REST (Reactive) + Jackson
- Hibernate ORM with Panache, PostgreSQL via Dev Services
- OIDC bearer-only (Keycloak via Dev Services, shared realm at
  `../keycloak-realm.json`)
- SmallRye OpenAPI + Swagger UI
- Hibernate Validator
- SmallRye Health

No Flyway. Hibernate generates the schema (`drop-and-create` in dev and test,
`none` in prod).

## Running

From this directory:

```shell
./mvnw quarkus:dev
```

Dev Services boots Postgres and a shared Keycloak realm automatically. The
seeders fill the schedule from `src/main/resources/seed/jprime-2026-agenda.json`
and create demo attendees, bookmarks, and ratings.

Useful URLs in dev mode:

- `http://localhost:8080/q/dev/` -- Dev UI
- `http://localhost:8080/q/swagger-ui/` -- Swagger UI
- `http://localhost:8080/audit-live/` -- second-screen audit dashboard
- `http://localhost:8080/api/v1/sessions` -- public schedule
- `http://localhost:8080/api/v1/audit/recent` -- open audit feed

## REST surface

Public (no auth):

- `GET /api/v1/sessions` (`?speaker_id=`, `?q=`)
- `GET /api/v1/sessions/{id}`
- `GET /api/v1/sessions/current?at=`
- `GET /api/v1/sessions/next?at=&limit=`
- `GET /api/v1/speakers`
- `GET /api/v1/audit/recent?limit=`

Attendee role (`attendee`):

- `GET /api/v1/me`
- `GET|POST /api/v1/me/agenda`, `DELETE /api/v1/me/agenda/{sessionId}`
- `GET /api/v1/me/conflicts`
- `POST /api/v1/sessions/{id}/ratings`
- `GET /api/v1/me/ratings`

Speaker role (`speaker`):

- `GET /api/v1/me/sessions/feedback`

Speaker role plus step-up
(`acr=urn:jprime:mfa` or `acr=2`):

- `GET /api/v1/sessions/{id}/attendees`
- `POST /api/v1/sessions/{id}/cancel` (reversible toggle)

## Demo clock

The rating cutoff and `whats_on_now`/`whats_next` resolve to `demo.now` when
set, otherwise to the wall clock at offset `+03:00`. In `%dev` the default is
`2026-06-03T10:45:00+03:00` so the demo lands on Wednesday mid-morning out of
the box. Override with `DEMO_NOW`.

## Tests

Run the full suite (no live Keycloak required, OIDC tenant is disabled in
`%test`):

```shell
./mvnw test
```

The suite covers public endpoints, the audit-live dashboard route, attendee
agenda CRUD with audit, rating gating against a profile-pinned demo clock, the
open audit feed, and step-up + reversible cancellation for the speaker.

## Configuration

See `src/main/resources/application.properties`. Production needs:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `OIDC_AUTH_SERVER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`
- `DEMO_NOW` if you want a fixed clock; otherwise unset.

## Packaging

```shell
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```
