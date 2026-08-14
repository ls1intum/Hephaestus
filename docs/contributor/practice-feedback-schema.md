# Practice review data model

This page records the durable relationships and invariants behind practice reviews. It deliberately
does not maintain exhaustive implementation inventories.

Use the executable sources for exact details:

- [generated database schema](./database-schema.mdx) for tables, columns, keys, and relationships;
- [the `practices` module](https://github.com/ls1intum/Hephaestus/tree/main/server/src/main/java/de/tum/cit/aet/hephaestus/practices)
  for the domain model;
- [Liquibase changelogs](https://github.com/ls1intum/Hephaestus/tree/main/server/src/main/resources/db/changelog)
  for persisted constraints and indexes;
- [`openapi.yaml`](https://github.com/ls1intum/Hephaestus/blob/main/server/openapi.yaml) for HTTP projections;
- [ADR 0021](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0021-findings-feedback-synthesis-seam.md)
  and
  [ADR 0022](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0022-observation-presence-assessment-and-schema-cleanup.md)
  for design history;
- [practice feedback language](./practice-feedback-language.md) for user-facing terms.

## Model at a glance

| Concept | Role | Relationships |
| --- | --- | --- |
| `PracticeArea` | Workspace-defined grouping for practices | Has many practices; a practice may be unassigned |
| `Practice` | Configurable criterion used by detection | Belongs to a workspace and may belong to an area |
| `PracticeRevision` | Criteria snapshot used to interpret a past result | Belongs to one practice; an observation may pin one revision |
| `Observation` | Evidence produced by one review job | Belongs to one practice and one job; may support many pieces of feedback |
| `Feedback` | One recipient-specific piece of feedback and its delivery outcome | Belongs to one job; may draw on many observations and have many placements |
| `FeedbackObservation` | Ordered evidence binding between one piece of feedback and one observation | Joins feedback to observations with a primary or supporting role |
| `FeedbackPlacement` | Where a piece of feedback was placed | Belongs to one `Feedback`; records a summary, inline, or conversation placement |
| `Reaction` | A developer response to delivered feedback | Belongs to one `Feedback` and retains its recurrence key |

## Invariants

### Evidence and feedback are separate

An observation records what a review detected. Feedback records the guidance prepared from one or more
observations and what happened to it. A placement records where that feedback appeared. This
separation preserves observations even when no feedback is composed or delivery is withheld.

Observation rows are immutable. Practice criteria are mutable, so each observation can reference the
criteria revision that applied to its review. Feedback content, provenance, and replacement link are
immutable. A replacement is inserted with a link to the prior feedback before guarded lifecycle updates
supersede it. Other guarded updates may change delivery state, delivery timestamp, or
suppression reason.

### Occurrence and recurrence have different identities

`occurrenceKey` prevents duplicate persistence of the same result within a job retry. `recurrenceKey`
correlates the same evidence locus across review jobs. A new review therefore creates a new observation
even when it reports a recurring issue.

### Presence and assessment are orthogonal

Presence says whether the reviewed work contains the practice signal. Assessment says whether the
result is good or bad for the developer.

| | `GOOD` | `BAD` |
| --- | --- | --- |
| `PRESENT` | desired behaviour is present | undesirable behaviour is present |
| `ABSENT` | undesirable behaviour is absent | desired behaviour is missing |

The two valence-free presences, `NOT_APPLICABLE` and `INCONCLUSIVE`, carry a null assessment; the
database enforces that pairing. They are not interchangeable — see
[when each is correct](./practice-review-glossary.mdx#outcome-states). Severity is present only for a
bad assessment; validation and persistence paths enforce that invariant.

#### An `ABSENT` observation must record its search

`ABSENT` is a universal claim, and the only claim a fragment of a corpus cannot support: a partial
capture of the review threads is equally consistent with "nobody raised it" and "the raising was in the
part we did not fetch". So an `ABSENT` observation carries an `evidence.search` block —

- `consulted`: the evidence source kinds actually searched,
- `lookedFor`: the thing whose absence is being reported,
- `boundary`: what the search did not cover.

A practice declares the corpus its absences range over by holding a source `EXHAUSTIVE` (see
[`EvidenceStance`](../../server/src/main/java/de/tum/cit/aet/hephaestus/practices/EvidenceStance.java)).
Every such source must appear in `consulted`, or delivery rejects the observation — the correct answer
for a search that could not cover them is `INCONCLUSIVE`. There is deliberately no second field
declaring "may assert absence here": `EXHAUSTIVE` already carries that decision, and a parallel
vocabulary would let the two contradict each other.

Both the in-sandbox normalizer and the delivery boundary enforce this. The duplication is intentional
and matches the citation rules: a runner that crashed, ran an older image, or had its output rescued
from raw text reaches delivery with nothing having validated the search.

### Recipient and subject remain distinct

An observation's `aboutUserId` identifies the developer the evidence concerns. Feedback separately
stores the subject and the recipient. They may be equal, but they are not the same concept and must not
be inferred from each other.

### Delivery state is an audit fact

Each feedback row retains its delivery outcome. See
[Evaluation Provenance Contract](./evaluation-provenance.md) for state interpretation, evaluation joins,
and limitations.

## Read projections and access

The persistence model is not an authorization boundary. Controllers define who may see each projection:

- developer observation list, detail, summary, and reflection endpoints are scoped to the authenticated
  developer;
- the pull-request observation projection shows workspace members every relevant observation for that pull
  request;
- the workspace-admin practice-review endpoints expose observations and feedback across that workspace;
- learner-facing practice projections omit detector criteria by construction.

Keep these rules enforceable in controller authorization, repository predicates, DTO shape, and tests.
Do not add a field matrix here: the
[OpenAPI specification](https://github.com/ls1intum/Hephaestus/blob/main/server/openapi.yaml) is the
current contract for fields exposed by each endpoint.
