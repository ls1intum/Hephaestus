# ADR 0028: Source-synced practice catalog

**Status:** Accepted
**Date:** 2026-10-14
**Authors:** Hephaestus maintainers

## Context

Bundled practices can change while a workspace keeps local choices.
Instance curation and workspace customization need separate authority.
One live definition must not have two independently mutable copies.

## Decision

Catalog entries retain definition, base, order and exclusion state.
Workspace adoption creates an immutable practice version and retains its exact catalog base.
A proposal compares current, base and offered fields.
Accepting fields creates a new version. Declining advances the base without deleting local text.

Reset copies the current catalog definition.
Include and exclude control adoption, not old workspace history.

Practice versions own authored definitions.
Name and review digest are database-generated read-only projections.
New source and script contracts append active versions without rewriting pinned history.
The [catalog guide](../contributor/practice-catalogue.md) owns update mechanics.

## Consequences

Both admin consoles use the same definition shape and explicit concurrency checks.
No runtime provenance repair or duplicate definition projection trigger remains.

## Revisit trigger

Revisit when a shipped catalog source cannot supply an exact definition and base for comparison.
