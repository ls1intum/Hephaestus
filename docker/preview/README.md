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
| Consumer lifetime | JetStream reaps the durable after `HEPHAESTUS_INTEGRATION_CONSUMER_INACTIVE_THRESHOLD` with no pulls | A deleted preview cannot delete its own cursors |
| App image | Immutable `${SOURCE_COMMIT}` tag from CI | The API and SPA both reflect the PR without a shared mutable tag |
| Agent runtime | Enabled, one sandbox at a time | A selected workspace can run real reviews without exhausting the host |
| Review automation | Paused in every cloned workspace | A clone cannot post reviews until an admin explicitly opts it in |

Before the first restore, the seeder recreates only the uniquely resolved preview database. The marker
`/var/lib/postgresql/data/.hephaestus-preview-seeded` is written only after the restore and sanitization
both succeed. A failed or partial first attempt can therefore retry cleanly, while redeploying an
already seeded PR preserves changes made while testing. A new preview volume gets a fresh staging clone.

## Preview policy

`seed-loader` applies a policy to the restored clone before the application server starts, then
verifies it took effect and only then writes the seed marker. A clone is another instance's live
database, so the policy answers two questions, and it is organised by them.

The SQL is inline in `compose.app.yaml` rather than a mounted `.sql` file: Coolify materialises a
relative bind mount as an empty managed *directory*, which `psql <` reads as zero bytes and exits 0
on. The seed marker is therefore written against the database's own answer rather than against an
exit code — any live trigger, binding, job, pending delivery, or inherited identity fails the
deployment.

### Silence — a clone must not act

1. disables automatic and manual practice-review triggers for every workspace;
2. disables each `PRACTICE_REVIEW` model binding while preserving its selected model;
3. pauses recurring review sweep schedules;
4. cancels cloned active sync and agent jobs; and
5. marks cloned pending feedback deliveries failed so recovery cannot publish a staging result.

It deliberately does **not** disable the practices feature. The review settings and existing data stay
visible in the UI. To test reviews for one workspace, enable its practice-review model binding and then
enable the desired manual or automatic trigger in that workspace's practice-review settings. Those
changes persist for the lifetime of the PR preview.

### Re-home — a clone must not keep the source instance's identity

A preview runs with the **source instance's** `HEPHAESTUS_SECURITY_ENCRYPTION_KEY`, because that is the
only key the cloned rows can be read with. Without it the preview boots and then fails every request
that touches a credential, with `AEADBadTagException: Tag mismatch` in the log. That key also unseals
the source's JWT signing key and decrypts its OAuth client secrets, so three tables are emptied and
rebuilt from this deployment's own configuration:

| Table | Why it cannot be inherited |
|---|---|
| `login_provider` | The source's OAuth apps are registered against the source's hostname, so the provider rejects every sign-in that starts from a preview host |
| `jwt_signing_key` | The preview would otherwise mint its own tokens signed with the source instance's production signing key |
| `issued_jwt` | Cloned sessions belong to the source instance's users |

`LoginProviderService` seeds `login_provider` from the environment whenever a registration id is
absent, so emptying the table hands the preview its own login apps on the next boot — which is why the
preview stack needs `GH_OAUTH_CLIENT_ID`/`GH_OAUTH_CLIENT_SECRET` (and any other provider it should
offer) pointed at an OAuth app
whose callback covers the preview hostnames. A provider with no credentials in the preview environment
is simply not offered; Slack, being link-only, is normally absent for that reason.

Accounts survive all of this: `identity_link` keys on `identity_provider`, not on `login_provider`, so
a cloned user signs in through the preview's own OAuth app and lands on the same account.

## One-time server and Coolify setup

1. Keep staging's `nats-server` attached to the external Docker network `shared-network`.
2. Point the Coolify application at `/docker/preview/compose.app.yaml` and enable preview deployments.
3. Keep **Connect to predefined network** enabled for Coolify proxy routing. The app server explicitly
   joins `shared-network` as a second network.
4. Set `PREVIEW_SEED_SOURCE_CONTAINER=app-postgres-1` if the staging Compose project/container name
   ever changes. The source user defaults to `root` and database to `hephaestus`.
5. Set `HEPHAESTUS_SECURITY_ENCRYPTION_KEY` to the **seed source instance's** key, and point
   `GH_OAUTH_CLIENT_ID`/`GH_OAUTH_CLIENT_SECRET` at an OAuth app whose callback covers the preview
   hostnames — see the re-home policy above for both. The `GH_` prefix is deliberate: GitHub Actions
   reserves `GITHUB_`, and the Compose file maps these onto the application's `GITHUB_OAUTH_*` inputs.
6. Assign the web and API services sibling wildcard domains. With the current template these are
   `pr<id>.hephaestus.felixdietrich.com` and `pr<id>.api.hephaestus.felixdietrich.com`.
7. Leave the preview `IMAGE_TAG` unset. Coolify injects `SOURCE_COMMIT`, and CI publishes the matching
   application-server image before the preview update is requested.
8. Put every preview-safe value in the application's **base** environment variables, not in Coolify's
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

## This file only takes effect once it reaches `main`

Coolify parses the Compose file from the branch configured on the application — `main` — and stores it
on the application record. A preview deployment builds the pull request's images but runs that stored
definition, so a change to `compose.app.yaml` is **not** exercised by the preview of the pull request
that makes it. Reviewing a change here means reading it; the first
preview that actually runs it is the next one created after the change is merged and Coolify has
re-read `main`.

## Host access

Both the seeder and the application server mount `/var/run/docker.sock`. The seeder's `:ro` is a
filesystem flag on a socket inode — it prevents unlinking, not any daemon command — so treat every
one of these mounts as root on the host, confined to the trusted preview application and used only
for `pg_dump`, restore, and sandbox execution.

Coolify's `SERVICE_NAME_POSTGRES` is a network alias, so the seeder resolves the physical target
container only when exactly one container carries both its own Compose project label and that
service label.

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
