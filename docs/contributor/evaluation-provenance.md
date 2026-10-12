---
title: Evaluation provenance
description: Trace a persisted practice result to its review inputs, configuration, and delivery evidence.
---

# Evaluation provenance

This page describes the provenance available for automated practice reviews. The Java model and
[Liquibase changelogs](https://github.com/hephaestus-build/Hephaestus/tree/main/server/application/src/main/resources/db/changelog)
own the persisted contract.
The [generated database schema](./database-schema.mdx) shows its structure.

## Review provenance

Each automated practice-review invocation has an `agent_job`. Provenance is captured when it becomes
stable:

| Dimension | Stored in | Captured at |
| --- | --- | --- |
| Behaviour configuration | `agent_job.config_snapshot` | submission and claim |
| Prompt scaffolding | `agent_job.prompt_digest` | preparation |
| Injected files | `agent_job.inputs_digest` | preparation |
| Repository revision, where applicable | job metadata and the evidence manifest | submission and preparation |
| Admitted practice criteria | evidence snapshot and `practice_result.practice_revision_id` | preparation and persistence |
| Practice program, attempt, and captured revision | `practice_result.provenance` | admission |
| Model identity, usage, and cost | `agent_job` and `llm_usage_event` | completion |

Use the complete versioned configuration snapshot as the behaviour-configuration identity. A selected
tuple of model and endpoint fields is not equivalent to the snapshot.

`prompt_digest` identifies the shipped prompt scaffolding. `inputs_digest` identifies the final map of
files injected by the executor, excluding repository mounts and runtime-created files. It is
path-order-independent and elides occurrences of the job UUID. Equal digests therefore do not imply
byte-identical sandbox workspaces or semantically equivalent evidence.

The evidence snapshot records the exact admitted practice revision. Persistence carries that identity
into `practice_result.practice_revision_id`.
The system does not reconstruct it from a timestamp.

Admission also computes `practice_result.provenance` from data that the server pinned.
It records the job, the attempt, the practice revision, and the captured head revision.
It records the program kind, `builtin` or `custom`, and the SHA-256 digest of the practice script.
The digest comes from the script that the practice revision holds, not from the runner's report.
A result recorded before the practice program contract has no provenance, and its coverage is unknown.

Comparisons must be stratified by the complete behaviour configuration, prompt digest, input or case
cohort, and practice revision. Equality in one dimension does not make runs comparable in the others.

## Delivery evidence

Every composed feedback unit that reaches the delivery layer has a `feedback` row. A feedback state is a
delivery-policy outcome, not proof of an external placement or of human exposure. Prove placement from
`feedback_placement` or dispatch evidence. A placement still does not prove that its recipient read or
acted on the feedback.

The Java
[`FeedbackDeliveryState`](https://github.com/hephaestus-build/Hephaestus/blob/main/server/application/src/main/java/de/tum/cit/aet/hephaestus/practices/feedback/FeedbackDeliveryState.java)
and
[`FeedbackSuppressionReason`](https://github.com/hephaestus-build/Hephaestus/blob/main/server/application/src/main/java/de/tum/cit/aet/hephaestus/practices/feedback/FeedbackSuppressionReason.java)
types define delivery outcomes and suppression reasons.

## Evaluation joins

- **Result to review:** join `practice_result.agent_job_id` to `agent_job` for configuration, digests,
  repository metadata, and usage. Join `practice_result.practice_revision_id` to the admitted criteria.
- **Result to observations:** join `practice_observation.practice_result_id`. Only a `NOT_MET` result has
  observations.
- **Result to feedback:** join through `feedback_evidence`. Absence of a link means no feedback
  was composed from that result.
  It says nothing about why.
- **Feedback to placement:** join `feedback_placement` and inspect its channel reference. Feedback state
  alone is insufficient evidence of placement.
- **Response to result:** join `reaction.feedback_id` through `feedback_evidence`. The latest
  append-only reaction records the supplied response fields.
  Missing telemetry is unknown, not a negative response.

## Interpretation limits

- Digests identify materialized bytes under their stated coverage.
  They do not establish semantic equivalence or cover every sandbox-visible input.
- Persisted results exclude candidate results that admission withheld or refused. Invalid-output and failure-rate
  analysis requires a durable attempt and transition record rather than logs or result rows alone.
- Delivery and placement evidence does not establish that feedback was read or changed behaviour.

## Approval and shown conversation feedback

`feedback_approval` records the actor and decision on the exact proposed feedback package. Rejection
records a reason.
It does not invalidate results. `practice_result_invalidation` records correction
and restoration separately. Neither an approval nor a valid citation proves the underlying judgment
is correct.

Conversation feedback linked by a completed Heph turn counts as delivered only after the presentation
record confirms it was shown. Without that confirmation it is `UNCONFIRMED`: neither delivered nor
withheld, and never prepared again. Do not infer human exposure from a tool call alone.
