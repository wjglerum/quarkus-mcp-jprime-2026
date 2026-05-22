# conference-api

The data API for the jPrime 2026 MCP demo. Owns the conference schedule, attendee agendas, ratings, and the audit log.

## Run

```bash
./mvnw quarkus:dev
```

In dev mode OIDC is disabled, so endpoints are open. Dev Services boots Postgres in the background. Hibernate owns the schema via `quarkus.hibernate-orm.database.generation=drop-and-create`; there is no Flyway. On startup the app seeds itself from the baked-in agenda snapshot.

## Endpoints

All under `/api/v1/`.

### Public (no auth)
- `GET /sessions` list. Filters: `day`, `track`, `speaker_id`, `level`, `q`.
- `GET /sessions/{id}`
- `GET /sessions/current` accepts `?at=` for demo determinism.
- `GET /sessions/next` accepts `?at=` and `?limit=` (default 3).
- `GET /speakers` and `GET /speakers/{id}/sessions`.

### Attendee (role `attendee`)
- `GET /me`, `GET /me/agenda`, `POST /me/agenda`, `DELETE /me/agenda/{sessionId}`
- `GET /me/conflicts`, `GET /me/ratings`
- `POST /sessions/{id}/ratings` refuses to rate sessions that have not started yet (422).
- `GET /audit` recent audit lines for the second-screen demo.

### Speaker (role `speaker`)
- `GET /me/sessions/feedback` aggregate + individual ratings on my sessions.

### Speaker, step-up required (acr=`urn:mace:incommon:iap:silver` or amr containing `mfa`/`otp`)
- `GET /sessions/{id}/attendees` attendee names and emails. Returns 401 with `WWW-Authenticate: Bearer error="insufficient_user_authentication"` if step-up is missing.
- `POST /sessions/{id}/cancel` reversible toggle; every call is audited.

OpenAPI lives at `/q/openapi` and Swagger UI at `/q/swagger-ui`.

## Auth

`quarkus-oidc` in `service` (bearer-only) mode. Roles come from the Keycloak claim path `realm_access/roles`. The shared Keycloak realm import lives at the monorepo root and is referenced via `quarkus.keycloak.devservices.realm-path=../keycloak-realm.json`. There is no `infra/` directory.

## Seeding

On startup, `StartupSeeder` runs two idempotent steps in order:
1. `StaticScheduleSeeder` loads `seed/jprime-2026-agenda.json` from the classpath if the database has no sessions. The snapshot ships **32 sessions and 27 speakers** across the two jPrime 2026 days, including Willem Jan on the MCP talk (Hall B, day 1 10:00) and on the Concurrency Crossroads deep dive.
2. `DemoDataSeeder` creates ~10 fake attendees, ~15 bookmarks, ~25 ratings (with extra coverage of Willem Jan's sessions for the speaker feedback demo).

There is no admin reseed endpoint. To reset state between rehearsals, restart `conference-api` after wiping the Dev Services Postgres container: `docker ps --filter "label=quarkus-dev-service.postgresql" -q | xargs -r docker rm -f`. Hibernate `drop-and-create` plus both seeders give you a clean slate on the next boot.

## Testing

```bash
./mvnw test
```

Uses Quarkus Dev Services (Testcontainers Postgres). Tests use `@TestSecurity` and `@OidcSecurity` to simulate users without standing up Keycloak.

## Related guides

- [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache)
- [REST](https://quarkus.io/guides/rest) and [REST Jackson](https://quarkus.io/guides/rest#json-serialisation)
- [SmallRye OpenAPI](https://quarkus.io/guides/openapi-swaggerui)
- [JDBC Postgres](https://quarkus.io/guides/datasource)
- [OpenID Connect](https://quarkus.io/guides/security-openid-connect)
