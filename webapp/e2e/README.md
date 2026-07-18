# Webapp E2E (Playwright)

Browser end-to-end tests that drive the real SPA against a running backend, authenticating with the
**passwordless dev-login** (no OAuth IdP), over plain **`http://localhost`**. For *why* plain http works
and why it's fail-closed in prod, see
[docs/contributor/e2e-testing.md](../../docs/contributor/e2e-testing.md).

## One-time setup

1. **Postgres + NATS** (any local instance; the helper scripts under `scripts/` work too).

2. **Backend** — boot on the `e2e` profile, which sets the dev-login + `cookie-secure=false` wiring these
   tests need (and is fail-closed under `prod`):

   ```bash
   pnpm dev:server:e2e        # = ./mvnw -f server spring-boot:run -Dapp.profiles=local,e2e
   ```

3. **Seed** the workspace + a member account (the dev-login creates account id 1; the SPA navigates by
   membership). Sign in once so the account exists, then seed:

   ```bash
   curl -s -X POST localhost:38080/auth/dev-login -H 'content-type: application/json' \
     -d '{"username":"e2e","admin":true}' -o /dev/null
   psql "$DATABASE_URL" -f webapp/e2e/seed.sql
   ```

## Run

```bash
pnpm --filter webapp run test:e2e          # starts vite (reused if running) + runs the specs
E2E_SERVER_URL=http://localhost:38080 pnpm --filter webapp run test:e2e   # override backend origin
```

Provider-backed integration checks are opt-in because they require configured live workspaces:

```bash
E2E_LIVE_USERNAME=e2e E2E_GITHUB_WORKSPACE=github-workspace \
  E2E_GITLAB_WORKSPACE=gitlab-workspace LIVE_INTEGRATION_E2E=true \
  pnpm --filter webapp run test:e2e -- sync-observability.live.spec.ts
E2E_LIVE_USERNAME=e2e E2E_GITHUB_WORKSPACE=github-workspace \
  E2E_GITLAB_WORKSPACE=gitlab-workspace E2E_MUTATE_LIVE_INTEGRATIONS=true \
  LIVE_INTEGRATION_E2E=true \
  pnpm --filter webapp run test:e2e -- sync-observability.live.spec.ts
```

All three identifiers are required. The mutation flag starts a real provider sync.

## How it works

- `fixtures.ts` replaces the dev `/env-config.js` stub so the SPA points at `E2E_SERVER_URL` and reads
  the non-`__Host-` `XSRF-TOKEN` cookie — matching `cookie-secure=false`.
- `loginAsDevAdmin(page)` drives the real **Dev sign-in** button on `/login`.
- Add specs as `e2e/*.spec.ts`; import `{ test, expect, loginAsDevAdmin }` from `./fixtures`.
