# Authentic end-to-end testing

Drive the **real** product loop locally — sign in through the UI, connect a GitLab/GitHub repo, and run
LLM practice detection on a real PR/MR — in a handful of steps, over plain `http://localhost` (no OAuth
IdP, no proxy, no TLS).

It works because of a few local-only affordances, all **fail-closed or absent under the `prod`
profile**, bundled in the opt-in **`e2e` profile** (`server/src/main/resources/application-e2e.yml`):

| Affordance | Property |
| --- | --- |
| Passwordless dev sign-in | `hephaestus.auth.dev-login-enabled=true` |
| Plain-http cookies (drops `Secure` + `__Host-`) | `hephaestus.auth.cookie-secure=false` |
| Self-service GitLab/GitHub workspace creation | `hephaestus.workspace.creation-policy=SELF_SERVICE` + `features.flags.gitlab-workspace-creation=true` |
| Persistable Connection credentials (PATs) | `hephaestus.security.encryption-key` (a fixed local dev key) |
| Dev review trigger | `hephaestus.dev.trigger-enabled=true` |

Activate it alongside `local` (which auto-starts Postgres + NATS).

> **Keep this server private.** `scripts/e2e-setup.sh` accepts only a loopback `E2E_APP_URL` and the
> `e2e` profile must never sit behind a public route: it intentionally exposes passwordless app-admin
> login and a dev-only review trigger. `scripts/jean-public-test.sh` forcibly disables both affordances;
> bootstrap on a loopback-bound server first, stop it, then start the public test deployment.

## The flow (≈3 steps)

```bash
# 1) Stack up: server on the e2e profile (Postgres + NATS auto-start) + vite on :4200.
pnpm dev:server:e2e        # = ./mvnw -f server spring-boot:run -Dapp.profiles=local,e2e
pnpm dev:webapp            # in another terminal
#    For the agent review loop you also need the sandbox + LLM proxy reachable — see "Running the
#    review" below; the app server port and the LLM-proxy port must match.

# 2) Configure everything with your own PAT + LLM key (idempotent; re-run any time).
#    Read secrets without echo/history; the script accepts credentials only through the environment.
read -rsp "SCM PAT: " E2E_GITLAB_PAT && echo && export E2E_GITLAB_PAT
read -rsp "LLM key: " E2E_LLM_KEY && echo && export E2E_LLM_KEY
export E2E_LLM_PRICING_MODE=PRICED
export E2E_LLM_INPUT_USD="$YOUR_CONTRACT_INPUT_RATE_PER_1M"
export E2E_LLM_OUTPUT_USD="$YOUR_CONTRACT_OUTPUT_RATE_PER_1M"
scripts/e2e-setup.sh --llm-base-url https://llm.example/v1 --model example-model

# 3) Open http://localhost:4200 → "Dev sign in" → your workspace → run the trigger the script printed.
```

`scripts/e2e-setup.sh` does the rest through the dev-login API: resolves the real SCM user behind your
PAT, seeds the dev account's identity + membership, **creates the workspace + connection**, wires the
LLM runtime, grants mentor access only to the disposable E2E account, binds the model to practice
detection and mentor, and creates the three practices (Submit reviewable work
/ Act on feedback / Plan & scope issues). Secrets use `E2E_GITLAB_PAT`, `E2E_GITHUB_PAT`,
`E2E_LLM_KEY`, and `E2E_DB_URL`; non-secret options may be flags or their matching `E2E_*` variables.
It also requires an explicit cost declaration: `E2E_LLM_PRICING_MODE=PRICED` with the two contract
rates, or `NO_CHARGE` with `E2E_LLM_PRICE_NOTE` explaining why no metered API rate applies. The script
never invents a price. Useful flags include `--provider`, `--server-url`, `--repo ns/project`, and
`--app-url`. Set `E2E_LLM_PROTOCOL=openai-responses` or `E2E_LLM_AUTH_MODE=API_KEY` when the target
uses those alternatives. Existing workspaces and provider routes are reused only when their SCM owner,
provider, base URL, protocol, and auth mode match exactly; otherwise the setup fails instead of mutating
an unrelated workspace or silently testing stale configuration. Set `E2E_PR_ID` to pin the review
artifact; otherwise the script selects only from repositories monitored by the target workspace.

## Running the review

Two ways to trigger a real practice-detection review:

- **Dev trigger (smoothest — no tunnel).** The script prints it:
  `curl -X POST "http://localhost:38080/api/dev/trigger-review?prId=<id>&workspaceId=<id>"`.
  Workspace creation syncs the connected repo's MRs, so a `prId` usually already exists.
- **Real webhook (full ingestion).** Expose the app with a tunnel (Cloudflare named tunnel is the most
  stable) and set `hephaestus.webhook.external-url` + a `secret` (≥32 chars); the GitLab connection
  then auto-registers a group hook (needs Owner + Premium on the group). Push an MR → review runs.

The agent runs in a Docker sandbox (`ghcr.io/ls1intum/hephaestus/agent-pi`) and calls the LLM through
the in-app proxy — the only path a sandbox has to a provider key, never injected directly. The sandbox
gets an explicit route to that proxy even when general internet access is disabled; the proxy alone
connects to the configured upstream. Findings are posted back to the MR and shown under the
workspace's AI → Activity view.

The live runner JUnit tests are useful component checks, but they point the runner directly at the
upstream endpoint. They do **not** replace this loop: only a real queued job and mentor turn through the
application prove catalog resolution, proxy authentication, budget admission, durable usage accounting,
sandbox execution, and SCM delivery together.

## Browser tests (Playwright)

`webapp/e2e/` holds a `@playwright/test` harness (`pnpm --filter webapp run test:e2e`) that drives the
SPA over plain http via the dev-login — see the `README.md` in that directory. It uses the same
`cookie-secure=false` + `XSRF-TOKEN` wiring this page relies on.

## Caveats

- **`Plan & scope issues` won't fire on its own** — the pipeline is PR/MR-triggered only (no
  issue-review pipeline yet). Test with a PR/MR; the practice is evaluated in PR context.
- **Delivery needs a PR row with `author_id`** — synced MRs have it; a hand-seeded PR may not.
- **Docker socket** — the agent sandbox needs `/var/run/docker.sock`; keep this setup on a dev machine
  only.
