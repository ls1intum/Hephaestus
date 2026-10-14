# ADR 0019: Workspace membership uses accounts

**Status:** Accepted
**Date:** 2026-10-14
**Authors:** Hephaestus maintainers

## Context

Authentication identifies a native account.
A provider actor is a source mirror and cannot be the workspace principal.
An account can belong to a workspace without an SCM identity.

## Decision

Membership joins workspace and account with the workspace role.
Roster sync creates UNCLAIMED accounts only for eligible humans.
Sign-in claims the exact existing identity instead of creating another person.
Linking can absorb an unclaimed record after verified identity ownership.
It cannot absorb another claimed account.

Overlapping memberships retain the highest already-granted role and existing scope selections.
Either active membership keeps the resulting membership active.
Disabled identities retain their authority state during roster sync.
Only an authenticated exact-identity flow can reactivate them.
A founder receives explicit membership. The first linked identity never gains implicit admin elevation.

A removed member retains history with leftAt.
Admin role assignment is the reverse action after owner checks.
Erasure removes dependent proof and feedback before membership.
The [workspace guide](../contributor/workspace-context.mdx) owns authorization details.
[ADR 0056](0056-one-practice-model-for-items-and-scans.md) owns the complete practice model.

## Consequences

One account can hold identity links and account-wide choices across workspaces.
SCM user mirrors retain provider authorship and activity facts.
The released-state migration proves claim, absorption, tenancy and erasure without synthetic people.

## Revisit trigger

Revisit when a shipped principal type needs workspace access without a human account.
