# conference-api

The data API for the jPrime 2026 MCP demo. Owns the conference schedule, attendee agendas, ratings, and the audit log.

## Run

```bash
./mvnw quarkus:dev
```

In dev mode OIDC is disabled, so endpoints are open. Dev Services boots Postgres in the background. On startup the app seeds itself from a baked-in schedule and (when `JPRIME_IMPORTER_ENABLED=true`) tries the live jprime.io agenda.

## Endpoints

All under `/api/v1/`.

### Public (no auth)
- `GET /sessions` -- list. Filters: `day`, `track`, `speaker_id`, `level`, `q`.
- `GET /sessions/{id}`
- `GET /sessions/current` -- accepts `?at=` for demo determinism.
- `GET /sessions/next` -- accepts `?at=` and `?limit=` (default 3).
- `GET /speakers` and `GET /speakers/{id}` and `GET /speakers/{id}/sessions`.
- `GET /rooms`

### Attendee (role `attendee`)
- `GET /me`, `GET /me/agenda`, `POST /me/agenda`, `DELETE /me/agenda/{sessionId}`
- `GET /me/conflicts`, `GET /me/ratings`
- `POST /sessions/{id}/ratings` -- refuses to rate sessions that have not started yet (422).
- `GET /audit` -- recent audit lines for the second-screen demo.

### Speaker (role `speaker`)
- `GET /me/sessions/feedback` -- aggregate + individual ratings on my sessions.

### Speaker, step-up required (acr=`urn:mace:incommon:iap:silver` or amr containing `mfa`/`otp`)
- `GET /sessions/{id}/attendees` -- attendee names and emails. Returns 401 with `WWW-Authenticate: Bearer error="insufficient_user_authentication"` if step-up is missing.
- `POST /sessions/{id}/cancel` -- reversible toggle; every call is audited.

OpenAPI lives at `/q/openapi` and Swagger UI at `/q/swagger-ui`.

## Auth

`quarkus-oidc` in `service` (bearer-only) mode. Roles come from the Keycloak claim path `realm_access/roles`. The Keycloak realm import is at `../infra/keycloak-realm.json`.

## Seeding

On startup, `StartupSeeder` runs three steps in order, all idempotent:
1. `StaticScheduleSeeder` populates a hand-curated baseline if the database has zero sessions. The demo never depends on jprime.io being reachable.
2. `JsoupImporter` attempts the live jprime.io scrape if `JPRIME_IMPORTER_ENABLED=true`. Failure is logged loudly and ignored.
3. `DemoDataSeeder` creates ~10 fake attendees, ~20 bookmarks, ~30 ratings (with extra coverage of Willem Jan's sessions for the speaker feedback demo).

The admin endpoint `POST /api/v1/admin/reseed-demo` (dev/test only) wipes user-generated rows and re-runs the demo seed. The repo's top-level `DEMO_RESET.sh` calls it.

## Testing

```bash
./mvnw test
```

Uses Quarkus Dev Services (Testcontainers Postgres). Tests use `@TestSecurity` and `@OidcSecurity` to simulate users without standing up Keycloak.

## Related guides

- [Hibernate ORM with Panache](https://quarkus.io/guides/hibernate-orm-panache)
- [REST](https://quarkus.io/guides/rest) and [REST Jackson](https://quarkus.io/guides/rest#json-serialisation)
- [Flyway](https://quarkus.io/guides/flyway)
- [SmallRye OpenAPI](https://quarkus.io/guides/openapi-swaggerui)
- [JDBC Postgres](https://quarkus.io/guides/datasource)
- [OpenID Connect](https://quarkus.io/guides/security-openid-connect)
