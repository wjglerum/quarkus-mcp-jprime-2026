#!/usr/bin/env bash
# Wipes user-generated demo data (bookmarks, ratings, audit, attendees) and
# re-seeds the demo set. Use between rehearsals so each run starts clean.
#
# Two strategies, in order of preference:
#   1. If conference-api is running in dev mode, hit /api/v1/admin/reseed-demo.
#   2. Otherwise, exec the SQL directly against the Postgres container.

set -euo pipefail

API_URL="${CONFERENCE_API_URL:-http://localhost:8080}"
COMPOSE_FILE="${COMPOSE_FILE:-infra/compose.yml}"
PG_SERVICE="${PG_SERVICE:-postgres}"
PG_DB="${POSTGRES_DB:-conference}"
PG_USER="${POSTGRES_USER:-conference}"

reset_via_api() {
    echo "Trying $API_URL/api/v1/admin/reseed-demo ..."
    if curl --silent --show-error --fail --max-time 5 -X POST "$API_URL/api/v1/admin/reseed-demo" >/tmp/demo-reset.json; then
        cat /tmp/demo-reset.json
        echo
        return 0
    fi
    return 1
}

reset_via_sql() {
    echo "Falling back to direct SQL through docker compose ..."
    docker compose -f "$COMPOSE_FILE" exec -T "$PG_SERVICE" \
        psql -U "$PG_USER" -d "$PG_DB" <<'SQL'
TRUNCATE TABLE rating RESTART IDENTITY CASCADE;
TRUNCATE TABLE bookmark RESTART IDENTITY CASCADE;
TRUNCATE TABLE audit_event RESTART IDENTITY CASCADE;
TRUNCATE TABLE attendee RESTART IDENTITY CASCADE;
SELECT 'wiped' AS status;
SQL
    echo "Restart conference-api now so DemoDataSeeder re-runs."
}

if reset_via_api; then
    echo "Demo reset complete via API."
    exit 0
fi

reset_via_sql
echo "Demo reset complete via SQL. Run 'cd conference-api && ./mvnw quarkus:dev' to re-seed."
