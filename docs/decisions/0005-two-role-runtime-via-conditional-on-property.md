# ADR 0005: Two-role runtime topology via `@ConditionalOnProperty`

**Status:** Accepted
**Date:** 2026-05-20
**Authors:** Server foundations epic (#1097)

## Context

The Hephaestus server runs everything in a single JAR today (HTTP, sync NATS, mentor SSE,
scheduled tasks, agent NATS pull consumer, Docker sandbox). As agent-job workload grows,
we want the option to deploy the worker on a separate pod / node without a refactor — a
deploy-config change only.

Two boundaries matter operationally:

1. **server ↔ worker** — Docker sandbox runtime resource isolation.
2. **server ↔ webhook receiver** — restart independence; push events are not redeliverable.

A draft considered five runtime roles (`api`/`ingest`/`worker`/`mentor`/`scheduler`); the
pressure-test concluded mentor and the webhook receiver live in the same JVM as today, and the
scheduler can stay single-replica. Two roles match the actual operational pressure for this
epic; a third role is anticipated but deferred.

## Decision drivers

- Single JAR ships as either role; deploy config selects which.
- Zero env vars → full monolith boots (DX invariant).
- Composition-layer gating: per-`@Configuration` class, not per-bean.
- `@Profile` is reserved for environments; the existing codebase already uses
  `@ConditionalOnProperty` heavily for capability gates.

## Considered options

1. **Two roles (`server`, `worker`) gated by `@ConditionalOnProperty`** — pragmatic, matches operational reality.
2. **Five roles** (`api`, `ingest`, `worker`, `mentor`, `scheduler`) — speculative; no current operational pressure.
3. **`@Profile`-based gating** — overloads environment-vs-topology semantics.
4. **Multi-module Maven** — premature; doubles build complexity for no current win.

## Decision

Option 1. `core.runtime.RuntimeRole` defines the property keys:

- `hephaestus.runtime.worker.enabled` (default true; **wired**) — gates the worker chain
  via `DockerSandboxConfiguration` and `AgentNatsConsumerConfig`
- `hephaestus.runtime.server.enabled` (default true; **reserved**) — placeholder constant
  for the future server-only role. Not wired in this epic: the server-side bean chain
  (HTTP API, sync NATS, mentor SSE, scheduled tasks, agent dispatch) still loads
  regardless. Wiring a real server-only mode lands when a concrete deploy-split need
  surfaces (likely after the BYO-runner / Kubernetes adapter epics).
- `hephaestus.sandbox.llm-proxy.enabled` — capability flag (not a role), see ADR 0006

Every `hephaestus.runtime.*` gate uses `matchIfMissing=true`, enforced by
`RuntimeRoleBoundaryTest`. The webhook receiver remains in-process under the default profile for
this epic; the third role lands when restart independence becomes a hard requirement.

## Consequences

- Zero env vars → full monolith boots.
- Worker-only deploy is feasible today by setting `hephaestus.runtime.worker.enabled=false`
  on the server pod — the Docker sandbox + agent NATS pull consumer drop out. The
  server JAR remains bit-identical.
- True server-only mode (HTTP API + sync NATS only) requires the reserved
  `server.enabled` flag to be wired in a follow-up; this epic establishes the
  property-key contract but defers the implementation.
- The split surfaces concrete boundary refactors (AgentNats publisher/consumer split,
  DockerSandbox worker gate, LlmProxy capability gate) — covered in commit messages,
  not duplicated here.
- No `RuntimeSmokeIT` lands in this epic. The role-isolation invariants are enforced
  at compile time by `RuntimeRoleBoundaryTest` (ArchUnit); end-to-end smoke per role
  lands with the deploy-split epic that has a real operational driver.

## Revisit trigger

A real operational need to scale `mentor` independently of `server`; or a third runtime role
becomes load-bearing (e.g., a dedicated webhook receiver pod for restart independence).

## Update — 2026-05-20 (issue #1110)

The revisit trigger has fired. `RuntimeRole.WEBHOOK_PROPERTY` is now wired (third runtime role:
`webhook`), and `RuntimeRole.SERVER_PROPERTY` — reserved by this ADR — is wired for the first
time. `ServerSchedulingConfig`, `NatsConsumerService`, and `WorkspaceStartupListener` are gated
by `SERVER_PROPERTY` so they do not duplicate-run on the dedicated `webhook-server` pod. See
**ADR 0008**.

## Update — 2026-07-20 (issue #1368)

The `hephaestus.sandbox.llm-proxy.enabled` capability flag referenced in the Decision section
above no longer exists — see **ADR 0006**'s 2026-07-20 amendment. The proxy's gate is now derived
from the same job-execution capability expression `AgentJobExecutor` wires on
(`hephaestus.agent.nats.enabled AND hephaestus.runtime.worker.enabled`), not a standalone
property.

## Update — 2026-07-21 (issue #1368)

The "agent NATS pull consumer" named in the Context section, and `hephaestus.agent.nats.enabled`
in the 2026-07-20 update above, no longer exist. The agent job queue moved off NATS JetStream onto
PostgreSQL — each worker replica polls `agent_job` and claims a batch with
`FOR UPDATE SKIP LOCKED` instead of pulling ids off a stream — see **ADR 0025**. The job-execution
capability expression `AgentJobExecutor` and the LLM proxy key off is now
`hephaestus.agent.enabled AND hephaestus.runtime.worker.enabled`: same shape, the left-hand
property renamed. This ADR's "server ↔ worker" boundary and the `hephaestus.runtime.worker.enabled`
gate it establishes are unaffected — only what feeds work to the worker changed. NATS remains
required for webhook ingest (ADR 0008) and SCM/Slack sync, which this change does not touch.
