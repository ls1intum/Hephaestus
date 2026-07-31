# ADR 0021: Separate findings from delivered feedback

**Status:** Accepted
**Date:** 2026-06-14
**Authors:** Felix T.J. Dietrich
**Builds on:** [ADR 0020](0020-context-fabric-everything-is-an-integration.md),
[ADR 0007](0007-sandbox-spi-shape.md)
**Partially superseded by:**
[ADR 0022](0022-observation-presence-assessment-and-schema-cleanup.md)

## Context

The original review pipeline rendered transient parser output directly into a provider comment. It
did not preserve a durable record of what was prepared, delivered, replaced, or withheld. This made
delivery and reception impossible to study independently from detection.

The design investigation in this ADR proposed a broad agent-authored feedback model. Subsequent
implementation and [ADR 0022](0022-observation-presence-assessment-and-schema-cleanup.md) narrowed
that proposal.

## Outcome

The following decisions survived:

- A **finding** is audience-neutral evidence about one practice and artifact.
- **Feedback** is a separate, durable ledger of messages derived from findings.
- A message may bind several findings, and a finding may contribute to several messages.
- Delivery state and suppression reason belong to the message.
- Provider placements are separate rows because one message can produce several posted notes.
- Subject and recipient are distinct identities even when they refer to the same person.

The following proposals did not ship:

- agent-authored feedback through a `report_feedback` tool;
- policy-floor or fallback feedback authors;
- reviewer/facilitator delivery axes;
- a separate reaction-event log;
- speculative placement fields for content that was never posted.

The implemented pipeline persists agent-authored findings through `report_finding`.
`DeliveryComposer` creates feedback server-side, and the delivery path records the resulting ledger
state and any actual placements.

## Consequences

Detection quality can be evaluated from findings without treating every finding as user-facing.
Delivery and suppression can be inspected without reconstructing provider comments. New delivery
channels reuse the ledger rather than changing finding identity.

This ADR is historical rationale, not a field or enum inventory. Current contracts live in:

- [Practice review data model](../contributor/practice-feedback-schema.md);
- [Evaluation provenance](../contributor/evaluation-provenance.md);
- the [generated database schema](../contributor/database-schema.mdx);
- the Java domain model and generated OpenAPI specification.
