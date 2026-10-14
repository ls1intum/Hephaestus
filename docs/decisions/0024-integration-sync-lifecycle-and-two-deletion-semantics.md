# ADR 0024: Mirror tombstones and source erasure are separate

**Status:** Accepted
**Date:** 2026-10-14
**Authors:** Hephaestus maintainers

## Context

A partial source listing cannot prove deletion.
Provider deletion, a temporary visibility gap and a workspace disconnect have different consequences.

## Decision

Adapters record soft tombstones when complete authorized reads establish that source work is no longer visible.
A later authoritative read can restore that work.
Incomplete or failed listing cannot replace a complete access grant or inventory.
New automatic review events retain their identity while a reversible visibility hold is active.
A manually requested review of gone work receives a precise refusal.

Disconnect and workspace purge use the provider's eraser.
They remove mirrored content and derived proof through its workspace owner.
They also retain physical-cleanup custody until worker storage confirms removal.
Source-person erasure reaches copied proof in another person's review and developerless scan evidence.
It does not merely remove verdicts addressed to that person.

Item event time, version and discovery facts are write-once.
Retry decisions can change without manufacturing a new source occurrence.
Shared SCM mirrors still produce workspace-specific item rows and explicitly scoped proof updates.
Slack and Outline retain their own provider and team authorization.

The [sync guide](../contributor/sync-lifecycle.md) owns provider lifecycle behavior.
The [data map](../admin/dsms/personal-data-map.md) owns erasure and retention.
[ADR 0056](0056-one-practice-model-for-items-and-scans.md) owns review and feedback history.

## Consequences

A temporary gap does not erase history or invent a failure outcome.
A source disconnect cannot leave a delivery writer recreating erased content.
Restoration needs authoritative source access, not a guessed current mirror row.

## Revisit trigger

Revisit when a provider exposes a new deletion mechanism that the adapter cannot distinguish from temporary invisibility.
