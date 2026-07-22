# ADR 0025: Agent job queue moves off NATS onto PostgreSQL

**Status:** Accepted (amended 2026-07-21 — fairness + fencing fix wave)
**Date:** 2026-07-21
**Authors:** Felix T.J. Dietrich
**Builds on:** [ADR 0005](0005-two-role-runtime-via-conditional-on-property.md) (two-role runtime, original agent NATS consumer), [ADR 0006](0006-llm-proxy-on-coordinator-trust-model.md) (LLM proxy job-execution capability gate), [ADR 0013](0013-no-jetstream-dlq-stream.md) (no JetStream DLQ stream)

> **Amendment (2026-07-21, adversarial review fix wave):** Two gaps in the initial cutover, both
> fixed without changing the decision above:
>
> - **Head-of-line starvation.** The poll candidate query (`findQueuedIdsOldestFirst`) was a plain
>   `WHERE status='QUEUED' ORDER BY created_at LIMIT n`. If the oldest `n` QUEUED rows all belonged to
>   a config already at its `max_concurrent_jobs` cap, every claim attempt in that batch correctly
>   skipped them — but a younger, immediately-runnable job from a *different* config never even
>   entered the candidate batch, and could starve indefinitely behind an unclaimable backlog. The
>   query now excludes candidates whose config is already at its RUNNING cap (a correlated subquery,
>   supported by a new partial index `ix_agent_job_running_config`); the per-row `FOR UPDATE`
>   concurrency recheck inside the claim transaction remains the authoritative gate, so a stale read
>   in this predicate costs nothing beyond an ordinary skipped candidate.
> - **Unfenced orphan requeue.** `requeueOrphan` CAS'd on `status='RUNNING'` alone. If a job was
>   requeued by the sweeper and immediately re-claimed by a live sibling before a second, belated
>   sweep pass (or a second replica's concurrent pass) processed the same stale orphan snapshot, the
>   belated requeue matched the row again — status was RUNNING once more, just under the new owner —
>   and silently stole the sibling's legitimately in-progress job back to QUEUED. `requeueOrphan` now
>   additionally fences on `worker_id = :deadWorkerId` (the id the caller identified as dead) and
>   enforces the retry cap in the SQL `WHERE` itself, not just in the caller. The same CAS is reused by
>   the worker drain path (see ADR 0009's amendment) so a draining worker's own jobs return to the
>   queue through the identical fenced mechanism.
>
> Also new: `AgentJobExecutor`'s poll capacity is now bounded by the sandbox executor's actual free
> thread-pool slots (previously only `WorkerCapacityState.reviewMax`, an independently-configured
> knob that can exceed the pool size), and a sandbox-pool rejection backs off the poll loop instead of
> immediately re-claiming — closing a retry-budget-burning churn loop a saturated pool could trigger.

## Context

The agent job queue (practice review, mentor-triggered work) ran on a NATS JetStream stream
(`AGENT`) that carried nothing but job ids: a worker pulled an id off the stream, then loaded the
actual `agent_job` row from PostgreSQL to do anything with it. The `agent_job` table was already
the source of truth for job state, retry count, and outcome — JetStream only decided *which*
worker got to claim *which* id next, a job JetStream is not needed for once the claiming worker
reads and writes the same database the job lives in.

That left two systems asked to agree about one queue:

- **Duplicated guarantees.** `FOR UPDATE SKIP LOCKED` claiming, worker fencing via
  `worker_registry` heartbeats, retry budgets, and orphan-job sweeps were all already implementable
  — and, for orphan sweeps, already necessary — against Postgres alone, because a worker that dies
  mid-job leaves its row claimed but its JetStream ack pending, and only a Postgres-side sweep (not
  JetStream redelivery, see ADR 0013) can find that row. JetStream's ack/redelivery machinery
  (`ack-wait: 70m`, `max-deliver: 5`, `max-ack-pending`) was a second, independently-tuned copy of
  guarantees the row already needed to provide.
- **Drift risk.** An id resurfaces on the stream and the row it points at could already be
  claimed, cancelled, or gone (workspace purge) by the time a worker reads it — every consumer
  already had to re-check the row before trusting the id. The id-only stream added a
  synchronization surface with no independent guarantee of its own.
- **Infrastructure cost disproportionate to the smallest deployments.** A single-operator instance
  running the monolith (ADR 0005) needs NATS today only because the agent queue does — webhook
  ingest and SCM/Slack sync are the only remaining consumers, and self-host installs that skip
  those integrations entirely had no other reason to run a JetStream stream, size its memory/file
  limits, or reason about its `AGENT` stream in troubleshooting.

## Decision drivers

- Prefer one source of truth over two that must be kept synchronized by convention.
- Preserve every current guarantee (mutual-exclusion claiming, worker fencing, bounded retries,
  orphan recovery) — this is a delivery-mechanism change, not a semantics change.
- Reduce the operational surface for the smallest self-host deployments (ADR from #1381) without
  removing NATS where it is still load-bearing (webhook ingest, ADR 0008; SCM/Slack sync).
- Keep the worker's poll cost low enough that dropping push-based delivery is a non-issue in
  practice.

## Considered options

1. **Keep JetStream, harden it** — add a Postgres-side reconciliation pass that periodically
   re-validates every claimed id against the `agent_job` table, on top of the existing ack/retry
   config. Closes the drift risk but keeps both systems and their independent tuning knobs.
2. **Poll-based delivery straight from `agent_job`** — each worker replica polls the table on an
   interval, claims a batch with `FOR UPDATE SKIP LOCKED`, and the same table row is the only place
   retry count, ownership, and outcome live.
3. **Move to a different broker** — out of scope; would trade one duplicated-truth problem for
   another broker to run.

## Decision

Option 2. The agent job queue is delivered by polling `agent_job` directly:

- `AGENT_ENABLED` (default `false`) replaces `AGENT_NATS_ENABLED` as the flag that lets a JVM
  claim and execute jobs, combined with the worker runtime role exactly as before
  (`hephaestus.agent.enabled AND hephaestus.runtime.worker.enabled` — see ADR 0005's amendment).
- `AGENT_POLL_INTERVAL` (default `1s`) controls how often each eligible replica polls for claimable
  work.
- `AGENT_CLAIM_BATCH_SIZE` (default `5`) bounds how many rows one poll claims via
  `FOR UPDATE SKIP LOCKED`, so replicas divide the backlog instead of contending for it.
- `AGENT_MAX_RETRIES` (default `5`) replaces JetStream's `max-deliver` as the bound on how many
  times a job is retried before it is left failed.
- `AGENT_NATS_ENABLED`, `HEPHAESTUS_AGENT_NATS_SERVER`, `AGENT_NATS_MAX_ACK_PENDING`, and
  `AGENT_NATS_FETCH_BATCH_SIZE` are removed; the `AGENT` JetStream stream is abandoned (operators
  may delete it, `nats stream rm AGENT`, as optional cleanup — nothing depends on it existing or
  not).
- Worker fencing and orphan recovery are unchanged: `worker_registry` heartbeats and Postgres-side
  claim expiry, already the actual mechanism that survived a worker dying mid-job, now govern
  claim loss uniformly instead of racing a JetStream ack-wait timer that pointed at the same row.
- NATS is untouched everywhere else. Webhook ingest (ADR 0008) and SCM/Slack sync consumption keep
  their own JetStream streams and consumers; `NATS_ENABLED` / `HEPHAESTUS_SYNC_NATS_SERVER`
  continue to gate exactly what they gated before this change.

## Consequences

**Positive.**

- One source of truth for job state, claiming, and retries — no second system to keep in sync by
  convention, no id that can point at a row already claimed, cancelled, or purged elsewhere.
- Operational simplicity: a deployment that needs practice review but not webhook ingest or SCM/
  Slack sync no longer needs NATS running at all. The smallest self-host installs drop a stateful
  dependency entirely.
- Worst case, a claimable job waits up to `AGENT_POLL_INTERVAL` before a replica picks it up —
  bounded, predictable added latency at job start, not at job execution.

**Negative / accepted.**

- **Polling load.** Every eligible replica runs one cheap, indexed query per `AGENT_POLL_INTERVAL`
  (default: once a second) against `agent_job`, whether or not there is work. This scales linearly
  with worker replica count; it does not scale with job volume, so it stays cheap at the replica
  counts this system operates at today.
- **No push-based wakeup.** A job submitted the instant after a poll waits out the rest of the
  interval rather than being dispatched immediately, unlike JetStream's push consumer. Acceptable
  given the default 1s interval against jobs that run for minutes.

## Revisit trigger

Worker replica count grows to where polling load on `agent_job` becomes measurable against the
database's own budget; or a push-based wakeup (e.g. `LISTEN`/`NOTIFY`) becomes worth the added
complexity to shave the poll-interval latency off job start.

## Hardening (2026-07-21)

A pressure test against the OSS queue field (Oban, River, pg-boss, Graphile Worker, Solid Queue,
good_job, Que) confirmed the design above — SKIP LOCKED claim, partial indexes, worker fencing,
retry cap in SQL, pure polling at this scale — matches or beats free-tier equivalents. It also
surfaced concrete gaps, closed here:

- **Retention.** `agent_job` had no pruning — 10k jobs/day is ~3.65M rows/year, each carrying up to
  64KB of `container_logs`. `AgentJobRetentionService` now runs two batched passes (`AGENT_PAYLOAD_RETENTION`,
  default 14 days: strip `container_logs`/`output` to `NULL`; `AGENT_ROW_RETENTION`, default 90
  days: delete the row outright), plus autovacuum tuning on `agent_job` and `worker_registry`
  (`autovacuum_vacuum_scale_factor = 0`, an absolute threshold instead) — Oban's own documented
  recommendation for a high-churn queue table.
- **Backoff + `available_at`.** A requeued job (orphan recovery, worker drain, or a classified
  infra failure — see below) used to become instantly reclaimable, so a crash-looping job burned
  its whole retry budget in seconds. `agent_job.available_at` (backed by
  `ix_agent_job_queued_available`) now gates the poll candidate query; `requeueOrphan` sets it to
  `now() + backoff`, a quartic-with-jitter schedule (`AgentJobBackoff`) capped at 15 minutes. A
  `CHECK` constraint on `status` closes the gap between the six documented `AgentJobStatus` values
  and what the column actually enforced.
- **Error classification.** Every execution failure used to become `FAILED` unconditionally,
  including sandbox-infrastructure blips (Docker daemon unreachable, image pull failure) that have
  nothing to do with the job itself. `AgentJobExecutor#handleExecutionFailure` now classifies
  provably-infra failures (`SandboxException`, `IOException`) as retryable — requeued with backoff,
  bounded by the same `retry_count < max-retries` cap every other requeue path uses — while
  everything else (a non-zero agent exit, a parse/envelope-mismatch failure, an unclassified
  exception) still fails immediately, exactly as before.
- **Token rotation on requeue.** `requeueOrphan` used to leave the job's `job_token` unchanged, so a
  zombie sandbox that was merely network-partitioned (not actually dead) could keep authenticating
  against the LLM proxy once a sibling worker re-claimed the same row — both could spend against
  the same job. The requeue now mints and stores a fresh token/hash pair; the old one is dead the
  moment the CAS commits, whether or not the zombie ever notices.
- **Delivery recovery + dedup.** A job stuck at `delivery_status = PENDING` (the executor crashed
  between the terminal write and finishing delivery) was previously unrecoverable through the
  operator-facing retry endpoint, which only accepts a `FAILED` source. `AgentJobZombieSweeper`
  now sweeps PENDING deliveries older than ~10 minutes and re-attempts them through
  `AgentJobLifecycleService#recoverStuckDelivery`, bounded by a `delivery_attempts` column (3
  attempts, then FAILED). Before re-posting, the handler checks whether a comment carrying the
  job's marker already landed (`JobTypeHandler#findExistingDelivery` /
  `FeedbackChannel#findExistingSummary`) — closing the crash window where the comment posted but
  the id was never persisted. Implemented for GitHub (reuses the existing `GetPullRequestComments`/
  `GetIssueComments` queries); GitLab has no equivalent reusable listing query today, so it is left
  unsupported — a recovery retry there falls through to a normal re-post rather than half-building
  a bespoke discussions-pagination path for this hardening slice.
- **Queue health metrics.** `agent.queue.depth`, `agent.queue.oldest_age_seconds` (the canonical
  health signal — depth alone can't distinguish "briefly busy" from "stuck"), and
  `agent.queue.running` are sampled every 15s by `AgentQueueHealthSampler`. `agent.job.claim.latency`
  times claim minus `available_at`; `agent.job.execution.duration` is now tagged by `jobType` and
  outcome `status` (previously untagged and only recorded on the success path). The poll loop
  sleeps `pollInterval × (0.9–1.1)` instead of a fixed interval, so replicas configured identically
  don't all poll in lockstep.

Deliberately not built in this pass (documented, not forgotten): per-workspace fairness lanes,
immutable per-attempt records, and a full multi-class decomposition of `AgentJobExecutor` — these
ride with the replay/backfill epic (#1354), which is what actually needs them.

## Fix wave (2026-07-21, adversarial review of the hardening commit)

A second adversarial pass over the hardening above found 14 issues, all closed here except where
noted as a documented residual:

- **BLOCKER — retention cascaded into feedback history.** `feedback.agent_job_id` carries
  `ON DELETE CASCADE` (1781092589259-32), which transitively cascades to `feedback_observation`,
  `feedback_placement`, and `reaction` — append-only research/product data. The 90-day row-delete
  pass would have silently destroyed it. `deleteTerminalRowsOlderThan` now excludes any job still
  referenced by `feedback` (`NOT EXISTS`); those rows already shed their heavy payload at 14 days,
  so they stay lightweight, not unbounded. `1784636803503-40` also hardens the FK itself from
  CASCADE to RESTRICT so this class of bug cannot regress silently — verified no application code
  deletes `agent_job` rows outside the retention service.
- **Retention vs. in-flight delivery.** Both the strip and delete passes now exclude
  `delivery_status = 'PENDING'` — a job whose delivery has not landed yet needs its `output` for a
  delivery-recovery retry to compose from, and must not be deleted out from under that retry.
- **Stale poll result bypassing backoff.** `findByIdQueuedForUpdateSkipLocked` now re-checks
  `available_at <= :now` at claim time (bound parameter, not DB `now()`, for the same app-clock
  consistency reason as the queue-health queries) — closing the narrow window where a concurrent
  backoff-requeue between the candidate poll and this claim could still be claimed instantly.
- **Delivery marker mismatch.** The actual PR/issue review delivery path
  (`FeedbackDeliveryService#formatPracticeNote`) embedded `<!-- hephaestus:practice-review:<job> -->`,
  but the delivery-recovery dedup lookup (`PullRequestCommentPoster#findExistingSummaryComment`)
  searched for a different literal (`<!-- hephaestus-agent-feedback:<job> -->`) — the two had drifted
  apart, so the dedup lookup could never match a real posted comment and every recovery retry
  double-posted. Now ONE canonical marker (`PullRequestCommentPoster#summaryMarkerFor`), used by
  every formatter and the lookup alike; a round-trip regression test formats via the real handler
  path and asserts the lookup marker is contained in it.
- **Tri-state dedup (not `Optional`).** `FeedbackChannel#findExistingSummary` and
  `JobTypeHandler#findExistingDelivery` returned an `Optional` that collapsed "confirmed absent" and
  "could not determine" (rate limit, transport error, unsupported channel) into the same empty value
  — every lookup FAILURE silently fell through to "post again". Both are now a tri-state
  `FOUND`/`ABSENT`/`UNKNOWN`: only `ABSENT` proceeds to post; `UNKNOWN` leaves the delivery PENDING
  for a later attempt (failing terminally once the attempt cap is exhausted) instead of guessing.
  GitHub's lookup now paginates (bounded, 3 pages) and reports `ABSENT` only once `hasNextPage=false`
  confirms every comment was scanned — exhausting the page budget with more comments left is
  `UNKNOWN`, never `ABSENT`. GitLab has no listing query (unchanged from the prior amendment) and now
  explicitly returns `UNKNOWN`, so it never auto-reposts on recovery — only records a confirmed match
  or exhausts the attempt cap.
- **`delivery_attempts` was a counter, not a lease.** The attempt-counter CAS
  (`claimDeliveryRecoveryAttempt`) only guarded against two callers claiming the identical attempt
  number concurrently — it did not stop a SLOW attempt spanning multiple 5-minute sweep passes from
  being superseded by a later one while `delivery_status` stayed PENDING throughout, and the final
  DELIVERED/FAILED write was unconditional (`updateDeliveryStatus`), so whichever attempt finished
  LAST always won — including a stale FAILED clobbering an in-flight or already-succeeded DELIVERED.
  Every terminal write in `recoverStuckDelivery` is now fenced via
  `transitionDeliveryStatusFenced(..., expectedAttempts)` on the exact attempt token this call
  claimed; a superseded attempt's write matches no row and is logged, not silently lost.
- **Infra-failure classification too broad.** `isRetryableInfraFailure` treated every
  `SandboxException` as retryable, including validation/config failures (path traversal, input-size
  limits, a misconfigured network policy) that are deterministic across retries, and
  `DockerSandboxAdapter`'s catch-all wrap of an unexpected exception — an unknown defect. A new
  narrower `SandboxInfrastructureException` subtype is now reserved for failures PROVABLY caused by
  transient infra (an actual `DockerException`/disk-I/O wrap from `DockerClientOperations`,
  `SandboxContainerManager`, or the file-injection paths in `SandboxWorkspaceManager`); validation and
  the catch-all wrap stay the broader `SandboxException` and fail fast, unchanged.
- **Retried failures could double-spend invisibly.** A job requeued after a classified infra failure
  had already started executing (past claim, `RUNNING`) and could carry real LLM spend, but no
  ledger row was written before the retry bought another run — N retries of one job could
  under-count spend by up to N-1 runs. `handleExecutionFailure`'s successful-requeue branch now
  records an UNPRICED ledger row unconditionally before returning, mirroring the pattern already used
  for cancellation and drain (the ledger's unique `source_id` makes a spurious duplicate safe).
- **Token rotation vs. in-flight proxy streams — residual window, documented rather than closed.**
  `requeueOrphan`'s token rotation already prevents a NEW proxy request from authenticating with the
  old token (the row's `job_token_hash` no longer matches once rotated, and the CAS also moves the
  row out of `RUNNING` for the moment of rotation). What it does NOT do: `JobTokenAuthenticationFilter`
  authenticates once per request at entry, and `LlmProxyController#doProxy` then streams the upstream
  response (SSE calls can run minutes) with no re-validation at chunk boundaries — a request already
  past authentication when rotation happens keeps streaming to completion on its original,
  now-superseded token. Closing this fully would mean re-validating mid-stream, which conflicts with
  a deliberate existing choice (`LlmProxyWebClientConfig` runs with no read-idle timeout, because LLM
  SSE streams go silent during model "thinking" — a mid-stream liveness check would need its own
  design, not a quick fence). Chosen: document the window rather than build a partial fence; a
  worker-drain or infra-retry requeue during an active LLM call can still incur one extra concurrent
  call's cost until that call naturally completes or times out (`responseTimeout=300s`).
- **Migration locks on the hot queue table.** The four `#1368` hardening indexes
  (`ix_agent_job_running_config`, `ix_agent_job_queued_available`, `ix_agent_job_delivery_pending`,
  `ix_agent_job_retention`) were created with plain `CREATE INDEX`, and `ck_agent_job_status` with a
  plain `ADD CONSTRAINT` — both take an `ACCESS EXCLUSIVE` lock for the duration on a table the poll
  loop hits every second. `1784636803503-41` through `-46` supersede them: `CREATE INDEX CONCURRENTLY`
  (each in its own `runInTransaction="false"` changeset — required, since `CONCURRENTLY` cannot run
  inside a transaction block) and the CHECK re-added `NOT VALID` then validated in a separate
  changeset (`VALIDATE CONSTRAINT` takes `SHARE UPDATE EXCLUSIVE`, which blocks other schema changes
  but not ordinary reads/writes).
- **Retention runs on every replica, unbounded.** `AgentJobRetentionService#runRetention` now carries
  `@SchedulerLock` (ShedLock, already used elsewhere in this codebase for retention/cleanup jobs —
  see `ConfigAuditRetentionJob`), single-flighting the sweep across server-role replicas, and each
  batch-loop pass is capped at a 5-minute wall-clock budget — a fresh, large backlog resumes on the
  next 6-hour run instead of running unbounded in one pass.
- **Queue-depth gauge was backlog-linear.** `AgentQueueHealthSampler` ran three separate COUNT/MIN
  queries every 15s — each an index scan, most expensive exactly when an incident has inflated the
  backlog. Now one query (`AgentJobRepository#queueHealthSnapshot`, `FILTER` clauses) returns all
  three signals in one pass; a sample failure keeps the last-good gauge values (rather than a
  misleading momentary "queue is empty") and increments a new
  `agent.queue.health.sampler.failures` counter; the interval is widened to 30s.
- **Backoff cap/overflow.** `AgentJobBackoff` capped the base BEFORE applying jitter, so the +10%
  jitter leg could push the final wait past the documented 15-minute cap; the attempt number was also
  unbounded (`max-retries` has no configured ceiling), risking `long` overflow in `n^4` at an
  extreme operator-set value. The cap is now enforced AFTER jitter, and `n` is clamped to a safe
  ceiling before the power is computed (a value far past where the cap would apply anyway, so
  behaviour is unchanged for every realistic `max-retries`).
- **Autovacuum preconditions too loose.** The `-36`/`-37` changesets' preconditions checked only
  whether *any* `autovacuum_vacuum_scale_factor` option was present, not the specific value this
  release sets — a stale or partially-applied option would silently skip the changeset. `ALTER TABLE
  ... SET (...)` is inherently idempotent, so the corrective changesets (`1784636803503-47`/`-48`)
  reapply it unconditionally rather than trying to precondition-match an exact prior state.
