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

## The flow (≈3 steps)

```bash
# 1) Stack up: server on the e2e profile (Postgres + NATS auto-start) + vite on :4200.
pnpm dev:server:e2e        # = ./mvnw -f server spring-boot:run -Dapp.profiles=local,e2e
pnpm dev:webapp            # in another terminal
#    For the agent review loop you also need the sandbox + LLM proxy reachable — see "Running the
#    review" below; the app server port and the LLM-proxy port must match.

# 2) Configure everything with your own PAT + LLM key (idempotent; re-run any time).
scripts/e2e-setup.sh \
  --gitlab-pat glpat-… \
  --llm-key sk-… --llm-base-url https://llm-gateway.example/api --model openai/gpt-oss-120b

# 3) Open http://localhost:4200 → "Dev sign in" → your workspace → run the trigger the script printed.
```

`scripts/e2e-setup.sh` does the rest through the dev-login API: resolves the real SCM user behind your
PAT, seeds the dev account's identity + membership, **creates the workspace + connection**, wires the
LLM runtime and binds it to practice detection, and creates the three practices (Submit reviewable work
/ Act on feedback / Plan & scope issues). Every flag has an `E2E_*` env fallback. Run with `--help`-less
bad args to see the list; key ones: `--server-url` (self-hosted GitLab), `--github-pat`, `--repo
ns/project` (monitor a repo), `--app-url`, `--db-url`.

## Running the review

Two ways to trigger a real practice-detection review:

- **Dev trigger (smoothest — no tunnel).** The script prints it:
  `curl -X POST "http://localhost:38080/api/dev/trigger-review?prId=<id>&workspaceId=<id>"`.
  Workspace creation syncs the connected repo's MRs, so a `prId` usually already exists.
- **Real webhook (full ingestion).** Expose the app with a tunnel (Cloudflare named tunnel is the most
  stable) and set `hephaestus.webhook.external-url` + a `secret` (≥32 chars); the GitLab connection
  then auto-registers a group hook (needs Owner + Premium on the group). Push an MR → review runs.

The agent runs in a Docker sandbox (`ghcr.io/ls1intum/hephaestus/agent-pi`) and calls the LLM through
the in-app proxy — the only path a sandbox has to a provider key, never injected directly. When the app
runs on the **host** (not a container) the sandbox must reach the proxy via the host gateway, which
forces `allowInternet=true` on the runtime — the script's `--llm-base-url` path. Findings are posted
back to the MR and shown under the workspace's AI → Activity view.

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
