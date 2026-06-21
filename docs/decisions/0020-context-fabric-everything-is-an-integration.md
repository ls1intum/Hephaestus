# ADR 0020: Context Fabric — everything is an integration, only practice-detection and mentor are native

**Status:** Proposed
**Date:** 2026-06-12
**Authors:** Hephaestus maintainers
**Builds on:** [ADR 0015](0015-unified-integration-framework.md) (the integration framework and `Connection` aggregate), [ADR 0004](0004-sql-layer-tenancy-via-statement-inspector.md) (SQL-layer tenancy), [ADR 0014](0014-per-row-aes-gcm-aad-binding.md) (per-row AAD), [ADR 0007](0007-sandbox-spi-shape.md) (the agent sandbox / `ContentProvider` seam)

## Context

The practice-detection agent reviews a change from a materialised context directory
(`context/target/*`) built by the `ContentProvider` SPI (`agent.context`, ADR 0007).
A directory that holds only what the diff and the PR row carry (`metadata.json`,
`diff.patch`, `diff_summary.md`, `comments.json`) makes a large fraction of mentor
lessons **context-blind** misses rather than detection misses: the signal lives in
another artifact the agent never saw. Three sharp examples:

- A change that `Closes #N` could not be judged against #N's acceptance criteria,
  because the criteria live in the **issue body**, never in the diff.
- A branch cut from an in-flight feature branch (large ahead-range, multiple authors)
  was invisible, because that is a fact of the **branch graph**, not the patch.
- A "all tests pass" Definition-of-Done tick was treated as credible even in a repo
  that **has no test target at all**, because test-suite existence is a fact of the
  **repository tree**, not the diff.

The deeper reframe behind the fix: the SCM is not special. An issue body, a branch
graph, a test-target inventory, an Outline doc, a Slack thread — these are all the same
shape of thing: **external content, fetched on demand, projected into the agent's
context with provenance**. The only genuinely *native* capabilities Hephaestus owns are
**practice-detection** and **mentor**; everything that feeds them is an integration. We
call the substrate that makes any such content addressable, tenant-safe, and cacheable
the **Context Fabric**.

This ADR records the target architecture *and* the first, smallest slice of it that
ships on the existing seam.

## Considered options

1. Build the full Connector SPI plus CAS plus node/edge graph now. Rejected: pays the whole migration cost before the reframe has delivered one lesson.
2. Keep adding ad-hoc ContentProviders forever. Rejected: a non-SCM connector would have no seam; naming the Fabric now makes the Connector promotion a forcing function, not a rewrite.
3. Ship the slice on the existing seam, target staged-additive (chosen). Reframe proven for one PR, zero schema change.

## Decision register

The Context Fabric is defined by the following decisions. Most are **target state**
(staged follow-ups); the shipped slice is called out in §"Shipped slice".

1. **SQL is the source of truth; disk is a rebuildable cache.** Persisted rows in
   Postgres are authoritative. Everything on the agent's disk (`context/target/*`, the
   git clone, any future projected doc) is a **derived, rebuildable cache** — deletable
   at any time and reconstructable from SQL + the upstream connector. No agent-visible
   file is ever a system-of-record. This is the same posture ADR 0004 takes toward
   tenancy: the row is the truth, the projection is a view.
2. **Content-addressable store (CAS), not "bronze."** The on-disk cache is keyed by
   content hash (generalising the existing git-clone + content-hash derivation). We
   deliberately do **not** call it a "bronze layer." The Databricks medallion vocabulary
   implies bronze is *raw landed truth*; here SQL is truth and the CAS is a derived
   cache, so importing "bronze" would invert the trust direction. See Evidence §Iceberg/
   Databricks for why the rename is load-bearing, not cosmetic.
3. **One `Connector` SPI, a superset of `ContentProvider` (target state).** The target SPI is a single
   `Connector` interface that subsumes today's `ContentProvider`: `supports(request)`,
   `required()`, and a `contribute`/`project` step, plus capability declaration and a
   fetch step against the upstream. SCM becomes a *peer* connector of Slack/Outline, not
   a privileged root. Today's `ContentProvider` is the narrow, already-shipped face of
   that superset.
4. **Five PROV-O Kinds.** Projected content is typed by a small fixed vocabulary aligned
   to W3C PROV-O: **Entity** (a thing — issue, doc, commit), **Activity** (a process —
   a review, a sync run), **Agent** (a who — author, bot, service), **Bundle** (a
   provenance-scoped set), **Plan/Usage** (the recipe + the act of using it). Five Kinds,
   not an open-ended type zoo.
5. **`entity_node` / `entity_edge` with split confidence.** Cross-context links (PR ↔
   issue, doc ↔ MR, thread ↔ change) are modelled as a node/edge graph with **two
   separate confidences**: *match confidence* (how sure we are this is the right target)
   and *assertion confidence* (how sure we are the claimed relationship holds). Splitting
   them prevents a high-recall fuzzy match from masquerading as a high-precision
   assertion — the exact failure mode the linked-work-item provider guards against by
   never asserting an unmet criterion.
6. **Capability registry.** A connector declares which Kinds and which capabilities
   (feedback channel, finding channel, content fetch, link assertion) it supports; the
   builder gates work on the declaration rather than on `instanceof`. This is the
   generalisation of ADR 0015's manifest-gated SPI.
7. **Telescope manifest, not a cage.** Projected content is **hints + provenance, never
   verdicts and never full bodies**: lists are capped, bodies are excerpted, every item
   carries a clickable `url`. The manifest tells the agent what *exists* and where to look
   deeper; it never pre-renders a judgement. "Telescope, not cage" is the litmus the
   linked-work-item provider already follows (cap 8 items, 600-char excerpt, real
   `htmlUrl`).
8. **Audience-scoped privacy + a `consistencyToken` + a delivery leak-guard.** Each
   projected fact carries an **audience** (who may see it) and a **`consistencyToken`**
   (a read-time snapshot stamp, à la Zanzibar's "zookie"), so a reviewer-side fact can
   never be delivered into an author-facing surface, and a stale projection can never be
   silently mixed with a fresh one. The delivery path keeps the existing **leak-guard**
   (internal `context/target/*` paths and metadata-only practices are filtered out of
   author-visible diff notes unless explicitly allow-listed).
9. **Fail-CLOSED tenancy (target state).** The Fabric does not relax ADR 0004; it hardens it. Target
   state pairs a **THROW-mode statement inspector** with **Postgres Row-Level Security
   (RLS)** so a projection query that lacks a `workspace_id` predicate *fails the request*
   rather than falling open. (ADR 0004 ships `log` mode today with a `throw`-in-prod
   revisit trigger; the Fabric is a forcing function for that flip plus RLS as
   defence-in-depth.)
10. **`workspace_binding` de-spine.** Workspace-scoped configuration of *which* connectors
    are active and *how* they project becomes a `workspace_binding`-style row rather than
    columns on `Workspace` — the same de-spining ADR 0015 did to credentials, extended to
    projection config.

## Evidence triangulation

The shape above is not invented; three independent industry families converge on it.

- **Connector convergence — Port Ocean / Backstage / Airbyte.** Port's *Ocean*
  integration framework, Spotify's *Backstage* entity model, and *Airbyte*'s source
  connector SPI independently land on the same triad: a typed entity graph, a declarative
  connector that *projects* upstream content into a local model, and capability/manifest
  gating. Three teams solving "ingest many external systems into one model" arriving at
  one shape is strong evidence the `Connector` superset + capability registry is the
  right seam, not an over-fit. (Port Ocean docs; Backstage Software Catalog model;
  Airbyte protocol/connector spec.)
- **Audience + priority — MCP.** Anthropic's **Model Context Protocol** carries an
  explicit `audience` and `priority` annotation on resources/content. We adopt the same:
  a projected fact declares who it is for and how load-bearing it is, which is exactly the
  audience-scoping in decision §8. (MCP specification, resource annotations.)
- **Consistency token — Zanzibar.** Google's **Zanzibar** uses a "zookie" — an opaque
  snapshot token — so a permission check reads at a known-consistent point and a stale
  read cannot silently win. Our `consistencyToken` is the same device for projected
  content. (Zanzibar: Google's Consistent, Global Authorization System, USENIX ATC 2019.)
- **Lazy non-lossy projection — Iceberg.** Apache **Iceberg**'s lazy, non-lossy schema
  evolution (read old data through new schema without rewriting) is the model for
  conformance: project content into the Kind vocabulary lazily and without discarding the
  raw upstream, so a later, richer reader is not blocked by an early, lossy projection.
  (Iceberg table spec / schema evolution.)
- **Why we renamed "bronze" — Databricks medallion.** In the **Databricks medallion**
  architecture the *bronze* layer is the **raw landed source of truth**; silver/gold are
  the derived, cleaned views. Our trust direction is the opposite: **SQL is truth, disk is
  the derived cache.** Reusing "bronze" for our CAS would tell every future reader the
  on-disk hash store is authoritative — the precise inversion we must not ship. Hence
  decision §2 calls it a CAS. (Databricks medallion architecture guidance.)

## What this PR ships (the first slice — honest scope)

This PR does **not** build the `Connector` SPI, the CAS, the `entity_node`/`entity_edge`
graph, RLS, or `workspace_binding`. It ships the **smallest useful slice of the Fabric on
the seam that already exists** — the `ContentProvider` SPI from ADR 0007 — proving the
reframe end-to-end before paying for the migration:

- **Cross-context `ContentProvider`s** under `agent.context.providers`, each best-effort
  (`required() == false`: a missing repo/branch/issue logs and skips, never aborts the
  job), each telescope-not-cage (capped, excerpted, every item carries a real `url`):
  - `linked_work_items.json` — already shipped; resolves closing/branch/commit issue refs
    to the issue row with an excerpted body (the acceptance criteria).
  - `branch_graph.json` — `looksBranchedOffFeatureBranch` + `commitsAhead` +
    `distinctAuthorsInRange`, computed from the local clone via
    `GitRepositoryManager.walkCommits` / `GitDiffOperations.resolveDiffRange`.
  - `test_presence.json` — `repoHasTestTarget` from a bounded, contents-free
    `Files.walk` of the clone (path strings only, capped).
- **Consuming practices** that turn those files into formative feedback (this PR):
  - `honours-linked-issue-acceptance-criteria` (goal `review-ready-work`) — consumes
    `linked_work_items.json` + `diff.patch`; asks which of `#N`'s criteria are done vs
    deferred; **never asserts an AC is unmet** from code it cannot verify.
  - `branches-from-the-integration-branch` (goal `delivery-and-version-control-discipline`)
    — consumes `branch_graph.json`; nudges the branching habit, MINOR-only, heuristic.
  - `keeps-the-test-suite-honest` — **revised** to read `test_presence.json`: when
    `repoHasTestTarget` is false, a "tests pass" DoD claim is vacuous; a calibrated,
    mostly-MINOR team-wide standing nudge, not a per-MR blocker.
- **Delivery leak-guard wiring** (the integrator edits): the three new files are added to
  `PullRequestReviewHandler.ALLOWED_INTERNAL_CONTEXT_PATHS` and the two new metadata-level
  slugs to `METADATA_LEVEL_PRACTICES`, so a finding grounded in a cross-context file
  survives `filterByDiffScope` instead of being dropped as out-of-diff-scope.

The audience tag, `consistencyToken`, split-confidence edges, RLS, and the
`Connector` superset are **not** in the original slice. They are the staged follow-ups below.

## CAS and final filesystem layout

The on-disk substrate is not SCM-special. The following decisions are **realized**
(additive, no schema change), so the layout is in its final, integration-namespaced shape and a
future connector slots in with no restructuring:

- **§1 SQL-truth / disk-is-cache, §2 CAS (no "bronze").** `FabricLayout`
  (`integration.core.fabric`) is the single source of every cache path under one configurable
  `hephaestus.fabric.root` (defaults to the old `git.storage-path`):
  `bulk/{connectorId}/{externalId}` (the git clone is `bulk/scm/{repoId}`), `cas/{ab}/{rest}`
  (`ContentAddressedStore` — sha-256, two-char fan-out, build-on-miss, atomic writes, striped
  locks, mark-and-sweep GC), `derived/`, and `jobs/{jobId}/` for replay. `GitRepositoryManager`
  now routes its clone path through `FabricLayout.bulkArtifact`; a clone is a rebuildable cache so
  the move needs no data migration.
- **Sandbox view = the telescope (§7).** The repo mounts at `/workspace/blobs/scm/repo` (one
  namespace among future `blobs/slack`, `blobs/outline`) with a back-compat `repo → blobs/scm/repo`
  symlink (`SandboxSpec.symlinks` + `SandboxWorkspaceManager.injectSymlinks`), so the agent surface
  and the `repo/` strippers are unchanged. `context/target/manifest.json` (`ContextManifestBuilder`)
  is the integration-agnostic index: one entry per projected file with `{path, connector, bytes,
  sha256}`. Every projected blob is stored in the CAS, so identical context deduplicates across jobs and
  each entry carries a content-addressed provenance hash. (Enforcing it — rejecting an agent citation
  whose sha is absent — is a follow-up; today the sha is recorded, not yet validated.)
- **Toward the `Connector` superset (§3).** `ContentProvider` gained `connectorId()` (additive
  default `"scm"`) so each file's producing integration is recorded in the manifest. The full
  `Connector` SPI rename + capability registry remain follow-ups.
- **Cache GC.** `FabricGarbageCollector` (`@Scheduled`, mirrors the existing retention sweepers)
  prunes expired `jobs/` dirs then mark-and-sweeps unreferenced CAS blobs.

**Still open** (unchanged from the staged list): `entity_node`/`entity_edge` + split confidence,
the five-Kind PROV-O vocabulary, audience + `consistencyToken`, fail-CLOSED tenancy (THROW + RLS),
`workspace_binding` de-spine, and the `Connector` SPI rename itself.

## Consequences

Positive:

- The agent stops being context-blind on the highest-payoff context-blind misses, on a
  seam that already exists — zero schema migration, zero new SPI.
- Each provider is best-effort and each practice is NA-safe: a workspace with git
  disabled, no linked issue, or no test target simply gets silence, never a spurious
  finding or a failed job.
- The reframe is validated cheaply: if cross-context providers + consuming practices pay
  off here, the `Connector`/CAS/RLS migration is justified; if not, we have spent one PR,
  not an epic.

Neutral / negative:

- The `ContentProvider` seam was not designed as the full `Connector` superset; these
  providers fit it, but the richer fetch/Kind/edge surface is absent and the providers
  encode their own ad-hoc projection. That debt is intentional and named here.
- `test_presence.json` and `branch_graph.json` are **heuristics**. `repoHasTestTarget`
  false can be a false negative for unconventional layouts; `looksBranchedOffFeatureBranch`
  can be a false positive for a long-lived solo branch that merged the integration branch
  back in. Both practices are deliberately capped (MINOR / standing-nudge) to keep the
  cost of a false positive low — the calibration is the mitigation.
- Tenancy is still ADR 0004 `log`-mode shape for these read paths; the fail-CLOSED
  THROW+RLS posture is target state, not shipped. Until then the controller layer remains
  the security boundary for cross-context reads, exactly as ADR 0004 documents.

## Staged follow-ups (the rest of the Fabric)

Additive, in dependency order: (1) `Connector` SPI superset of `ContentProvider` with a
capability registry; (2) CAS for the on-disk cache (generalising the git clone); (3)
`entity_node`/`entity_edge` with split match/assertion confidence; (4) the five-Kind
PROV-O vocabulary + lazy non-lossy conformance; (5) audience + `consistencyToken` on every
projected fact; (6) fail-CLOSED tenancy (flip ADR 0004 to THROW + add Postgres RLS); (7)
`workspace_binding` de-spine of projection config. Each lands as its own PR with its own
ADR delta; none blocks the slice this PR ships.

## Revisit trigger

- A second non-SCM connector (Outline doc, Slack thread) needs to project content — the
  forcing function to promote `ContentProvider` to the `Connector` superset (§3) rather
  than adding a fourth ad-hoc provider.
- A cross-context link needs to be *asserted* (not just hinted) into a finding — the
  forcing function for `entity_node`/`entity_edge` with split confidence (§5).
- A projected fact needs to be reviewer-scoped (visible to a maintainer but not the
  author) — the forcing function for the audience tag (§8); until then the leak-guard
  allowlist is the coarse stand-in.
- ADR 0004's counter reads clean for a calendar week and prod flips to `throw` — fold RLS
  in at that point (§9).

## Sources

Port Ocean integration framework docs; Spotify Backstage Software Catalog entity model;
Airbyte connector/protocol specification; Anthropic Model Context Protocol specification
(resource `audience`/`priority` annotations); Zanzibar: Google's Consistent, Global
Authorization System (USENIX ATC 2019, the "zookie" snapshot token); Apache Iceberg table
spec and schema-evolution guidance; Databricks medallion (bronze/silver/gold) architecture
guidance; W3C PROV-O provenance ontology. Internal: ADR 0015 (integration framework),
ADR 0004 (SQL-layer tenancy), ADR 0007 (sandbox/ContentProvider seam), ADR 0014 (per-row
AAD), and internal mentor-quality evaluation.