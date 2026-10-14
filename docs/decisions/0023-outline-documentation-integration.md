# ADR 0023: Outline supplies authorized document evidence

**Status:** Accepted
**Date:** 2026-10-14
**Authors:** Hephaestus maintainers

## Context

Engineering practices can depend on a design document or architecture decision outside an SCM change.
The review needs the actual authorized document rather than a guessed summary.

## Decision

Outline is a documentation integration with selected collection scope.
Its adapter owns API access, mirror sync, exact team identity and document capture.
A document is a docs-domain item with its own version and author role.
Core review admission owns the result, not the provider adapter.

The source contract controls which collections and product uses are authorized.
Linked document evidence remains separate from asserted relationships.
A missing or unauthorized required document withholds the result instead of creating a claim about the work.

Outline has no on-work feedback writer and no repository scan capability.
It does not edit a document to imitate a feedback copy.
Eligible personal feedback can use practice-page or conversation destinations independently of the work's provider.
[ADR 0056](0056-one-practice-model-for-items-and-scans.md) owns that capability boundary.

## Consequences

Documents can supply real evidence without creating provider-specific result shapes.
Source-person consent includes the document author when that person's work is read.
Disconnect and erasure follow the normal source lifecycle and mounted-copy custody.

## Revisit trigger

Revisit when a shipped Outline capability needs provider writes or a collection scan.
