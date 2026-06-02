# conference-mcp

Quarkus MCP server for the jPrime 2026 demo. Thin protocol adapter that exposes
conference tools to AI clients, secured with OIDC and propagates the user's
bearer token to `conference-api`.

- Port: 8081
- MCP endpoint (streamable HTTP): `http://localhost:8081/mcp`
- Health: `GET /q/health/ready`

Start in dev mode via the Quarkus Agent MCP plugin. Dev Services provisions a
shared Keycloak using the realm at `../keycloak-realm.json`.
