# ADR 0055: Practice scripts use one result contract

**Status:** Accepted
**Date:** 2026-10-14
**Authors:** Hephaestus maintainers

## Context

Some practices can decide from exact captured records.
Others need model judgment within the same evidence boundary.
Both must reach one admission path before they can support feedback.

## Decision

Each assigned practice runs one custom script or the built-in script.
A custom script exports definePracticeScript with meta and run(ctx).
The script returns one candidate result for its assigned item.
A null script uses the built-in script. An invalid script never falls back.

The host owns identity, one job credential, deadline, governed model requests and trusted read receipts.
Custom child processes have no credentials and use typed host IPC.
Model access follows declared slots and the developer's AI ceiling.
The frozen method retains all bound slots and epoch independently of job retention.

Candidate admission is atomic. One refused citation or child withholds the complete candidate.
Problems own severity. The server caches the exact worst severity on the result.
A Not met result can keep open questions beside its proven problems.
The [runner guide](../contributor/practice-script-runner.mdx) owns the strict version 3 contract.

[ADR 0056](0056-one-practice-model-for-items-and-scans.md) owns durable model relationships.

## Consequences

No earlier script form, output decoder or grace runner remains.
Operators upgrade roles, runtime images and clients together.
The migration retains old history and cancels incompatible queued work without replay.
A script failure creates no claim about the work.

## Revisit trigger

Revisit when a shipped script requires a capability that its domain kit and governed host cannot express.
