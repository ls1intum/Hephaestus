# ADR 0004: SQL-layer tenancy enforcement via WorkspaceStatementInspector

**Status:** Accepted
**Date:** 2026-05-20
**Authors:** Server foundations epic (#1097)

## Context

Cross-workspace data leakage is a security risk every Hephaestus deployment must
avoid. We had ArchUnit tests catching some categories at the repository layer, but
nothing catches a query that emits SQL against a workspace-scoped table without a
`workspace_id` predicate. The bug class is: someone writes a `@Query("SELECT ... FROM
pull_request")` and forgets the workspace filter — the test suite passes, the leak
ships.

Hibernate 6 ships native multi-tenancy support via `@TenantId` +
`CurrentTenantIdentifierResolver`. That's the modern discriminator-based approach,
auto-injects the predicate, and auto-fills the column on insert. But:

- It does NOT cover native queries, `@Modifying` deletes, or some projection shapes
- Multi-hop entities (CommitFileChange → Commit → Repository → Organization ←
  Workspace) need workspace_id denormalization onto every entity for `@TenantId` to
  attach the predicate cheaply — that's a 6–12 week migration epic of its own

A `StatementInspector` works at the SQL-emit boundary (catches everything, including
native queries) but needs a runtime escape hatch for legitimately-cross-workspace
queries (workspace listing, slug history, admin maintenance).

## Decision drivers

- Catch the leak class at PR time, not in production
- Don't block on a 6–12 week schema migration (`@TenantId` denormalization)
- Make `@WorkspaceAgnostic` annotation load-bearing (not just docs)
- Performance: hot-path SQL must stay fast (regex + cache)
- Single source of truth for which tables are scoped

## Considered options

1. **`StatementInspector` + AOP bypass + JPA-metamodel SSOT** — pragmatic; ships
   without schema migration; defense-in-depth.
2. **`@TenantId` native multi-tenancy** — modern, integrated; needs denormalization
   first.
3. **`@TenantId` + `StatementInspector` hybrid** — best long-term, but option 1 is
   the prerequisite (inspector exists; `@TenantId` adoption layers on later).
4. **Hibernate `@Filter`/`@FilterDef`** — legacy; per-repository activation; doesn't
   catch native queries; effectively superseded by `@TenantId`.

## Decision

Option 1 for this epic. Statement inspector + AOP-driven bypass on
`@WorkspaceAgnostic` + JPA-metamodel-derived `WorkspaceScopedTables` SSOT.
Performance pipeline: ThreadLocal bypass check → cache → regex fast path → JSqlParser
slow path. Enforcement mode is configurable
(`hephaestus.tenancy.enforcement = throw | log | off`); `throw` in test, `log` (with
Micrometer counter `tenancy.violation.total{table, mode}`) elsewhere.

Native `@TenantId` adoption is filed as a follow-up epic that lands after the
multi-hop denormalization migration.

## Consequences

- Test profile fails loudly on any workspace-scoped query that lacks the predicate.
- Production runs in `log` mode for the staging canary period; flip to `throw`
  after a calendar week of clean counter readings (tracked separately).
- The `@WorkspaceAgnostic` annotation now has runtime semantics: dropping it from a
  repository that issues cross-workspace queries causes `TenancyViolationException`
  in test. Annotation is no longer documentation-only.
- JSqlParser added as a dependency (~700 KB). The regex fast path catches 99%+ of
  statements without invoking the parser; the bounded Caffeine cache amortizes the
  slow-path cost.
- `WorkspaceScopedTables` derives the table set from the JPA metamodel at startup —
  new scoped entities get auto-protected; explicit `GLOBAL_TABLES` allowlist holds
  the 11 known exceptions with per-entry rationale.

## Revisit trigger

`@TenantId` denormalization epic completes; or the Micrometer counter shows zero
violations for a calendar week and we flip to `throw` in prod; or the regex fast
path produces a false-negative that ships a real leak (would mean the
fast-path-as-bypass was wrong and we need full parse-everything).
