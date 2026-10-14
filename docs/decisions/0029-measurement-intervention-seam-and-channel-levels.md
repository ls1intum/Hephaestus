# ADR 0029: Compose feedback after result admission

**Status:** Superseded by [0056](0056-one-practice-model-for-items-and-scans.md)
**Date:** 2026-10-14
**Authors:** Hephaestus maintainers

## Context

Composition follows result admission and uses only authorized support. A feedback destination does not establish a scientific content level.
The 1.0 cutover removes the earlier model inventory from this record.

## Decision

[ADR 0056](0056-one-practice-model-for-items-and-scans.md) owns the current model.
The [data model](../contributor/practice-feedback-schema.md) owns durable relationships.
The [evaluation contract](../contributor/evaluation-provenance.md) owns historical interpretation limits.
The [standard mapping](../contributor/practice-review-glossary.mdx#standard-terms) owns research correspondence.

## Consequences

Use the current model without compatibility readers.
Git history retains the earlier decision text.
