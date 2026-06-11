# system-tests

End-to-end system tests for the personal-stack platform services: auth-api, auth-ui, app-ui, and infrastructure (Traefik routing, Vault OIDC, Stalwart, forward-auth).

## Scope

These tests cover the services built and deployed from this repository:

- `auth-api` — registration, login, TOTP, session management, forward-auth chain
- `auth-ui` / `app-ui` — Playwright flows for login, logout, protected pages, cross-app session
- Traefik routing — health check accessibility, security headers, forward-auth redirects
- OIDC downstream services — Vault, n8n, Grafana native OIDC flows
- Stalwart mail — forward-auth protection

## Out of scope

Agents (agents-api / agents-ui) e2e coverage lives in the [ExtraToast/agents](https://github.com/ExtraToast/agents) repository. That service is consumed as an external GHCR image and is not built or tested here.

## Running locally

```bash
# Start the compose stack (requires Docker)
bash .github/scripts/start-system-test-stack.sh

# Run all system tests
./gradlew :services:system-tests:test

# Playwright-only shards
./gradlew :services:system-tests:testPlaywright
```
