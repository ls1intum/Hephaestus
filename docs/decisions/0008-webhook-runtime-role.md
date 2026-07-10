# ADR 0008: Webhook as a third runtime role (`webhook-server` container)

**Status:** Accepted
**Date:** 2026-05-20
**Authors:** Webhook substrate epic (#1110)

## Context

Two operational facts force the design:

1. **Push events on GitHub and GitLab are NOT manually redeliverable.** Both providers expose
   a redeliver button in the UI, but it is documented to NOT apply to push events. A webhook
   drop during an `application-server` restart is permanent data loss.
2. **App-server restarts are frequent and unavoidable.** Deploys, OOM crashes, config flips,
   schema migrations — all routinely cycle the JVM. The receiver must survive each one.

Restart independence between `application-server` and the webhook receiver is therefore a hard
operational requirement, not a nice-to-have. ADR 0005 anticipated this and listed
*"webhook becomes a third runtime role"* as its explicit revisit trigger.

## Decision drivers

- **Single artifact:** ADR 0005's principle was *"single JAR ships as either role; deploy config
  selects which."* A third role must compose with the existing two without forking the artifact.
- **Default-on monolith:** ADR 0005's DX invariant — zero env vars boots the full stack — must
  hold. `pnpm dev` starts one process that handles everything.
- **No multi-Maven-module split:** ADR 0005 rejected this. We honour that.
- **Cycle through NATS, not direct coupling:** the publisher and the sync consumer agree on the
  subject grammar; nothing else couples them.

## Considered options

1. **Same JAR, third runtime role, separate container** *(chosen)* — Spring profile `webhook`
   activates `WebhookConfiguration`; production deploys two containers from one image.
2. **In-process webhook handler.** Operationally unacceptable: an app-server restart drops
   webhooks. Push events lost permanently.
3. **Multi-Maven-module.** ADR 0005 rejected this for "premature complexity." Still true.

## Decision

Option 1. Concretely:

- A new constant `RuntimeRole.WEBHOOK_PROPERTY = "hephaestus.runtime.webhook.enabled"`. Defaults
  to `true` (matchIfMissing) so the monolith DX invariant holds.
- A new Spring profile `webhook` with overlay file `application-webhook.yml` that sets
  `server.enabled=false`, `worker.enabled=false`, `webhook.enabled=true`, and disables Liquibase
  on the receiver pod.
- A new Docker container `webhook-server` running `ghcr.io/ls1intum/hephaestus/application-server`
  with `SPRING_PROFILES_ACTIVE=prod,webhook`. Traefik routes `/webhooks/*` (strip-prefix) to this
  container's `/gitlab` and `/github` endpoints. The `application-server` container sets
  `HEPHAESTUS_RUNTIME_WEBHOOK_ENABLED=false` so the receiver beans don't load there.
- The pure verifier/builder classes (`HmacVerifier`, `GitLabTokenVerifier`, `GitLabSubjectBuilder`,
  `GitHubSubjectBuilder`, `DedupIdResolver`) live in
  `de.tum.cit.aet.hephaestus.gitprovider.webhook`. JaCoCo package-coverage gate ≥ 0.95 branch on
  this package keeps subject grammar, dedup ID derivation, HMAC verification, and hex encoding
  honest under refactor.
- `WebhookConfiguration` reuses the existing `Connection natsConnection` bean from
  `config.NatsConfig` — no new NATS connection per pod. Publisher uses Resilience4j Retry with
  exponential backoff + ±25% jitter; stream creation is `addStream`-or-`getStreamInfo` with
  WARN-on-drift (never `updateStream`).
- The previously-reserved `RuntimeRole.SERVER_PROPERTY` is **wired** for the first time. It
  gates `ServerSchedulingConfig` (a new `@Configuration` carrying `@EnableScheduling`, extracted
  from `Application.java`), `NatsConsumerService`, and `WorkspaceStartupListener`. This is the
  minimum surface required to prevent duplicate-run pathologies (cron schedulers, durable NATS
  consumers, workspace bootstrap) when the same JAR runs as two containers.
- `WebhookProperties` is moved to `core/webhook/` with a `@NamedInterface("webhook")` declaration
  so both `workspace.GitLabWebhookService` (auto-registration) and `gitprovider.webhook.*`
  (inbound receiver) can depend on it without forming a cycle. Nested `TokenRotation`, `Publish`,
  `Stream`, `Shutdown`, and `Http` blocks sit under the existing `hephaestus.webhook.*` prefix.
  Incoming-request size is capped by `WebhookPayloadSizeFilter` (rejects requests whose declared
  `Content-Length` exceeds `hephaestus.webhook.http.max-payload-bytes` with
  `413 Payload Too Large`) — Servlet `max-http-post-size` only applies to form-encoded payloads,
  not the `application/json` bodies this receiver accepts.
- ArchUnit boundaries: `HexEncodingArchTest` forbids `Integer.toHexString` /
  `Long.toHexString` inside `..gitprovider.webhook..`; `HexFormat.of()` is the only approved hex
  source. `LocaleSafetyArchTest` forbids no-arg `toLowerCase`/`toUpperCase` and
  `Locale.getDefault()` inside the same scope. `RuntimeRoleBoundaryTest` asserts the correct
  property gates on `WebhookConfiguration`, `ServerSchedulingConfig`, `NatsConsumerService`, and
  `WorkspaceStartupListener`, plus role isolation (webhook package cannot depend on
  workspace/practices/agent) and `@EnableScheduling` uniqueness (only on
  `ServerSchedulingConfig`).

## Consequences

- **Restart independence achieved.** Restarting `application-server` no longer interrupts
  webhook reception; the sync consumer catches up on the buffered JetStream messages once it
  comes back. Webhook drops during an `application-server` restart are zero.
- **Single-artifact CI/build cost.** One Maven build, one Docker image, no separate pipeline.
- **Schedulers cannot duplicate.** `@EnableScheduling` is extracted into
  `ServerSchedulingConfig` gated by `SERVER_PROPERTY=true`. The `webhook-server` container sets
  `server.enabled=false` and consequently fires no `@Scheduled` methods — preventing GitHub and
  GitLab sync schedulers, agent zombie sweepers, mentor in-flight reaper, rate-limit eviction,
  contributor cache eviction, and the GitLab webhook health check from double-running.
- **Sync consumer cannot duplicate-register.** `NatsConsumerService` is gated by
  `SERVER_PROPERTY`. Two containers cannot race the same durable consumer name.
- **Workspace bootstrap cannot race.** `WorkspaceStartupListener` is gated by
  `SERVER_PROPERTY`; only the application-server pod runs PAT workspace creation and GitHub App
  installation activation.
- **Liquibase migrations remain owned by application-server.** The webhook profile sets
  `spring.liquibase.enabled=false`. No race between the two pods at boot.
- **DB connection retained on the webhook pod.** Cheaper than gating every workspace-context
  bean; the receiver itself never touches the DB. Operational cost: ~150 MB extra heap and one
  Hikari pool — acceptable.
- **Endpoints `/gitlab` and `/github` (no `/webhooks` prefix on controllers).** Traefik already
  strips the `/webhooks` prefix; preserving the controller paths keeps existing provider
  registrations valid (GitLab rate-limits webhook re-registration at 5 req/min per group).
- **Webhook secret remains a single shared value (`hephaestus.webhook.secret` /
  `WEBHOOK_SECRET`).** Used identically by auto-registration (sent to the provider) and
  verification (incoming HMAC / token). No env var rename.

## Residual risks accepted

The two-container design eliminates the application-server-restart class of webhook drops, but
the receiver still has known failure windows that we are NOT solving here:

- **`webhook-server`'s own restart.** A SIGTERM during in-flight publish runs the
  `WebhookGracefulShutdown` drain (`hephaestus.webhook.shutdown.drain-timeout`, default 15s)
  against an HTTP server already declared closed. Docker's `stop_grace_period: 40s` covers
  Spring's HTTP drain + the NATS drain + margin (formula:
  `stop_grace_period ≥ server.shutdown drain + shutdown.drain-timeout + 5s`). POSTs that arrive
  AFTER Traefik routes away from the dying pod but BEFORE the new pod is in rotation hit a brief
  outage window. Multi-replica + rolling restart closes this; deferred (see Revisit trigger).
- **NATS unavailability.** Resilience4j retry (5 attempts, ±25% jitter, 9s total) absorbs short
  blips. Beyond that the controllers return 503 → provider retries per its own schedule. Push
  events on GitHub/GitLab are NOT in the provider retry set, so a sustained NATS outage drops
  pushes. Mitigation: NATS uptime SLO (operationally tracked, not gated here).
- **Stream config drift.** `WebhookJetStreamBootstrap` is `addStream`-or-`getStreamInfo` and never calls
  `updateStream`. If a future config change widens `duplicate-window` or `maxAge` in the source,
  an operator must apply it manually via `nats stream edit`. WARN-on-drift surfaces the divergence
  in logs; the receiver continues to publish into the pre-existing stream.
- **DataSource on the webhook pod.** The workspace-context filter is wired unconditionally and
  requires a JPA `DataSource` bean. The receiver itself never writes to the DB; the connection
  exists only to satisfy bean wiring. Followup: make the workspace-context filter
  profile-conditional so the webhook pod can boot without Postgres.

## Observability

The receiver exposes Micrometer instruments under the `webhook.*` namespace:

- `webhook.publish{outcome=success|failure}` — counter per NATS publish attempt outcome. The
  receiver's primary SLI.
- `webhook.publish.retry` — counter per Resilience4j retry event. Spike indicates NATS pressure
  or transient broker errors.
- `webhook.rejected{provider, reason}` — counter for every request rejected before publish
  (bad signature, bad token, oversize, invalid JSON, etc.). Lets ops separate scan traffic from
  misconfigured providers.
- Spring Boot's standard `http.server.requests` covers per-endpoint latency / status counts;
  no webhook-specific HTTP histograms are added.

Recommended SLO and alert (operational, defined in the observability stack):

- **SLO:** `rate(webhook.publish{outcome=failure}) / rate(webhook.publish) < 0.01` over a 5-min
  window.
- **Alert — `webhook-server-down`:** `/actuator/health/readiness` returns DOWN for ≥ 60s →
  page on-call. The health indicator goes DOWN on NATS disconnect or unreachable streams.
- **Alert — `webhook-publish-failing`:** SLO breach for ≥ 2 minutes → page on-call.

Dashboards and alert wiring live outside this PR (observability epic).

## Revisit trigger

A real operational need to scale `webhook-server` independently of replica count (e.g., > 50 RPS
sustained per pod) — at which point multi-replica + sticky-cookie + JetStream dedup window
review is appropriate. The `duplicate-window` is currently 2 minutes (JetStream default;
provider redelivery uses the same delivery-UUID so a wider window only protects against
adversarial replays).

## Related

- ADR 0001 — flat top-level layout (updated by this ADR: see "Update" section).
- ADR 0005 — two-role runtime baseline (revisit trigger fired here; webhook is the third role).
- ADR 0006 — LLM proxy on coordinator (unaffected).
- ADR 0007 — sandbox SPI shape (unaffected).
