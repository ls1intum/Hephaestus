# ADR 0001: Flat top-level layout for Java + TypeScript deployables

**Status:** Accepted
**Date:** 2026-05-20
**Authors:** Server foundations epic (#1097)

## Context

The repo's server-side code lived under `server/application-server/` while
`server/webhook-ingest/` (TypeScript) sat beside it. The nested `server/application-server/`
was awkward in every developer interaction: search results, CI workflow filters
(`server/application-server/**`), Dockerfile contexts, IDE workspace definitions,
`cd server/application-server` in scripts.

With epic #1097 formalizing the two-runtime-role split (`server` + `worker` from one
JAR), the redundant nesting became indefensible. We also wanted to stop fighting the
weird path before the rename merge bills compound on top of every concurrent feature
branch.

## Decision drivers

- DX: every command + every search result includes the path
- Operational clarity: webhook-ingest is a sibling deployable, not a sub-feature of server
- Future-proof: a third top-level service (e.g. Java webhook-ingest in Phase 3) slots in as a sibling

## Considered options

1. **Flat top-level** — `server/`, `webhook-ingest/` at repo root (alongside `webapp/`).
   - ✓ Concise paths, sibling deployables visibly equal
   - ✗ "server" is a generic name; conversations need disambiguation
2. **`server/server/`** — keep nested with the server module re-named to match the parent.
   - ✗ Stutter in every path reference; `cd server/server` is a typo trap; `COPY server/server/target/*.jar` reads like a generated test fixture
3. **`services/server/` + `services/webhook-ingest/`** — Nx/TurboRepo-style parent grouping.
   - ✓ Scales cleanly when 5+ deployables exist
   - ✗ Over-organization at 2 deployables; introduces a layer without buying anything today

## Decision

Adopt option 1 (flat top-level). `server/application-server/` → `server/`;
`server/webhook-ingest/` → `webhook-ingest/`. Both sit at repo root.

## Consequences

- All CI workflow filters, Dockerfile contexts, scripts, IDE configs needed one-shot updates (~80 files; mechanical, no logic change).
- Docker image names, Compose service names, Traefik labels, and the
  `generate:api:application-server` pnpm script are intentionally NOT renamed in this epic — they're deploy-side concerns with a rollback story and live in a follow-up issue.
- A third top-level Java deployable (Phase 3: webhook-ingest ported to Java; see ADR 0005) drops in as `webhook-ingest-server/` or similar.

## Revisit trigger

The repo grows to 5+ top-level deployables, or someone proposes a non-trivial
`services/` reorganization with concrete scaling justification.

## Update — 2026-05-20 (issue #1110)

`webhook-ingest/` has been removed from the top-level layout. Webhook reception is now part of
the Java `server/` artifact (`gitprovider.webhook` package) and deployed as a separate
`webhook-server` container from the same image (Spring profile `webhook`). The top-level layout
is now: `server/`, `webapp/`, `docs/`. See **ADR 0008**.
