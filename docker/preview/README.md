# Preview deployment setup

Coolify deploys one application stack per pull request from `compose.app.yaml`. A preview has its own
PostgreSQL and git-checkout volumes, but shares staging's NATS event stream and starts from a sanitized
copy of the staging database.

## Runtime topology

| Concern | Preview behavior | Why |
|---|---|---|
| PostgreSQL | Private volume per PR | Tests cannot mutate staging or another preview |
| Seed data | One live `pg_dump` from `app-postgres-1` on first deploy | Realistic data without touching the staging data volume |
| NATS | Shared staging `nats-server:4222` on `shared-network` | Previews see the same integration events as staging |
| NATS consumer | `${SERVICE_NAME_APPSERVER}-consumer` | Every preview has an independent JetStream cursor |
| Consumer lifetime | JetStream reaps the durable after 72h unbound | A deleted preview cannot delete its own cursors |
| App image | Immutable `${SOURCE_COMMIT}` tag from CI | The API and SPA both reflect the PR without a shared mutable tag |
| Agent runtime | Enabled, one sandbox at a time | A selected workspace can run real reviews without exhausting the host |
| Review automation | Paused in every cloned workspace | A clone cannot post reviews until an admin explicitly opts it in |

Before the first restore, the seeder recreates only the uniquely resolved preview database. The marker
`/var/lib/postgresql/data/.hephaestus-preview-seeded` is written only after the restore and sanitization
both succeed. A failed or partial first attempt can therefore retry cleanly, while redeploying an
already seeded PR preserves changes made while testing. A new preview volume gets a fresh staging clone.

## Silence policy

Before the application server starts, `seed-loader`:

1. disables automatic and manual practice-review triggers for every workspace;
2. disables each `PRACTICE_REVIEW` model binding while preserving its selected model;
3. pauses recurring review sweep schedules;
4. cancels cloned active sync and agent jobs; and
5. marks cloned pending feedback deliveries failed so recovery cannot publish a staging result.

It deliberately does **not** disable the practices feature. The review settings and existing data stay
visible in the UI. To test reviews for one workspace, enable its practice-review model binding and then
enable the desired manual or automatic trigger in that workspace's practice-review settings. Those
changes persist for the lifetime of the PR preview.

## One-time server and Coolify setup

1. Keep staging's `nats-server` attached to the external Docker network `shared-network`.
2. Point the Coolify application at `/docker/preview/compose.app.yaml` and enable preview deployments.
3. Keep **Connect to predefined network** enabled for Coolify proxy routing. The app server explicitly
   joins `shared-network` as a second network.
4. Set `PREVIEW_SEED_SOURCE_CONTAINER=app-postgres-1` if the staging Compose project/container name
   ever changes. The source user defaults to `root` and database to `hephaestus`.
5. Assign the web and API services sibling wildcard domains. With the current template these are
   `pr<id>.hephaestus.felixdietrich.com` and `pr<id>.api.hephaestus.felixdietrich.com`.
6. Leave the preview `IMAGE_TAG` unset. Coolify injects `SOURCE_COMMIT`, and CI publishes the matching
   application-server image before the preview update is requested.
7. Put every preview-safe value in the application's **base** environment variables, not in Coolify's
   separate preview-variable scope. See below.

## Why the safe values live in Coolify's base scope

Coolify documents a second variable scope that only preview deployments see. On the installed version
that scope is not injected into a Docker Compose application: a preview started with variables
duplicated across both scopes receives the base value, and the preview copy is silently ignored. A
preview would then inherit whatever the base entry happens to say — for this stack that meant agent
support off, larger sandbox limits than the host can afford, and startup sync enabled.

This Coolify application exists only to host PR previews; staging itself is deployed by GitHub Actions
from `docker/compose.app.yaml` and never reads these entries. So the base scope is the safe place for
them, and duplicating a key across both scopes is what to avoid — the duplicate reads like the
effective value and is not.

| Variable | Value | Without it |
|---|---|---|
| `AGENT_ENABLED` | `true` | Reviews cannot be tested at all, even deliberately |
| `NATS_SERVER`, `HEPHAESTUS_SYNC_NATS_SERVER` | staging NATS | The preview sees no integration events |
| `SANDBOX_MAX_CONCURRENT`, `SANDBOX_CPUS`, `SANDBOX_MEMORY_BYTES` | `1`, `1.0`, `2147483648` | One preview's agent run can starve the host |
| `MONITORING_RUN_ON_STARTUP`, `MONITORING_SYNC_CRON` | `false`, `-` | The clone starts a full lifecycle sync on boot |

`-` is Spring's disabled-cron value; an empty string fails the boot instead of disabling the schedule.

The Docker socket mount is privileged access to the host Docker daemon even though it is mounted
read-only. It is intentionally confined to the trusted preview application and used only for
`pg_dump`, restore, and sandbox execution. Coolify's `SERVICE_NAME_POSTGRES` is a network alias, so
the seeder resolves the physical target container only when exactly one container has both its own
Compose project label and that service label.

## Failure behavior

Seeding is fail-closed. If staging Postgres is unavailable, the target container cannot be resolved,
restore fails, or sanitization fails, `seed-loader` exits non-zero and the application server does not
start. This prevents an apparently healthy but empty or unsanitized preview. Fix the source and
redeploy; because the success marker is absent, the next deployment retries the clone.

Useful checks on the staging VM:

```bash
docker ps --format '{{.Names}}' | grep -E '^(app-postgres-1|core-nats-server-1)$'
docker network inspect shared-network
docker exec app-postgres-1 pg_dump -U root -d hephaestus --schema-only >/dev/null
```

## File

`compose.app.yaml` is the complete per-PR application stack. Shared NATS and webhook handling belong
to staging and are deliberately not duplicated in this directory.
