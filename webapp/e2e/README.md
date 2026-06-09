# Webapp E2E (Playwright)

Browser end-to-end tests that drive the real SPA against a running backend, authenticating with the
**passwordless dev-login** (no OAuth IdP). Everything runs over plain **`http://localhost`** — no proxy,
no TLS — because `localhost` is a browser "secure context" where Chromium honours plain `Secure`
cookies. The backend just has to drop the `__Host-` cookie prefixes (which the browser rejects over
http) via `hephaestus.auth.cookie-secure=false`.

## One-time setup

1. **Postgres + NATS** (any local instance; the helper scripts under `scripts/` work too).

2. **Backend** — boot the app server with the E2E flags:

   ```bash
   HEPHAESTUS_AUTH_DEV_LOGIN_ENABLED=true \
   HEPHAESTUS_AUTH_COOKIE_SECURE=false \
   HEPHAESTUS_AUTH_COOKIE_NAME=HEPHAESTUS_AT \
   HEPHAESTUS_WORKSPACE_INIT_DEFAULT=false \
   mvn -f server spring-boot:run
   ```

   `dev-login-enabled` + `cookie-secure=false` are **fail-closed in the `prod` profile** (the app
   refuses to boot), so they can only ever be on locally.

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

## How it works

- `fixtures.ts` replaces the dev `/env-config.js` stub so the SPA points at `E2E_SERVER_URL` and reads
  the non-`__Host-` `XSRF-TOKEN` cookie — matching `cookie-secure=false`.
- `loginAsDevAdmin(page)` drives the real **Dev sign-in** button on `/login`.
- Add specs as `e2e/*.spec.ts`; import `{ test, expect, loginAsDevAdmin }` from `./fixtures`.
