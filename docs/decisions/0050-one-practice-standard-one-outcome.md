# ADR 0050: One practice standard, one outcome

**Status:** Accepted
**Date:** 2026-10-14
**Authors:** Hephaestus maintainers

## Context

A developer needs a verdict about the practice standard within captured evidence.
A positive behavior alone does not prove the complete standard.
Missing evidence, excluded applicability and unresolved meaning are different facts.

## Decision

Use Met, Not met, Does not apply and Could not determine.
The [outcome contract](../contributor/practice-feedback-language.md#result-outcomes) owns their exact evidence requirements.
A result concerns one practice, item version and optional developer.
A scan has many file results, not one verdict for the repository.
Failed required capture creates no result.

Historical empirical records retain original criteria and evidence qualification.
A name conversion or recomputed digest does not establish scientific equivalence.
[ADR 0056](0056-one-practice-model-for-items-and-scans.md) owns the complete consolidated model.

## Consequences

Persistence, scripts, HTTP and UI use the same four outcomes.
Feedback state and result marks remain separate from the outcome.
Research must assess factual accuracy independently of structural admission.

## Revisit trigger

Revisit when independently reviewed cases establish an evidence distinction that these outcomes cannot express.
