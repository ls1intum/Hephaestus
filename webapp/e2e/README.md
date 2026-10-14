# Webapp E2E (Playwright)

These tests drive the SPA against the real backend using the local dev login. See
[the contributor guide](../../docs/contributor/e2e-testing.md) for the live practice-review setup.

## Run locally

Start the backend with the profiles used by the packaged CI suite:

```bash
vp run dev:compose:e2e
node scripts/run-gradlew.ts :application:bootRun -Pprofiles=e2e,playwright
```

Create the test account and seed its workspace:

```bash
curl -fsS -X POST http://localhost:8080/auth/dev-login \
  -H 'content-type: application/json' \
  -d '{"username":"e2e","admin":true}' >/dev/null
docker compose -f server/compose.yaml exec -T postgres \
  psql -v ON_ERROR_STOP=1 -U root -d hephaestus < webapp/e2e/seed.sql
```

Run the suite:

```bash
vp run --filter webapp test:e2e
```

Provider-backed integration checks are opt-in because they require configured live workspaces:

```bash
E2E_LIVE_USERNAME=e2e E2E_GITHUB_WORKSPACE=github-workspace \
  E2E_GITLAB_WORKSPACE=gitlab-workspace LIVE_INTEGRATION_E2E=true \
  vp run --filter webapp test:e2e sync-observability.live.spec.ts
```

Set `E2E_MUTATE_LIVE_INTEGRATIONS=true` as well to run the provider-sync mutation test.

`accessibility.spec.ts` is the integrated half of the accessibility evaluation: axe on every route in both
colour schemes, a Tab walk, reflow, menus, dialogs and three keyboard-only processes. It needs only the
`e2e` account and runs on any browser project; `docs/contributor/accessibility-audit-plan.md` says what it
leaves to a person with a screen reader.

## Reading the result

`public-activity.spec.ts` uses the isolated `e2e-public` workspace in `seed.sql`.
It reads real public activity from the packaged server, including private-repository and hidden-person exclusions.
It covers drawer history and sign-in return at desktop, plus one touch viewport.
The seed refreshes repository visibility confirmations; load it before each run.
It uses synthetic GitHub records and needs no provider token.

The coverage reporter prints actual test outcomes and lists every selected live-provider check and
skipped test with its skip reason. CI also appends this report to the job summary. A skipped check
is not a pass; the ordinary packaged-server suite does not prove that live GitHub or GitLab
integrations are healthy. A filtered run explicitly says when no live-provider checks were selected.
The reporter does not enable tests, install credentials, or opt into mutations.
