# ADR 0005: Two-role runtime topology via `@ConditionalOnProperty`

**Status:** Accepted
**Date:** 2026-05-20
**Authors:** Server foundations epic (#1097)

## Context

The Hephaestus server today is a single JAR running everything: HTTP API, sync NATS
consumer, mentor SSE, scheduled tasks, agent NATS pull consumer, Docker sandbox
runtime. As the agent-job workload grows, we want the option to split the worker out
to its own pod (or pods, on separate nodes) without a refactor — just a deploy-config
change.

The TypeScript `webhook-ingest` process is already a separate deployable. The Java
server-side split is what's missing.

Earlier draft plans considered 5 runtime roles (`api`/`ingest`/`worker`/`mentor`/`scheduler`).
Pressure-testing surfaced that this was over-engineering: mentor lives in the same
JVM as ingest today (in-process events between them), scheduler is single-replica
(no leader election needed yet), `api`/`ingest` are co-located in the same Spring
context anyway. The two boundaries that matter operationally are:

1. **server ↔ worker** (resource isolation for the Docker sandbox runtime)
2. **server ↔ webhook-ingest** (restart independence — webhook-ingest already separate)

## Decision drivers

- Single JAR ships as both server and worker (deploy-config selects the role)
- DX invariant: zero env vars → full monolith boots
- Consolidate at the composition layer, not bean-by-bean (`@Configuration` class
  per role, not 50+ scattered `@ConditionalOnProperty`)
- The codebase already has 50+ `@ConditionalOnProperty` uses; `@Profile` is reserved
  for environments

## Considered options

1. **Two roles (`server`, `worker`) gated by `@ConditionalOnProperty`** — pragmatic, matches operational reality, single JAR.
2. **Five roles** (`api`, `ingest`, `worker`, `mentor`, `scheduler`) — speculative; no current operational pressure to split these.
3. **`@Profile`-based gating** — Spring 2025 consensus moved against `@Profile` for runtime topology; it's reserved for environment names.
4. **Multi-module Maven** (shared lib + N thin executable submodules) — premature for current scale; doubles the build complexity for no current win.

## Decision

Option 1. Two runtime roles defined by string constants in
`core/runtime/RuntimeRole.java`:

- `hephaestus.runtime.server.enabled` (default true) — HTTP API, sync NATS consumer,
  mentor SSE, scheduled tasks, agent dispatch chain
- `hephaestus.runtime.worker.enabled` (default true) — agent NATS pull consumer,
  Docker sandbox runtime, reconcilers, zombie sweepers
- Capability flag (not a role): `hephaestus.sandbox.llm-proxy.enabled` — see ADR 0006

Each gate sits at the `@Configuration` class level (`ServerRoleConfiguration`,
`WorkerRoleConfiguration`) with `matchIfMissing=true`. `RuntimeRoleBoundaryTest`
(ArchUnit) enforces `matchIfMissing=true` on every gate AND that `@Profile` values
stay in the env vocabulary (no `@Profile("worker")` smuggling).

`webhook-ingest` stays TypeScript for this epic. A future Java port (Phase 3) adds a
third role to the same JAR — when the operational win (one less language runtime,
one less Dockerfile) is concretely measured.

## Consequences

- A fresh JAR with no env vars boots the full monolith (DX invariant).
- A future server-only deploy sets `hephaestus.runtime.worker.enabled=false`;
  worker-only deploys set `hephaestus.runtime.server.enabled=false`. JAR
  bit-identical.
- ArchUnit catches role-gate rot at PR time. Three concrete boundary fixes shipped
  in this epic so the split is "flip flags" rather than "refactor":
  - `AgentNatsConfiguration` split into `AgentNatsConnectionConfig` (always-on
    publishers) + `AgentNatsConsumerConfig` (worker-only)
  - `DockerSandboxConfiguration` gated by `WORKER_PROPERTY` (sandbox runtime drops
    off server-only pods)
  - `LlmProxyController` + `LlmProxySecurityConfig` gated by the capability flag
    (no unconditional `@RestController` serving `/internal/llm/**`)
- One smoke test variant per role, not a full CI matrix (the JAR is bit-identical
  across role configs; running `mvn verify` 3× would triple CI for no information).
  RuntimeSmokeIT for actually booting each role is filed as a follow-up.
- `AgentJobService.cancel()` documents eventual-consistency semantics for the
  split case; replacing the inline `SandboxManager.cancel()` call with a NATS event
  is a follow-up.

## Revisit trigger

A real operational need to scale `ingest` independently (e.g., webhook bursts that
saturate single-server-pod capacity); or the BYO-runner epic introduces a third
role (probably `webhook-ingest` in Java) that lands a new `@Configuration` marker.
