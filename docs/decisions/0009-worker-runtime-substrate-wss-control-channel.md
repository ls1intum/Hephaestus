# ADR 0009: Worker runtime substrate + WSS-over-443 control channel

**Status:** Accepted (amended 2026-07-21 — drain requeues instead of cancelling, #1368 fix wave)
**Date:** 2026-05-21

> **Amendment (2026-07-21):** `AgentJobExecutor#cancelInFlight` (referenced in the Drain row below)
> originally wrote a terminal `CANCELLED` for every in-flight job on drain, contradicting the
> operator docs (`docs/admin/runtime-roles.mdx`), which always described drain as jobs "returning to
> the queue... bounded by `AGENT_MAX_RETRIES`". It now attempts a worker-fenced requeue
> (`RUNNING → QUEUED`, retry-capped — the same CAS `AgentJobZombieSweeper` uses for orphan recovery)
> first, for both `DRAIN_GRACEFUL` and the `timeout=0` `DRAIN_IMMEDIATE` case; only a job that has
> already exhausted its retry budget, or lost the fence to a concurrent user-cancel, falls back to a
> worker-fenced terminal cancel. `agent_job.cancellation_reason` is still populated on that fallback
> path (unchanged: `DRAIN_GRACEFUL` / `DRAIN_IMMEDIATE` / `USER` / `TIMEOUT`).

## Context

BYO / self-hosted workers behind MITM proxies are on the roadmap. NATS subjects don't traverse
corporate firewalls that block non-443 traffic, and gRPC trailers don't survive proxy interception
either. The existing `runtime.worker.enabled` gate toggles a sandbox + a NATS consumer but has no
capacity reporting, no graceful drain, and no control-channel health — the dispatcher (#1100) has
no signal to schedule against and SIGTERM during a deploy drops in-flight jobs.

ADR 0005 listed worker substrate as its explicit revisit trigger; ADR 0008 introduced the
overlay-profile + property-gated pattern this ADR extends to a third role.

## Decision

WSS over TLS-443 with sealed-record JSON frames, gated by `runtime.worker.enabled=true`
(`matchIfMissing=true`, monolith stays default-on).

| Concern | Mechanism |
| --- | --- |
| Wire | JDK `java.net.http.WebSocket` client → Spring `WebSocketHandler` hub at `/api/workers/connect`. JSON frames; 256 KiB cap. |
| Protocol | Sealed `WorkerControlFrame` permits `WorkerHello`/`WorkerWelcome`/`Heartbeat`/`CapacityReport`/`ForceReconnect` + mentor session quartet. Idempotent — no per-frame `seq`/`ack` replay. |
| Auth | Registration-token → 1h RS256 JWT exchange at `POST /api/workers/exchange`. Issuer + audience enforced; `nbf` set; explicit `RS256` allowlist; per-`kid` JWK ring for hot rotation. Postgres+Caffeine denylist for revocation. Per-IP exchange throttle. |
| Capacity | `WorkerCapacityState` + 20s `WorkerCapacityReporter` on a dedicated `TaskScheduler`. |
| Drain | `WorkerDrainCoordinator` (`SmartLifecycle`, phase `< WebServerGracefulShutdownLifecycle.SMART_LIFECYCLE_PHASE`). Sequence: publish `REFUSING_TRAFFIC` → emit final draining heartbeat + zero-capacity → close mentor sessions → `AgentJobExecutor.awaitInFlight(timeout)` then `cancelInFlight(reason)`. One Duration knob (`hephaestus.worker.drain.timeout`, default 5m). |
| Hub shutdown | `WorkerSessionRegistry` is `SmartLifecycle` at the same phase: emits `ForceReconnect` + closes with `GOING_AWAY` before Tomcat stops accepting traffic. |
| Backpressure | Per-session `ConcurrentWebSocketSessionDecorator` (`sendBufferSize`, `sendTimeLimit`). Binary frames refused with WS code 1003. |
| Audit | New `agent_job.cancellation_reason` column for drain-initiated cancels (`DRAIN_GRACEFUL` / `DRAIN_IMMEDIATE` / `USER` / `TIMEOUT`). No separate phase-log table. |

`AgentJobExecutor` exposes three orthogonal methods — `stopAcceptingNewJobs()` /
`awaitInFlight(Duration)` / `cancelInFlight(reason)` — composed by the drain coordinator. The
previous `@PreDestroy` never awaited the sandbox executor (latent bug); this fixes it.

## Considered options

| | Reason rejected |
| --- | --- |
| NATS-only control | Doesn't traverse MITM proxies. `WorkerControlPublisher` is a one-method seam so the choice is reversible. |
| gRPC over HTTP/2 | Trailers are routinely stripped by enterprise proxies. |
| `FrameRouter` + `FrameHandler<T>` SPI | ~300 LOC of dispatcher infrastructure for what a Java 21 sealed `switch` does in five lines with compiler exhaustiveness. |
| Per-frame `seq`/`ack_seq` replay | YAGNI: every frame in scope is idempotent. ~150 LOC saved. |
| Three named drain modes | Operator-hostile vs one `Duration`. `timeout=0` is the immediate-cancel degenerate. |

## Consequences

- A worker pod boots without Postgres-related env if its profile excludes JPA — but the current
  worker overlay still loads JPA because the executor reads job rows. Removing that dependency is
  a follow-up.
- JWT signing keys default to an ephemeral keypair when unset; production must set
  `hephaestus.worker.hub.token.signing-key` or the `keys[]` ring. Logged at WARN if ephemeral.
- The registration token is fleet-wide until per-worker minting lands; rate-limited by source-IP
  to bound brute-force noise.
- The mentor session bridge sits on the same WSS connection. If the hub-side bridge module is
  not deployed (`HubSessionInbox` absent), worker session frames are silently dropped at the
  hub — acceptable for cold-start smoke tests, deliberate for monolith deployments without the
  bridge.

## Out of scope (deferred)

- Dispatcher claim logic + admin UI (#1100).
- JWK Set rotation via JWKS endpoint (single static keypair via Spring config; rotate via
  redeploy or the `keys[]` ring).
- Adaptive concurrency rules — static config is the contract.
- Worker-side observability: `worker_*` Prometheus catalog ships, no golden-file pinning yet.
