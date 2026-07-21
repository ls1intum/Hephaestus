# ADR 0025: Agent job queue moves off NATS onto PostgreSQL

**Status:** Accepted
**Date:** 2026-07-21
**Authors:** Felix T.J. Dietrich
**Builds on:** [ADR 0005](0005-two-role-runtime-via-conditional-on-property.md) (two-role runtime, original agent NATS consumer), [ADR 0006](0006-llm-proxy-on-coordinator-trust-model.md) (LLM proxy job-execution capability gate), [ADR 0013](0013-no-jetstream-dlq-stream.md) (no JetStream DLQ stream)

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
  (`hephaestus.agent.enabled AND hephaestus.runtime.worker.enabled` — see ADR 0006's amendment).
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
