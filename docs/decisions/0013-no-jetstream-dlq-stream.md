# ADR 0013: No dedicated JetStream DLQ stream — poison handler ACKs after N redeliveries

**Status:** Accepted
**Date:** 2026-05-25
**Authors:** Integration framework polish (#1198), Wave 9 revert

## Context

Pass 9 (commit `976b4dbf9`) added a dedicated JetStream **dead-letter stream** + DLQ
consumer to capture messages that exceeded `maxRedeliver` instead of letting
`IntegrationPoisonHandler` ACK them with a counter bump. The next audit pass reverted
it (commit `b6970db0b`). This ADR records *why* the revert is the standing design.

The original argument for a DLQ stream:
- Operator visibility into the actual bytes that failed (rather than only
  `integration.consumer.poison` Micrometer counter).
- Replay-after-fix workflow without re-running the upstream vendor delivery.
- Symmetry with the agent-runtime DLQ stream (which exists for a different reason —
  long-running agent jobs whose container died mid-execution).

What the revert noticed:
- **Dedup window already covers vendor-retry storms.** GitHub redelivers up to 8 times
  over a few hours with the same `X-GitHub-Delivery` UUID; GitLab redelivers up to 5
  times with `X-Gitlab-Webhook-UUID`; Slack redelivers 3 times. The JetStream stream
  `duplicate-window: 2m` (default) catches the same delivery-id arriving on the same
  subject — vendor retries are not the source of DLQ entries.
- **`IntegrationPoisonHandler` is the right shape for the actual failure modes.** It
  NAKs with exponential backoff up to `maxRedeliver` (default 10), then ACKs + bumps
  `integration.consumer.poison`. The failures it sees are (a) DB constraint races on
  upsert (resolved on retry), (b) sync-target removed mid-flight (legitimate skip),
  (c) malformed payload from vendor schema drift (alerts surface via the counter +
  WARN log + sanitized subject).
- **A DLQ stream would flap on (a) and (b)** — both are recoverable on retry. The
  ACK-after-N policy is the *correct* outcome for both. A DLQ stream that fills with
  transient-race entries is operationally worse than no DLQ stream.
- **Replay-after-fix is not a current workflow.** No oncall procedure documents
  "drain DLQ and re-inject"; vendors retry on their own schedule when their delivery
  fails to ACK; if Hephaestus is the broken party the fix is a code change + the next
  vendor poll cycle redelivers (GitHub) or a manual `gh api` replay (operator).
- **The Micrometer `integration.consumer.poison` counter + WARN log is operator-
  actionable** — a non-zero rate triggers Grafana alert and the log shows
  `sanitizeForLog(subject)` so operators can reason about scope without payload bytes.
- **Capacity.** A DLQ stream at staging traffic (~50 deliveries/day × 4 providers ×
  365 days = ~73k/year) accumulates persistent bytes for an event no one will replay.
- **Operator audit trail.** The `integration_consumer_dlq` Liquibase table that pass-9
  introduced (correlation_id, subject, deliveredCount, bytes) wasn't queried by any
  shipped Grafana dashboard, nor by any operator runbook.

## Decision

`IntegrationPoisonHandler` is the only DLQ mechanism:

- `maxRedeliver = 10` (configurable via `hephaestus.integration.consumer.poison.max-redeliver`).
- Exponential backoff `clamp(base * 2^attempt + jitter, maxDelay)` — `baseDelay = 2s`,
  `maxDelay = 5m`, `jitter = 1s`.
- At `deliveredCount() >= maxRedeliver`: ACK the message (stop occupying an inflight
  slot), bump `integration.consumer.poison{kind=<kind>}` counter, log ERROR with
  `sanitizeForLog(subject)`.
- No persistent record of poisoned message bytes. The operator's primary signal is
  the counter rate.

No `dlq` stream is provisioned in `StreamBootstrap`. No `IntegrationDlqConsumer` bean
exists. No `integration_consumer_dlq` table exists in Liquibase.

## Rejected alternatives

1. **Dedicated DLQ JetStream stream with retention=-1.** Rejected for the reasons in
   Context: flap on transient races, no replay workflow, no dashboards consuming the
   bytes, persistent capacity cost for unactioned data.
2. **DLQ as a separate subject filter on the SAME stream** (e.g. publish poisoned
   messages to `dlq.<kind>.<subject>`). Rejected: still flap-prone, still no replay
   workflow, AND mixes "real traffic" + "operator audit trail" on one stream's storage
   budget.
3. **External DLQ store (S3, etc.).** Rejected: out of scope for #1198, more
   infrastructure to operate, doesn't solve the underlying "no one is going to replay
   this" problem.
4. **In-process bounded-buffer DLQ (last N poison messages in memory).** Rejected:
   loses on pod restart, doesn't scale across replicas, debug-only utility that's
   already covered by the structured log line.

## Consequences

**Positive:**
- Zero operational overhead for the DLQ surface that no one queries.
- Counter-based alerting is the right granularity for the actual failure modes.
- Vendors handle their own retry; we don't duplicate vendor's persistence.
- Simpler infrastructure (one fewer JetStream stream, one fewer Liquibase table).

**Negative:**
- If a future use case actually needs replay-from-DLQ semantics (e.g. a regulatory
  audit requirement: "prove every webhook was processed"), this design has to be
  revisited. The trigger condition is documented below.

## Revisit trigger

Re-open this decision if any of the following land:

1. **Regulatory requirement** to retain every webhook payload regardless of processing
   outcome (e.g. SOC 2 evidence collection for a security audit).
2. **`integration.consumer.poison` counter rate > 0.1 events/hour sustained for a
   week** — at that point we have a class of recurring failures that *would* benefit
   from replay-after-fix.
3. **A vendor adds at-most-once delivery** (current vendors are at-least-once; retries
   absorb transient failures).

## References

- `server/src/main/java/de/tum/cit/aet/hephaestus/integration/consumer/IntegrationPoisonHandler.java`
- Revert: commit `b6970db0b`
- Original pass-9 add: commit `976b4dbf9`
- Counter dashboard: Grafana panel `integration.consumer.poison{kind}` (per-vendor rate)
