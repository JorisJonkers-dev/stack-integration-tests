# system-tests

End-to-end system tests for the Joris Jonkers platform services: auth-api, auth-ui, home-portal, and infrastructure (Traefik routing, Vault OIDC, Stalwart, forward-auth).

## Scope

These tests cover the deployed service set:

- `auth-api` — registration, login, TOTP, session management, forward-auth chain
- `auth-ui` / `home-portal` — Playwright flows for login, logout, protected pages, cross-app session
- `knowledge-api` — smoke coverage hooks for the published service image
- `agents-api` / `agents-ui` / `agent-runtime` — tag pins accepted for deploy-gate runs
- Traefik routing — health check accessibility, security headers, forward-auth redirects
- OIDC downstream services — Vault, n8n, Grafana native OIDC flows
- Stalwart mail — forward-auth protection

## Running locally

```bash
# Compile and run structural/unit checks
./gradlew :system-tests:check

# Validate exact image refs from a deployment lock
IMAGE_TAGS="$(deploy-config-schema lock images --lock deployment.lock.yml --format image-tags)" \
  scripts/validate-image-tags.sh --require-all

# Run system suites against an already-started stack
IMAGE_TAGS="$(deploy-config-schema lock images --lock deployment.lock.yml --format image-tags)" \
  ./gradlew :system-tests:testNonPlaywright :system-tests:testPlaywright
```
