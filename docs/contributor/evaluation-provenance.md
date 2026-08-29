---
title: Evaluation provenance
description: How evaluation runs record what produced a result, so a number can be traced back.
---

# Evaluation provenance contract

This document defines how an evaluation reconstructs a practice review. Exact columns and domain values
belong to the Java model and
[Liquibase changelogs](https://github.com/ls1intum/Hephaestus/tree/main/server/application/src/main/resources/db/changelog),
not here. The [generated database schema](./database-schema.mdx) provides the structural view.

## Per-review provenance

Each review is an `agent_job`. Provenance is captured at the stage where it becomes stable:

| Dimension | Stored in | Captured at |
| --- | --- | --- |
| Route and behaviour configuration | `agent_job.config_snapshot` | submission |
| Price rates used for accounting | `agent_job.config_snapshot.priceSnapshot` | claim |
| Prompt scaffolding digest | `agent_job.prompt_digest` | preparation |
| Injected input-file digest | `agent_job.inputs_digest` | preparation |
| Repository revision | `agent_job.metadata.commit_sha` | submission |
| Practice criteria revision | `observation.practice_revision_id` | observation persistence |
| Model identity and usage outcomes | denormalised `agent_job` usage fields and `llm_usage_event` | completion |

The frozen `ConfigSnapshot` contains the wire protocol, endpoint, upstream model identifier, context
window, output-token limit, reasoning support, connection identity, timeout, and internet policy. Its
optional `modelVersion` field is not a model identity. Treat the complete snapshot, rather than a
hand-picked tuple of fields, as the run's behaviour configuration.

`prompt_digest` is a SHA-256 root digest of the shipped prompt scaffolding. Evaluations must not aggregate
precision across different prompt digests.

`inputs_digest` covers the final map of files injected by the executor. It excludes the repository mount
and runtime-created material. The digest is path-order independent and deliberately elides every byte
occurrence of the current job UUID. Equal values therefore mean equal injected inputs **modulo that UUID
elision**, not byte-identical sandbox workspaces. A replay must also check out
`agent_job.metadata.commit_sha`.

Sampling controls may only be introduced through workspace bindings and must be frozen in
`ConfigSnapshot`. Runtime-only sampling knobs are forbidden because completed runs must remain
reproducible.

## Delivery evidence

Every composed piece of feedback that reaches the delivery layer has a `feedback` row. The Java
[`FeedbackDeliveryState`](https://github.com/ls1intum/Hephaestus/blob/main/server/application/src/main/java/de/tum/cit/aet/hephaestus/practices/feedback/FeedbackDeliveryState.java)
and
[`FeedbackSuppressionReason`](https://github.com/ls1intum/Hephaestus/blob/main/server/application/src/main/java/de/tum/cit/aet/hephaestus/practices/feedback/FeedbackSuppressionReason.java)
types own the domain values; Liquibase owns the persisted constraints.

`DELIVERED` and `SUPERSEDED` prove that a placement was recorded. They do not prove that a person read
the feedback. `SUPPRESSED` records a policy decision to withhold feedback and always carries a reason.
Any new decision point that can withhold composed feedback must write the suppressed row instead of
silently dropping it.

## Evaluation joins

- **Observation to producer:** join `observation.agent_job_id` to `agent_job` for configuration,
  digests, repository revision, and usage; join `observation.practice_revision_id` to the criteria used
  for that observation.
- **Observation to delivery outcome:** join through `feedback_observation` to feedback state and
  suppression reason. A link to delivered or superseded feedback proves placement. Links only to
  prepared, suppressed, or failed feedback do not. No link means no feedback was composed from that
  observation.
- **Response to delivered evidence:** join `reaction.feedback_id` to feedback, then through
  `feedback_observation` to its observations. The latest append-only row carries perceived usefulness,
  resolution, or both. Responses are accepted only for delivered feedback; a later review may supersede
  that feedback.
- **Feedback to posted location:** join `feedback_placement` and inspect `posted_comment_ref` or
  `chat_message_id`.

Criteria revisions are selected as of `agent_job.started_at`, which is stamped when the job is claimed.
A revision created after that instant is not attributed to the run.

## Known limits

- Parser discards, diff-scope filtering, unknown practice slugs, and duplicate occurrence keys happen
  before an observation exists. Logs count them, but the database contains only validated, in-scope
  observations.
- A `NOT_APPLICABLE` result is an abstention. It is not delivered and does not create a suppression row.
- A good observation linked as supporting evidence may be rendered as an abridged acknowledgement.
  Placement evidence is therefore coarser for strengths; precision evaluation should score bad
  observations.
- The repository mount is not part of `inputs_digest`; the commit SHA pins it.
- `prompt_digest` covers shipped scaffolding, not the per-job task prompt. Task-specific data is represented
  in job metadata and injected inputs.
- `inputs_digest` compares materialised bytes, not semantic source equivalence. Different materialisations
  produce different digests.
- Older rows may have null digests or no practice revision. Exclude them from evaluations rather than
  inferring missing provenance.
