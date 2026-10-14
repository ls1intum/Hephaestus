# ADR 0020: Authorized source context has one boundary

**Status:** Accepted
**Date:** 2026-10-14
**Authors:** Hephaestus maintainers

## Context

A review can need an issue, repository tree, chat thread or linked document.
The review must know what was captured, its identity and who permits that use.
A file in a sandbox is not an independent source of truth.

## Decision

SQL and provider-owned immutable identities retain authoritative facts.
Worker folders are disposable projections of authorized capture.
Core uses neutral source, item and citation-verifier ports.
Domain modules define source meaning, version and claim basis.
Provider adapters collect and present their authorized material.

Capture records availability, content, completeness and collection outcome separately.
Required capture failure never becomes a verdict about the developer.
Consent applies to every person whose work the review reads.
Deep links and bounded descriptions aid discovery without pretending to prove a conclusion.

[ADR 0039](0039-git-and-postgresql-own-evidence.md) owns source truth.
[ADR 0041](0041-compose-1x-kubernetes-2.md) owns actual worker storage and erasure acknowledgement.
[ADR 0056](0056-one-practice-model-for-items-and-scans.md) owns the current core, domain and provider boundary.

## Consequences

No generic provenance graph or separate content store is required by this model.
A provider can add a domain source without placing provider persistence in core.
The source contract remains the authority for allowed capture and citations.

## Revisit trigger

Revisit when a shipped source cannot express its authorization or identity through the existing ports.
