#!/usr/bin/env bash
# Wipes user-generated demo data (bookmarks, ratings, audit, attendees) and
# re-seeds the demo set. Use between rehearsals so each run starts clean.
#
# Strategy: call the dev-only POST /api/v1/admin/reseed-demo on conference-api.
# Dev Services owns the database; we never touch it directly.

set -euo pipefail

API_URL="${CONFERENCE_API_URL:-http://localhost:8080}"

echo "POST $API_URL/api/v1/admin/reseed-demo ..."
if curl --silent --show-error --fail --max-time 5 -X POST "$API_URL/api/v1/admin/reseed-demo" >/tmp/demo-reset.json; then
    cat /tmp/demo-reset.json
    echo
    echo "Demo reset complete."
    exit 0
fi

cat <<'MSG'
Reset endpoint unreachable. Common causes:
  1. conference-api is not running. Start it with: cd conference-api && ./mvnw quarkus:dev
  2. CONFERENCE_API_URL points at the wrong host. Default: http://localhost:8080
  3. The app is running in %prod mode where /admin/* is disabled.

If you need a nuclear reset, stop conference-api and remove the Dev Services
Postgres container:

  docker ps --filter "label=quarkus-dev-service.postgresql" -q | xargs -r docker rm -f

Then start conference-api again -- Flyway and the seeders will re-create everything.
MSG
exit 1
