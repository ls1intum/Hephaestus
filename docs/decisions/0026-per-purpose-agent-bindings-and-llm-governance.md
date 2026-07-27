# ADR 0026: Per-purpose agent bindings and governed OpenAI-compatible LLM catalog

**Status:** Accepted (amended 2026-07-26 — named-agent-config model deleted)
**Date:** 2026-07-24
**Authors:** Felix T.J. Dietrich
**Builds on:** [ADR 0006](0006-llm-proxy-on-coordinator-trust-model.md) (in-app LLM proxy as the sole credential path), [ADR 0025](0025-agent-job-queue-on-postgresql.md) (PostgreSQL agent job queue)

> **Amendment (2026-07-26):** the transitional `agent_config` mirror described below is gone. The
> table, the `practice_config_id` / `mentor_config_id` scalar pointers on `Workspace`, and the
> write-through `sync` are deleted; `WorkspaceAgentBinding` is the sole `ModelBindingSource` and the
> practice-review settings page reads bindings directly. Text below that describes the mirror as
> present is retained as the record of the decision, not as a description of the current code.

## Context

Two problems sat on top of the LLM configuration surface (#1368):

1. **The wrong abstraction for "what model runs what."** `agent_config` was a *named execution
   profile* (model + timeout + concurrency + internet). The thing that actually decided which model
   ran practice detection versus the mentor was two scalar FK columns on `Workspace`
   (`practice_config_id`, `mentor_config_id`). So the object an admin created was not the object that
   bound a purpose, and the flow was scattered across three pages under several names. Worse,
   `AgentJobService` fanned out to *every* enabled config when a workspace had no explicit binding —
   submitting one detection job per config for the same event, at N× the cost and N× the feedback.

2. **Credentials, base URL, and price lived on the wrong owner.** They were per-workspace
   `agent_config` fields. Making `base_url` workspace-editable turns the in-app proxy (which attaches
   the server-held key, per ADR 0006) into a workspace-admin-controlled SSRF + credential-exfil
   primitive; making price workspace-editable lets a capped workspace set the numerator of its own
   cap. Security forces these up to instance ownership.

Alongside this, the monthly budget cap (#1368) had correctness gaps: over-cap queued jobs were
cancelled although the UI promised they would resume; the proxy was ungated in flight; a crashed job
recorded zero cost; and a blind `WARN`/`BLOCK` policy knob let unpriced spend evade a cap.

## Decision

**A two-scope catalog, owned where security requires.** Instance administrators own an
`app_admin`-scoped catalog (`llm_connection` → `llm_model`, with pricing and per-workspace sharing
grants) registered GLOBAL in the tenancy layer; workspaces may, when the instance allows, own a
tenant-scoped BYO catalog (`workspace_llm_connection` → `workspace_llm_model`). The two catalogs stay
separate tables — a merged table with a nullable `workspace_id` would reopen the cross-tenant leak
class and blur the credential blast radius. Model availability (enabled + connection enabled +
supported protocol + visibility/grant) is decided in exactly one place, `LlmModelResolver`.

**Per-purpose bindings replace named configs.** A new `workspace_agent_binding` row binds exactly one
purpose (`PRACTICE_DETECTION` | `MENTOR`) of a workspace to exactly one catalog model plus execution
limits — `UNIQUE(workspace_id, purpose)`, a DB CHECK that exactly one of the instance/workspace model
is set, and a tenancy-safe composite FK to workspace models. No row means the purpose is off; there
is no implicit fan-out. The object the admin configures *is* the purpose→model binding. Detection's
three job types (pull-request, issue, conversation review) share one binding; a future job type that
needs its own model is a new purpose value, not a schema change.

**One resolve/admit seam.** A `ModelBindingSource` interface (instance/workspace model + workspace +
enabled + limits) is implemented by the binding, so the
resolver and admission service work through one shape without branching. Admission row-locks the binding by
`(workspace, purpose)` before freezing its price, exactly as it locked a config by id.

**One pricing authority.** Cost is derived from the admission-frozen `LlmPriceSnapshot` for both
detection and mentor; the `llm_usage_event` ledger freezes the applied per-1M rates per event as the
authoritative price history. The legacy per-1k `model_pricing` table is retired.

**A budget cap that is honest.** The monthly cap holds queued detection jobs (re-eligible via
`available_at`) instead of cancelling them on the spot; the hold is bounded — `AgentJobExecutor`'s
`BUDGET_HOLD_MAX_AGE` cancels a job that is still over cap seven days after it was queued, because a
month-old review is noise and an unbounded hold loops forever. The proxy refuses new calls once a
workspace is over cap,
a crashed job bills the calls it made from proxy-accumulated counters, and enforcement is structural:
a capped workspace with an unverifiable month is paused (a cap you cannot verify is not a cap) while
an uncapped workspace is never paused — the `WARN`/`BLOCK` knob is removed.

## Consequences

- The runtime resolves a model only from a binding. `agent_config` and the scalar pointers were
  demoted to a transitional write-through mirror and have **since been deleted** (see the amendment
  note at the top of this ADR); `WorkspaceAgentBinding` is now the sole `ModelBindingSource`.
- **Operators:** a workspace that relied on the implicit fan-out must explicitly assign a detection
  model; the instance `WARN`/`BLOCK` "usage without a known price" setting is gone. Nothing to
  migrate — the setting was never persisted, so no column is created or dropped for it; enforcement
  is structural instead.
- Credential isolation is structural, not a WHERE-clause promise: a workspace-scoped query can never
  select an instance key, and the proxy resolves the URL and key together from the live row so a
  repointed host can never be paired with a rotated key.
