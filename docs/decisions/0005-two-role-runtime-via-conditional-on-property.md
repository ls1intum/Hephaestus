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

1. **server ↔ worker** — Docker sandbox runtime resource isolation
2. **server ↔ webhook-ingest** — restart independence (webhook-ingest already separate)

A draft considered five runtime roles (`api`/`ingest`/`worker`/`mentor`/`scheduler`); the
pressure-test concluded mentor + ingest live in the same JVM as today, and the
scheduler can stay single-replica. Two roles match the actual operational pressure.

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

- `hephaestus.runtime.server.enabled` (default true)
- `hephaestus.runtime.worker.enabled` (default true)
- `hephaestus.sandbox.llm-proxy.enabled` — capability flag (not a role), see ADR 0006

Every `hephaestus.runtime.*` gate uses `matchIfMissing=true`, enforced by
`RuntimeRoleBoundaryTest`. `webhook-ingest` stays TypeScript for this epic.

## Consequences

- Zero env vars → full monolith boots.
- Future server-only / worker-only deploys flip flags; JAR stays bit-identical.
- The split surfaces concrete boundary refactors (AgentNats publisher/consumer split,
  DockerSandbox worker gate, LlmProxy capability gate) — covered in commit messages,
  not duplicated here.
- The single role-smoke test variant is run per role; no full `mvn verify` matrix.

## Revisit trigger

A real operational need to scale `ingest`/`mentor` independently of `server`; or a third
runtime role becomes load-bearing (likely Java `webhook-ingest`).
