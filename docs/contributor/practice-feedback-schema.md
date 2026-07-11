# Practice & Feedback Schema — Reference

> Status: living reference for the `practices` Spring Modulith module (entities under
> `server/src/main/java/de/tum/cit/aet/hephaestus/practices/**`). Grounded in ADR 0021
> (`docs/decisions/0021-findings-feedback-synthesis-seam.md`) and ADR 0022
> (`docs/decisions/0022-observation-presence-assessment-and-schema-cleanup.md`), which retires
> "finding" for **observation** (`presence` × `assessment`). Uses the **canonical vocabulary**
> throughout — *area*, never *goal* — one word per concept across code, schema, API, and UI.

---

## 1. Orientation

This schema records, for every contribution a developer makes, **what software-engineering
practices an AI agent observed** and **what feedback a student actually saw**. This schema exists to
answer: *do good engineering practices appear, change over time, and respond to targeted feedback?*
To answer that honestly the schema separates three things that
naive review tools fuse: the **raw observation** (an `Observation`, audience-neutral, append-only,
deduplicated by a stable cross-run recurrence key), the **synthesised delivery** of one or more of those
observations to one person (a `Feedback` unit, with its own provenance and delivery lifecycle), and
the **rule metadata** that gives an observation meaning (a `Practice`, carrying its criteria and
trigger events, optionally rolled up into a `PracticeArea` learning bucket). Everything is
append-only so the temporal record of *what a student was shown, and how they reacted* survives
re-runs and supersession — the substrate that answering "do practices change over time?" depends on.

The vocabulary is deliberately aligned with finding-interchange standards (SARIF, SonarQube,
GitHub code-scanning) where one exists, and with learning-analytics standards (xAPI, Caliper) for the
actor/recipient distinction those finding standards lack — and it diverges from them only where the
divergence is load-bearing (the `presence` × `assessment` split that replaces a single signed
outcome). The naming choices below are *grounded*, not asserted: each is justified against the
standard it tracks or the reason it departs.

---

## 1a. Construct grounding (practice theory)

The entity model is grounded in the theory of **organizational routines** (the abstract, tacit standard
of a practice vs. its situated enactments) and in **formative-assessment** theory for the delivery layer.
The full literature rationale lives in ADR 0021
(`docs/decisions/0021-findings-feedback-synthesis-seam.md`); in engineering terms the mapping is:

| Concept | Hephaestus object | What it is |
| --- | --- | --- |
| the practice as a shared, tacit standard | the **`Practice` *concept*** | lives in the cohort's shared understanding; NOT a column |
| the written rule representing that standard | **`Practice.criteria`** | a partial, codified representation — never the standard itself |
| a single observed enactment | **`Observation`** | the situated, observable trace |
| feedback synthesised for a person | **`Feedback`** | task-framed feed-up / feed-back / feed-forward |

---

## 2. Ubiquitous language — one canonical name per concept

This is the **single source of truth for naming**. Each concept has exactly one canonical name; the
"Legacy term it replaced" column names the spelling that must no longer appear in new code. Per the
DDD *Ubiquitous Language* principle, model, code, schema, API and UI share one word per concept
([Fowler, *UbiquitousLanguage*](https://martinfowler.com/bliki/UbiquitousLanguage.html); Evans, *DDD*).

> The evaluation model is the `presence` × `assessment` split of
> [ADR 0022](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0022-observation-presence-assessment-and-schema-cleanup.md): `presence`
> (`PRESENT`/`ABSENT`/`NOT_APPLICABLE`) × `assessment` (`GOOD`/`BAD`, NULL iff
> `presence = NOT_APPLICABLE`). There is no `Practice.kind` and no `subject_role`; direction is recomputed
> as `assessment = BAD`, and the reaction anchors on the delivered feedback.

| Concept (one canonical name) | Canonical term | Legacy term it replaced | Why the rename |
| --- | --- | --- | --- |
| Grouping bucket over practices | **PracticeArea** / `area` | `PracticeGoal` / `goal` | "goal" implies a *target state* to reach; the entity is a neutral grouping bucket (a SARIF *taxon*), not an objective — see §2 grounding below |
| One observed practice evaluation | **Observation** (`observation`) | `PracticeFinding` / `practice_finding` | the row *is* an observation of a practice; "finding" implied a baked-in problem. It carries two orthogonal axes, `presence` × `assessment` |
| Was the signal present? | **presence** (`PRESENT` / `ABSENT` / `NOT_APPLICABLE`) | the signed `observation` enum (`OBSERVED` / `NOT_OBSERVED`) | `OBSERVED`→`PRESENT`, `NOT_OBSERVED`→`ABSENT`; presence is measurement, free of any good/bad judgement |
| Is what was seen good or bad? | **assessment** (`GOOD` / `BAD`, NULL iff `NOT_APPLICABLE`) | `Practice.kind` / `PracticeKind` direction | valence is resolved per observation by the detector, not fixed on the rule; `Practice.kind` is gone |
| The person whose work is evaluated | **developer** | `contributor` | one word for the SCM author across the module |
| The kind of work reviewed | **WorkArtifact** / `artifact_type` | `focus_artifact`, `target` | names the *work* (PR/issue), not a build artifact nor a vendor object |
| Cross-run observation identity | **recurrence_key** | `finding_fingerprint`, `correlation_key` | states the purpose — the locus that recurs across runs (SARIF `partialFingerprints`); the older names were opaque or finding-flavoured |
| Cross-run feedback continuity | **thread_key** | `feedback_thread_key`, `continuity_key` | names *what it threads* (successive deliveries), domain-readable |
| Delivery destination class | **FeedbackChannel** / `channel` (column) | `surface` | the concept and the column are both "channel" now |
| One physical render of a unit | **FeedbackPlacement** / **PlacementType** | `placement` / `slot` | the type enum is `PlacementType`; the row is the `FeedbackPlacement` |
| Observation's weight inside a unit | **EvidenceRole** (`PRIMARY` / `SUPPORTING`) | `display_role` | it weights *evidence*, not display |
| The detection timestamp | **observed_at** | `detected_at` | names *when the practice was observed*, matching the `Observation` entity |

### Grounding the naming choices

- **PracticeArea (not goal).** Grouping rules under a category is standard: SonarQube groups rules into
  *quality profiles* and tags each rule with a *clean-code category* and a *software quality*
  ([SonarQube clean code](https://docs.sonarsource.com/sonarqube-server/10.8/core-concepts/clean-code/software-qualities)),
  and SARIF formalises rule taxonomies via `taxa` / `reportingDescriptorRelationship`
  ([SARIF v2.1.0](https://docs.oasis-open.org/sarif/sarif/v2.1.0/csprd01/sarif-v2.1.0-csprd01.html)).
  Candidate nouns were weighed: *category/taxon* (reads as static classification, not a
  workspace-configurable bucket); *quality profile* (an activatable rule **set** — a different concept,
  and our active toggles already live on `Practice.active` / `PracticeArea.active`); *competency/goal/
  objective* (learning-framework terms, but *goal/objective* over-claim a target state). **PracticeArea**
  is the defensible middle: a neutral grouping noun like a SARIF taxon, kept in our software-practice
  domain language rather than borrowing SonarQube's overloaded "profile" or education's over-claiming
  "competency". *goal* is therefore eliminated.
- **presence × assessment (deliberate divergence from SARIF `kind`).** SARIF `result.kind`
  (`pass`/`fail`/`notApplicable`) bakes the good/bad direction into the result, so a rule can only ever
  "fail". We split that into two orthogonal columns on the observation: `presence`
  (`PRESENT`/`ABSENT`/`NOT_APPLICABLE`) is measurement, and `assessment` (`GOOD`/`BAD`, NULL iff
  `presence = NOT_APPLICABLE`) is valence — resolved **per observation** by the detector, not fixed on the
  rule. This is a defensible refinement, and lossless on SARIF export: `(PRESENT,GOOD)→pass`,
  `(ABSENT,BAD)→fail`, `(PRESENT,BAD)→fail`, `(ABSENT,GOOD)→pass`, `NOT_APPLICABLE→notApplicable`.
  `NOT_APPLICABLE` is a verbatim match for SARIF `notApplicable`. There is no rule-direction column: the
  good/bad meaning lives in `criteria` + `what_good_looks_like`, and readers recompute "is this a problem?"
  as `assessment = BAD`. See [ADR 0022](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0022-observation-presence-assessment-and-schema-cleanup.md).
- **recurrence_key.** Directly analogous to SARIF `partialFingerprints` + `result.baselineState`
  (`new`/`unchanged`/`updated`/`absent`), the mechanism by which a Results-Management System decides "is
  this the *same* observation as last run?" ([SARIF issue #615](https://github.com/oasis-tcs/sarif-spec/issues/615)).
  Excluding job-id, line number and title from the digest mirrors the SARIF guidance that fingerprints be
  stable against churn.

---

## 3. Per-entity reference

### 3.1 `Practice` — table `practice`

Workspace-scoped rule definition. `artifact_type` lets one detector schema serve PR and issue work
without per-case branching; desirable vs undesirable behaviour is no longer a rule column (it is
resolved per observation as `assessment`), and there is no `subject_role` — direction and conduct are
recomputed from the observation, not fixed on the practice.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | Long | `id` | no | Auto-generated PK (`IDENTITY`). | Surrogate key. |
| workspace | Workspace (`@ManyToOne`) | `workspace_id` | no | Owning workspace (FK `fk_practice_workspace`). | SQL-layer multi-tenancy (`core/tenancy`). |
| slug | String(64) | `slug` | no | Stable machine key, unique per workspace (`uk_practice_workspace_slug`). | Survives a `name` rename; the routing/catalog key. |
| name | String(128) | `name` | no | Display label. | Human-readable in dashboards/feedback. |
| artifactType | WorkArtifact | `artifact_type` | no (default `PULL_REQUEST`) | Routes trigger gate, context builder, `AgentJobType`/handler, and delivery surface. | Single pipeline discriminator; default keeps existing rows behaviour-preserving. SARIF has no analogue — it is a file format, not a pipeline router. |
| **area** | PracticeArea (`@ManyToOne`) | `practice_area_id` | yes | Optional roll-up bucket (NULL = ungrouped); 1:N. FK `fk_practice_area`, index `idx_practice_practice_area`. | Single owning bucket keeps per-area progress denominator unambiguous. SARIF `taxa`-style grouping. |
| triggerEvents | JsonNode (jsonb) | `trigger_events` | no | Which domain events activate detection. | JSONB keeps the event set open without schema churn. |
| criteria | String (TEXT) | `criteria` | no | NL spec passed to the detection agent. | The rule body the LLM evaluates against. Detector/admin **reference** register — never delivered to a learner (§3a). |
| whyItMatters | String (TEXT) | `why_it_matters` | yes | Admin-authored learner-facing *explanation* — why this practice matters. Seeded for all 32 default practices; editable in the practices admin form. | Developer-facing Layer 1 (Nicol & Macfarlane-Dick 2006 P1 feed-up; Diátaxis *explanation*). Surfaced via `LearnerPracticeDTO` (§3a), never the detector. |
| whatGoodLooksLike | String (TEXT) | `what_good_looks_like` | yes | Admin-authored learner-facing **exemplar** — what good looks like. Seeded for all 32 default practices; an authoring guard rejects detector vocabulary (`PRESENT`/`ABSENT`/`NOT_APPLICABLE`, `GOOD`/`BAD`) in this field. | Developer-facing Layer 2 (Sadler 1989 exemplar; Hattie feed-forward). The guard keeps the rubric-leak/Goodhart vector physically closed. |
| precomputeScript | String (TEXT) | `precompute_script` | yes | Optional Bun/TS static-analysis producing *hints, not verdicts*. | Narrows the agent search space; hints never become verdicts (provenance-admission contract). |
| active | boolean | `is_active` | no (default true) | Soft delete / feature flag for detection. | Toggle without losing history. |
| createdAt | Instant | `created_at` | no (immutable) | Insert timestamp. | Audit trail. |
| updatedAt | Instant | `updated_at` | yes | Last-update timestamp. | Audit trail. |

### 3.2 `PracticeArea` — table `practice_area`

Workspace-scoped **read-model / organising** concept that groups practices into a configurable learning
bucket. A practice belongs to at most one area; the 1:N binding is load-bearing for the per-area
acted-on/total progress denominator. An area never enters `trigger_events`, `criteria`, the detector,
or the observation schema — practices remain the unit of detection.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | Long | `id` | no | Auto-generated PK. | Surrogate key. |
| workspace | Workspace (`@ManyToOne`) | `workspace_id` | no | Owning workspace (FK `fk_practice_area_workspace`). | Tenancy binding. |
| slug | String(64) | `slug` | no | Stable key, unique per workspace (`uk_practice_area_workspace_slug`). | Survives a `name` rename. |
| name | String(128) | `name` | no | Admin-renameable display label. | Dashboard label. |
| description | String (TEXT) | `description` | yes | Optional blurb on the area card. | Context for the bucket. |
| active | boolean | `is_active` | no (default true) | Cohort-level visibility toggle; independent of `Practice.active`. | Surface/hide on dashboards without touching detection. |
| displayOrder | int | `display_order` | no (default 0) | Admin dashboard ordering. | Deterministic UI sequence. |
| createdAt | Instant | `created_at` | no (immutable) | Insert timestamp. | Audit trail. |
| updatedAt | Instant | `updated_at` | yes | Last-update timestamp. | Audit trail. |

### 3.3 `Observation` — table `observation`

Immutable, append-only, deduplicated record of one practice evaluation. **One-to-one structural
analogue of a SARIF `result`** ([SARIF v2.1.0](https://docs.oasis-open.org/sarif/sarif/v2.1.0/csprd01/sarif-v2.1.0-csprd01.html)):
it references a rule (`practice` ≈ SARIF `ruleId`), carries a message (`title`), `reasoning`, structured
`evidence` (≈ `result.locations` + `relatedLocations` + `codeFlows`), a `severity` (≈ `result.level`), and
a stable cross-run identity (`recurrence_key` ≈ `partialFingerprints`). Its outcome is the two-column
`presence` × `assessment` pair rather than a single signed enum. Our deduped-across-runs notion
(GitHub code-scanning's "alert" grain) lives in `recurrence_key`.

The observation carries **evidence + presence/assessment + reasoning** (the justification) and **no advice**: it is
immutable evidence (ADR 0021 — an observation gives an outcome, but no advice). Advice is *feedback*, so it is
composed into the delivered `Feedback` (`body`, §3.5) and the developer-facing read surfaces
(reflection dashboard, observation detail, mentor history) source it from there — never from the observation.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | UUID | `id` | no | PK; assigned in `@PrePersist` if null. `insertIfAbsent` bypasses it and requires explicit UUID. | Immutable identifier. |
| occurrenceKey | String(255) | `occurrence_key` | no | Per-occurrence dedup key, unique (`uk_observation_occurrence`). | Race-safe `insertIfAbsent` upsert; the same observation cannot double-insert on re-run. Distinct from the `recurrence_key` locus grain. |
| agentJobId | UUID | `agent_job_id` | no | Producing job (FK `fk_observation_agent_job`, Liquibase-managed). Raw UUID, not `@ManyToOne`. | Avoids a Modulith cycle into the `agent` module; cascade-delete with the job. |
| practice | Practice (`@ManyToOne`) | `practice_id` | no | Evaluated practice; DB `ON DELETE CASCADE`. | Deleting a practice cleans its immutable observations without lifecycle callbacks. |
| practiceRevision | PracticeRevision (`@ManyToOne`) | `practice_revision_id` | yes | Pins the observation to the `criteria`-as-it-was when the detector evaluated it; FK `fk_observation_revision`, DB `ON DELETE SET NULL`. The delivery service looks up the current revision per practice and writes it on the native insert. | Reproducibility: *which criteria version fired this observation* is queryable (SCD-2 point-in-time). NULL marks pre-versioning observations honestly (deleting a revision detaches without losing the observation). See §3.8. |
| artifactType | WorkArtifact | `artifact_type` | no | PR vs ISSUE the observation targets. | Routes the observation to the correct dashboard/channel. |
| artifactId | Long | `artifact_id` | no | External id of the target PR/issue. | Links to the specific artifact. |
| developer | User (`@ManyToOne`) | `developer_id` | no | The contribution author being evaluated; FK `RESTRICT` (no cascade). | Observations outlive users; deleting a user with observations is blocked. |
| aboutUserId | Long | `about_user_id` | **no (ALWAYS populated)** | Whose conduct the observation is *about*. Raw Long FK `sfk_observation_subject` (the `sfk_` prefix marks a deliberate scalar user FK — no `@ManyToOne` — so the Liquibase schema-drift gate treats it as intentional). | xAPI Actor (the agent a statement is about) is mandatory and unambiguous — [xAPI](https://xapi.com/statements-101/). The former *null⇒developer* fallback was collapsed to an explicit value (§4). |
| recurrenceKey | String(64) | `recurrence_key` | yes | Deterministic hash of what the observation is **about** (practice + target + subject + content anchor), never of *when* it was produced. | Enables supersession and reaction-continuity across re-detections. SARIF `partialFingerprints` — title excluded because the LLM re-words it every run. Nullable: backfill-free, new observations only. |
| title | String(255) | `title` | no | Short headline. | SARIF `result.message`. |
| **presence** | Presence | `presence` | no | Was the signal present: `PRESENT` / `ABSENT` / `NOT_APPLICABLE`. | Measurement axis, free of valence. SARIF `result.kind` with the direction factored out. |
| **assessment** | Assessment | `assessment` | yes | Is what was seen `GOOD` or `BAD`; NULL iff `presence = NOT_APPLICABLE`. | Valence axis, resolved per observation by the detector; replaces the rule-level `Practice.kind`. |
| severity | Severity | `severity` | yes | Impact level, set only when `assessment = BAD`. | SARIF `kind ⟂ level`; SonarQube blocker..info ladder. NULL on a GOOD or NOT_APPLICABLE observation. |
| confidence | Float | `confidence` | no | Agent confidence 0.0–1.0. | Delivery filtering + quality; ≈ SARIF `result.rank` (diagnostic relevance). |
| evidence | JsonNode (jsonb) | `evidence` | yes | `{locations:[{path,startLine,endLine}], snippets:[…], references:[…]}`. | Location is JSON, not columns, because many practices have no file location and observations can be multi-location. ≈ SARIF `result.locations`. |
| reasoning | String (TEXT) | `reasoning` | yes | Agent's rationale for the outcome (the justification, not advice). | Quality review + developer education. |
| observedAt | Instant | `observed_at` | no | Detection timestamp; `@PrePersist` if null. | Temporal ordering for trends. |

### 3.4 `Reaction` — table `reaction`

Immutable, append-only record of a developer's reaction to **delivered feedback** — never to a private
observation. **Explicitly excluded from agent context (#895)** so prior disputes never contaminate
detection accuracy. The latest row per `(feedback, developer)` is the current state.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | UUID | `id` | no | PK; `@PrePersist` if null. | Immutable identifier. |
| feedback | Feedback (`@ManyToOne`) | `feedback_id` | no | Reacted-to feedback unit; DB `ON DELETE CASCADE`. | A developer reacts to the *delivered feedback*, not a private observation; authorization is the feedback recipient. |
| feedbackId | UUID | `feedback_id` | no (read-only mirror) | Scalar access without lazy-loading the proxy. | Avoids a proxy hit in hot reads. |
| recurrenceKey | String(64) | `recurrence_key` | yes | Denormalised copy of the feedback's underlying locus recurrence key at reaction-write time. | `thread_key` exists only on *delivered* units, so a withheld-but-recurring locus has no other cross-run key; this lets B2 suppression find a prior DISPUTED / NOT_APPLICABLE reaction. Index `idx_reaction_recurrence`. |
| reactorUserId | Long | `reactor_user_id` | no | Reacting developer; FK `RESTRICT`. | Reactions outlive users; the single identity on a reaction. |
| action | ReactionAction | `action` | no | `ADDRESSED` / `DISPUTED` (RESPONSE axis) / `NOT_APPLICABLE` (VALIDITY axis). | Measurement signal (which feedback was acted on vs disputed), not workflow state. The value set is a *recipience act* (see note below). |
| explanation | String (TEXT) | `explanation` | yes | Free-text rationale (required for `DISPUTED`; the required explanation *is* the evaluative judgement). | Qualitative dispute signal. |
| createdAt | Instant | `created_at` | no (immutable) | When the reaction was submitted. | Temporal "changed my mind" record. |

> **`ReactionAction` partitions into two orthogonal sub-axes.** RESPONSE `{ADDRESSED, DISPUTED}` is what
> the learner *did*: `ADDRESSED` records action to close the gap (a proxy, not verified closure — so
> `ReactionSuppressionFilter` may re-nag an `ADDRESSED`-but-still-`ABSENT` locus); `DISPUTED` is the
> reasoned rejection and requires an `explanation`. VALIDITY `{NOT_APPLICABLE}` is a relevance judgement,
> not an uptake act, so `ReactionEngagementDTO` excludes it from every uptake / non-uptake ratio and
> reports it as a separate scope signal. The recipience-literature grounding for this split lives in
> [ADR 0022](https://github.com/ls1intum/Hephaestus/blob/main/docs/decisions/0022-observation-presence-assessment-and-schema-cleanup.md) § Evidence.

### 3.5 `Feedback` — table `feedback`

Immutable, append-only synthesised **delivery** unit for a single recipient — the developer-facing
rendering of one or more observations, with body, channel, source,
and delivery lifecycle. A re-run inserts a new row (deduped by the `(agent_job_id, position)` unique)
and points `replaces_id` at the prior one rather than mutating it, so the record of what a student
actually saw is preserved. `baseline_state` is intentionally **not** a column — derived on read from the
supersession chain.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | UUID | `id` | no | PK; `@PrePersist` if null. | Immutable identifier. |
| agentJobId | UUID | `agent_job_id` | no | Producing job (FK `fk_feedback_agent_job`, Liquibase). Raw UUID. Unique with `position`. | Avoids a cycle into `agent`; cascade-delete. `(agent_job_id, position)` is the dedup key — a re-emitted job cannot double-insert a unit. |
| workspaceId | Long | `workspace_id` | no | Owning workspace (FK `fk_feedback_workspace`, Liquibase). Raw Long. | Avoids a cycle into `workspace`; purge removes feedback explicitly. |
| artifactType | WorkArtifact | `artifact_type` | yes | Kind of artifact this is about. | Nullable: reflection-dashboard feedback is not anchored to one artifact. |
| artifactId | Long | `artifact_id` | yes | External id of the target. | Nullable in lockstep with `artifactType`. |
| recipientUserId | Long | `recipient_user_id` | no | The user this is delivered **to** (FK `fk_feedback_recipient`). Raw Long. | Messaging "To"-recipient; distinct from subject. ≈ xAPI Authority / audience — [xAPI/Caliper comparison](https://www.imsglobal.org/initial-xapicaliper-comparison). |
| aboutUserId | Long | `about_user_id` | **no (ALWAYS populated)** | The user this is **about**: equals `recipientUserId` for author-facing units, the subject (e.g. the reviewer) when ≠ recipient (reviewer-side feedback). | xAPI Actor (mandatory, unambiguous). NOT NULL since the symmetry migration backfilled `about_user_id = recipient_user_id`, matching `Observation.aboutUserId` — no delivery-row fallback remains (§4). |
| channel | FeedbackChannel | `channel` | no | Destination class (in-context / conversation / profile). | Decouples "what we say" from "where it lands". |
| position | Integer | `position` | no | 0-based position within the producing job's output. | Dedup-key component + stable delivery order. |
| deliveryState | FeedbackDeliveryState | `delivery_state` | no | Lifecycle: prepared → delivered / superseded / suppressed / failed. | Conventional delivery state machine; SUPPRESSED ≈ SARIF `result.suppressions`. |
| suppressionReason | FeedbackSuppressionReason | `suppression_reason` | yes | Why a unit was withheld (set only when SUPPRESSED). | ≈ SARIF `suppression.justification`. |
| body | String (TEXT) | `body` | yes | Final student-facing body. | Null while PREPARED or when suppressed. |
| source | FeedbackSource | `source` | no | `AGENT` / `POLICY_FLOOR` / `FALLBACK`. | Provenance: policy/fallback units must not be scored as model output. |
| replacesId | UUID | `replaces_id` | yes | Self-FK to the prior row this replaces (`fk_feedback_replaces`). | Re-delivery without duplication; null for first delivery. ≈ SARIF `baselineState=updated`. |
| threadKey | String(64) | `thread_key` | yes | Cross-run continuity tying successive deliveries of "the same" feedback, independent of job. Indexed (`idx_feedback_continuity`). | Conversation continuity across runs. |
| createdAt | Instant | `created_at` | no (immutable) | Insert timestamp; `@PrePersist`. | Audit + temporal ordering. |
| deliveredAt | Instant | `delivered_at` | yes | When actually delivered. | Null while PREPARED, suppressed, or failed. |

### 3.6 `FeedbackObservation` — table `feedback_observation`

Immutable many-to-many join binding a `Feedback` unit to the `Observation`s it was composed from.
Both sides live in the `practices` module, so real `@ManyToOne` associations are used. Composite PK
`(feedback_id, observation_id)` makes the binding idempotent.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | Id (embedded) | `(feedback_id, observation_id)` | no | Composite PK. | Idempotent binding; written via `insertIfAbsent`. |
| feedback | Feedback (`@ManyToOne`, `@MapsId`) | `feedback_id` | no | Composed unit; DB `ON DELETE CASCADE`. | Read-only mirror of the key. |
| observation | Observation (`@ManyToOne`, `@MapsId`) | `observation_id` | no | Bound evidence; DB `ON DELETE CASCADE`. | An observation can be reused across units. |
| **role** | EvidenceRole | `role` | no | `PRIMARY` (anchors the headline) / `SUPPORTING` (corroborates); DB CHECK `chk_feedback_observation_role`. | One unit can fuse a PRIMARY plus SUPPORTING observations. |
| ordinal | Integer | `ordinal` | no | Stable ordering within the unit (lower = earlier). | Deterministic narrative order. |

### 3.7 `FeedbackPlacement` — table `feedback_placement`

Immutable record of one concrete placement of a `Feedback` unit on a delivery surface — a summary block,
an inline diff anchor, or a conversation turn. 1:N from `Feedback`. Carries the diff-anchor coordinates
actually used so re-delivery and snapping reconcile per anchor.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | UUID | `id` | no | PK; `@PrePersist` if null. | Immutable identifier. |
| feedback | Feedback (`@ManyToOne`) | `feedback_id` | no | Parent unit; DB `ON DELETE CASCADE` (FK `fk_feedback_placement_feedback`). | Cleans placements with the unit. |
| feedbackId | UUID | `feedback_id` | no (read-only mirror) | Scalar id without lazy load. | Hot-read optimisation. |
| placementType | PlacementType | `placement_type` | no | `SUMMARY` / `INLINE` / `CONVERSATION_TURN`. | Where it renders. |
| anchorKind | PlacementAnchorKind | `anchor_kind` | yes | `LINE` / `RANGE` / `FILE` / `IMAGE`. | Only INLINE placements anchor to a diff. |
| anchorPath | String (TEXT) | `anchor_path` | yes | Head-side file path. | Inline anchor. |
| anchorStartLine | Integer | `anchor_start_line` | yes | First anchored line (1-based). | Inline anchor. |
| anchorEndLine | Integer | `anchor_end_line` | yes | Last anchored line. | Equals start for single line. |
| anchorSide | PlacementAnchorSide | `anchor_side` | yes | `OLD` / `NEW` for the end/single line. | Unified-diff side. |
| postedCommentRef | String (TEXT) | `posted_comment_ref` | yes | External comment/note id (indexed). | Reconciliation; 1 per placement. |
| createdAt | Instant | `created_at` | no (immutable) | Insert timestamp. | Audit. |

### 3.8 `PracticeRevision` — table `practice_revision`

Append-only **slowly-changing-dimension (SCD Type 2)** history of a practice's `criteria` text. Every
time the `criteria` actually changes (value-compared), `PracticeService` appends a new revision; revision
1 is written on practice create. `Practice.criteria` stays the **current projection** (no read path
breaks), while `practice_revision` records every prior wording, so the recursive ostensive↔performative
loop — admins reshaping the criteria over time in response to what they see enacted — is *recorded,
not overwritten* (D'Adderio 2011 translation loop; the qualitative-coding codebook audit-trail / IRR
norm; data-warehousing SCD-2). `Observation.practice_revision_id` (§3.3) pins each observation to the
revision in force when it was detected, making *which criteria version fired this observation* queryable.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | Long | `id` | no | Auto-generated PK. | Surrogate key. |
| practice | Practice (`@ManyToOne`) | `practice_id` | no | Owning practice; FK `fk_practice_revision_practice`, DB `ON DELETE CASCADE`. | Revisions are part of the practice's lifecycle; deleting the practice removes its history. |
| revisionNumber | int | `revision_number` | no | Monotonic per-practice revision counter; revision 1 on create, `+1` on each criteria change. Unique with `practice_id` (`uk_practice_revision_practice_number`). | Point-in-time ordering; the cross-finding identity of a criteria version. |
| criteria | String (TEXT) | `criteria` | no | Snapshot of the `criteria` text as of this revision. | The audit-trail record of the in-force rubric wording. |
| createdAt | Instant | `created_at` | no (immutable) | When this revision was written. | Temporal reconstruction. |

> A backfill changeset creates revision 1 for every practice that existed before versioning shipped, so
> the history is complete from the migration forward; observations detected before versioning pin to NULL
> (an honest "pre-versioning" marker, not a reproducible rubric snapshot).

### 3.9 Enumerations

| Enum | Values & meaning | Grounding |
| --- | --- | --- |
| **WorkArtifact** | `PULL_REQUEST` (code diff + commits + review thread, delivered in-PR), `ISSUE` (title, body, labels, assignees, comment thread, state-transition timeline — no diff). | Named for the *work*, not a tool; closed set grows in lockstep with a runtime that can build that artifact's context (design doc, chat thread). |
| **Presence** | `PRESENT` (the signal is there), `ABSENT` (expected but missing), `NOT_APPLICABLE` (practice irrelevant to the work). | Measurement axis; ≈ SARIF `result.kind` with the direction factored out. `NOT_APPLICABLE` is a verbatim SARIF match. `PRESENT`/`ABSENT` replace the signed `OBSERVED`/`NOT_OBSERVED`. |
| **Assessment** | `GOOD` (a strength), `BAD` (a problem); NULL iff `presence = NOT_APPLICABLE`. | Valence axis, resolved per observation by the detector. Replaces the rule-level `Practice.kind`; "is this a problem?" = `assessment = BAD`. |
| **Severity** | `CRITICAL`, `MAJOR`, `MINOR`, `INFO` — impact, set only when `assessment = BAD`. | SARIF `result.level` / SonarQube blocker..info. NULL on GOOD or NOT_APPLICABLE. |
| **ReactionAction** | **RESPONSE axis:** `ADDRESSED` ("acted to close the gap"; outcome unverified), `DISPUTED` ("AI is wrong", reasoned rejection, requires explanation). **VALIDITY axis:** `NOT_APPLICABLE` ("valid but irrelevant"; excluded from uptake ratios). | Recipience act (Winstone 2017 "enacting"); not workflow. `ADDRESSED` renamed from `APPLIED` (claimed action ≠ verified closure). No `DISMISSED`/`ACKNOWLEDGED` — non-action = absence of a row; affective dismissal deferred behind a UI affordance (§6). |
| **EvidenceRole** | `PRIMARY` (anchors the headline), `SUPPORTING` (corroborates). | Synthesis-time weighting; replaces `display_role`. |
| **FeedbackChannel** | `IN_CONTEXT` (on the PR/issue), `CONVERSATION` (mentor turn), `PROFILE` (recipient's private dashboard). | Decouples message from channel. Every channel is developer-facing. `PROFILE` replaces legacy `REFLECTION_DASHBOARD`. |
| **FeedbackDeliveryState** | `PREPARED`, `DELIVERED`, `SUPERSEDED` (replaced via `replaces_id`), `SUPPRESSED` (withheld; see reason), `FAILED`. | Delivery state machine + review-tool edit-in-place (SUPERSEDED) + SARIF `suppressions` (SUPPRESSED). |
| **FeedbackSuppressionReason** | `REVIEWER_SIDE`, `BELOW_THRESHOLD`, `LOW_CONFIDENCE`, `POLICY_FLOOR_DROP`, `REACTED_DISPUTED` (subject DISPUTED this locus earlier — B2), `REACTED_NOT_APPLICABLE` (subject marked N/A earlier — B2). | ≈ SARIF `suppression.justification`. `REVIEWER_SIDE` replaces legacy `AUDIENCE_REVIEWER`. |
| **FeedbackSource** | `AGENT` (LLM), `POLICY_FLOOR` (deterministic guaranteed-coverage), `FALLBACK` (synthesis unavailable/failed). | Provenance for honest quality measurement (column `source`). |
| **PlacementType** | `SUMMARY`, `INLINE`, `CONVERSATION_TURN`. | Where a placement renders. Replaces the `placement`/`slot` word. |
| **PlacementAnchorKind** | `LINE`, `RANGE`, `FILE`, `IMAGE`. | Diff-anchor granularity. |
| **PlacementAnchorSide** | `OLD` (left/base), `NEW` (right/head). | Unified-diff side. |

---

## 3a. Stakeholder display model (the projection contract)

The schema encodes two orthogonal axes that the display layer must keep separate:

- **Actor axis** = the observation's `about_user_id` — *who* the practice evaluates. Already encoded.
- **Audience axis** = *who reads the analytic*. The display model adds this. Every feedback unit is
  developer-facing first: a developer always reads their **own** report (`GET /practices/reports/me`).
  Beyond that, exactly two bounded non-developer reads exist, both enforced server-side in
  `practices.report.PracticeReportController`:
  1. A workspace **ADMIN/OWNER** (the mentor role) may read the named roster (`GET /practices/reports`)
     and a per-developer drill-down (`GET /practices/reports/{userId}`). Every serving of either writes an
     append-only `DataAccessEvent` disclosure row (`core.audit`) — the mentor read is *audited*, never silent.
  2. The **anonymised cohort** rollup (`GET /practices/cohort`, k-anonymised with K = 5 small-cell
     suppression) is readable by admins/owners always, and by regular members only when the workspace's
     `cohortVisibility` feature is `EVERYONE` (default: `MENTORS_ONLY`).

  The researcher analysing **anonymised** study data and the workspace admin editing the catalog
  (criteria, area labels) remain distinct audiences; no audience ever receives an *unaudited,
  non-anonymised* view of another developer's feedback.

**Field-by-audience matrix — enforce server-side, never in the webapp.** "Developer" and "Reviewer" are
the *same human*; the column that applies is selected by the **observation's `about_user_id`**, not a static
user role, so reviewer-craft never leaks to the author. The `whyItMatters` / `whatGoodLooksLike`
learner-layer columns are **implemented** — admin-authored `Practice` columns served to learners through
`LearnerPracticeDTO` (`GET /practices/learner`), which carries no `criteria` field by construction.

| Field | Developer / Learner | Reviewer (observation about the reviewer) | Mentor (workspace ADMIN/OWNER) | Researcher / Admin |
| --- | --- | --- | --- | --- |
| `name` | yes | yes | yes | yes |
| `whyItMatters` | yes — Layer 1 | yes | yes | yes |
| `whatGoodLooksLike` | yes — Layer 2 (on request) | yes | yes | yes |
| area / area progress | yes — own | yes — own | yes — subject's, via audited drill-down | yes — anonymised |
| per-observation `Feedback` (task-framed) | yes — own only | yes — own only | yes — subject's report cards; every serving writes a `DataAccessEvent` | yes — anonymised |
| **`criteria`** | **NEVER** | **NEVER** | via the catalog-editing surface only — never in a report | yes — edit (admin) |
| `precomputeScript`, `triggerEvents` | no | no | via admin surfaces only | yes — edit (admin) |
| raw `presence` / `assessment` label | no — delivered as task-framed feedback | no | no — same task-framed cards the developer sees | yes — anonymised |
| reaction `NOT_APPLICABLE` (validity signal) | own — scope signal, **not** uptake | n/a | no | yes — anonymised |

**Two hard rules from theory (not UX taste):**

1. **`criteria` is NEVER delivered to a learner.** Three independent groundings: Diátaxis register
   mismatch (`criteria` is *reference* material for the detector/admin; a learner needs *explanation* +
   *how-to/example*); Kluger & DeNisi (1996) — a rubric+score frame directs attention to the self/standard
   and can *depress* performance; and Goodhart-style gaming of an exposed detection rubric. Omission is
   **physical**, not policy: `LearnerPracticeDTO` is a record with no `criteria` component, so the field
   cannot reach a learner even by accident (an integration test asserts the raw `GET /practices/learner`
   JSON contains no `"criteria"`) — mirroring how CodeQL physically separates the developer-facing
   `.qhelp` from the `.ql` query metadata. An authoring guard additionally rejects detector
   vocabulary (`PRESENT`/`ABSENT`/`NOT_APPLICABLE`, `GOOD`/`BAD`) in `whatGoodLooksLike`, keeping the rubric out
   of the learner copy at the source.
2. **Visibility keys off the *observation's* `about_user_id`, not a static user role.** Reviewer-craft feedback
   must not leak to the author — a known prior bug class in this codebase.

**Within-learner progressive disclosure (NN/g, Nielsen 1995):** Layer 1 = `name` + `whyItMatters` + this
finding's feed-forward (what most learners need most of the time); Layer 2 (on request) =
`whatGoodLooksLike` + area context + personal trend. Layer 3 (`criteria` / detector config) is a
**different audience**, not a deeper layer — never surfaced to a learner.

*Sources:* ADL xAPI ([Statements 101](https://xapi.com/statements-101/)) / IMS Caliper
([comparison](https://www.imsglobal.org/initial-xapicaliper-comparison)) — actor↔audience separation ·
Diátaxis ([diataxis.fr](https://diataxis.fr/)) — reference vs explanation register ·
Nielsen Norman Group, [*Progressive Disclosure*](https://www.nngroup.com/articles/progressive-disclosure/)
(Nielsen 1995) · Kluger & DeNisi (1996), Psychological Bulletin 119(2):254–284 ·
Hattie & Timperley (2007) feed-up/feed-back/feed-forward · CodeQL
[query-help files](https://codeql.github.com/docs/writing-codeql-queries/query-help-files/).

---

## 4. What was removed and why

- **`practice.category`** — removed. Static classification was redundant with the configurable
  `PracticeArea` roll-up (SARIF-`taxon`-style grouping). A practice's grouping is now its *area*, not a
  frozen category.
- **`Practice.kind` / `PracticeKind` (and `CONTEXTUAL`) and `subject_role` / `SubjectRole`** — dropped
  entirely. Direction is no longer a rule column: it is recomputed per observation as `assessment = BAD`,
  and whose conduct an observation is about lives in `about_user_id` rather than a `subject_role` enum.
  `kind` was transient migration scaffolding — an intermediate column that never ships.
- **The observation `observer` column** — dropped (always `SYSTEM`, zero readers); likewise transient
  scaffolding that never ships.
- **The `FeedbackPost` subsystem** — removed entirely (zero references remain in `server/src/main/java`).
  It was an earlier delivery ledger superseded by the `Feedback` + `FeedbackObservation` + `FeedbackPlacement`
  triad of ADR 0021: `Feedback` owns the rendered unit and lifecycle, `FeedbackPlacement` owns the
  physical posting/reconciliation, `FeedbackObservation` owns the composition. Keeping a parallel ledger with
  zero writers was dead weight and a second source of truth.
- **The `null ⇒ developer` / `null ⇒ recipient` subject fallbacks** — removed on **both** sides.
  `Observation.aboutUserId` and `Feedback.aboutUserId` are now `NOT NULL` and always explicitly
  populated (the feedback side backfilled `about_user_id = recipient_user_id`). Every reader can trust
  the column without a fallback, and reviewer-side observations/feedback (subject ≠ developer/recipient) are
  representable. The two sides are now symmetric — both match xAPI's requirement that the Actor (the agent
  a statement is about) be mandatory and unambiguous ([xAPI](https://xapi.com/statements-101/)).
- **Cut delivery columns** — `Feedback.idempotency_key` (a duplicate of the `(agent_job_id, position)`
  unique), `model_id`, `composer_version` (write-only), and `synthesis_prompt_version` (dead) are removed;
  so are the never-written placement columns (`anchor_old_path`, `anchor_start_side`, `anchor_quote`,
  `pinned_commit_sha`) and the written-but-unread `posted_state`, `thread_external_ref`, and `resolved*`.
- **Dead anchor/location columns on observations** — never introduced: location lives in the `evidence`
  JSONB, not as top-level columns, because many practices have no file location and observations can be
  multi-location (mirrors SARIF `result.locations` being an array).
- **`baseline_state` as a column** — intentionally absent on `Feedback`; derived on read from the
  supersession chain (the SARIF concept kept implicit — see §6).

---

## 5. Naming consistency — intentional keeps

The grouping bucket is named **area** throughout (code, schema, API, UI) — one word per concept across
the bounded context, model to UI (DDD *Ubiquitous Language*:
[Fowler](https://martinfowler.com/bliki/UbiquitousLanguage.html); Evans, *Domain-Driven Design*).

A few identifiers intentionally retain an older spelling:

- The entity is `Observation` (table `observation`); its outcome is the two columns `presence` and
  `assessment`. There is no longer a single `observation` outcome column — that signed enum was split
  (§2, §3.3).
- The index **names** deliberately keep the older correlation/continuity spelling even though their
  columns were renamed: `idx_observation_correlation` and `idx_reaction_correlation` index
  `recurrence_key`, and `idx_feedback_continuity` indexes `thread_key`.

---

## 6. Standards-divergence design decisions

This section records where the schema deliberately diverges from SARIF and the learning-analytics
standards, and why.

The schema makes three larger decisions worth calling out:

- **Practice criteria versioning (SCD-2)** — `PracticeRevision` / `practice_revision` (§3.8) +
  `Observation.practice_revision_id` (§3.3). `PracticeService` appends revision 1 on create and a new
  revision whenever `criteria` actually changes (value-compared); `Practice.criteria` stays the current
  projection. Each observation pins to the criteria-as-it-was, so *which criteria version fired this observation*
  is queryable.
- **Developer-facing layer + physical anti-leak projection** — `Practice.whyItMatters` /
  `whatGoodLooksLike` (§3.1, seeded for the default practices, editable in the admin form, guarded against
  detector vocabulary) are served through `LearnerPracticeDTO` / `GET /practices/learner`, which
  carries no `criteria` field **by construction** (§3a): "criteria never reaches a learner" is a physical
  guarantee, asserted by an integration test on the raw learner JSON.
- **`Feedback.aboutUserId` NOT NULL** (§3.5, §4) — set equal to `recipient_user_id`, closing the
  asymmetry with `Observation.aboutUserId` (xAPI mandatory, unambiguous Actor on both sides).

**Documented design decisions (deliberate keeps — do not "fix"):**

- **`baseline_state` stays derived, not stored.** SARIF stores `result.baselineState`
  (`new`/`unchanged`/`updated`/`absent`) as a first-class field; we compute the equivalent on read from
  the supersession chain. Storing it would *duplicate* state the chain already encodes (a new row with no
  `replaces_id` is "new"; one with `replaces_id` is "updated"; an unreplaced prior is "superseded"),
  so a stored column would be a second source of truth that could drift from the chain. We accept the one
  cost — a consumer wanting "only NEW observations this run" walks the chain rather than filtering a column
  ([SARIF #615](https://github.com/oasis-tcs/sarif-spec/issues/615)).
- **Per-occurrence id *and* cross-run recurrence key — both grains are present.** SARIF separates a per-run
  assigned `guid`/`correlationGuid` (a stable id assigned on ingest) from `partialFingerprints` (the
  heuristic recurrence bucket). We already have **both**: `Observation.id` is a per-occurrence UUID
  (the assigned-id grain), and `recurrence_key` is the cross-run recurrence key (the
  `partialFingerprints` grain). Nothing is folded away or missing — the two SARIF grains map onto two
  existing columns.
- **`recurrence_key` is a *locus* hash, by design.** It hashes (practice, artifact, subject,
  file-path) — the **locus** the observation is about — deliberately **not** a line number or content/title
  hash. That keeps it stable across line moves and LLM re-wording of the title, which is exactly what a
  recurrence key must do. Cross-file-rename stability is **out of
  scope on purpose**: a moved locus is a *different* locus, and conflating the two would mis-merge
  unrelated observations. This is the SARIF `partialFingerprints` philosophy (stable against churn), applied
  to our domain.
- **No first-class direction in any standard — our `presence` × `assessment` split is the extension.**
  Splitting measurement (`presence`) from valence (`assessment`) has no analogue in SARIF, SonarQube, or
  code-scanning — all assume a rule fires only on something wrong, baking direction into the result. The
  two-axis design is defensible and lossless on SARIF export (the mapping in §2), but it is *our* extension;
  interoperability tooling needs that export mapping to collapse `(presence, assessment)` back onto
  `result.kind`.
- **`confidence` (0.0–1.0) kept distinct from SARIF `result.rank` (-1.0–1.0).** The two are different
  scales with different semantics; an export chooses one rather than pretending they are the same number.

### Honest caveats (state, don't hide)

- Observations detected **before** versioning shipped pin to NULL criteria-version (an honest "pre-versioning"
  marker — those early observations are not reproducible against an exact rubric snapshot). Every observation from
  the migration forward pins to a concrete revision.
- **Affective dismissal is currently unmeasured** (absorbed as silence / absent row) until a UI affordance
  can elicit an explicit reject-without-reason act distinguishable from never-reacting.
- The **"exposed-rubric gaming" claim is principled, not empirically studied** — it rests on
  construct-validity reasoning (Goodhart) + Kluger & DeNisi (1996), not on a study of learners gaming an
  AI-detection rubric specifically. Flagged as a named gap.

---

*Standards cited:* SARIF v2.1.0 (OASIS) ·
[spec](https://docs.oasis-open.org/sarif/sarif/v2.1.0/csprd01/sarif-v2.1.0-csprd01.html) ·
[fingerprints #615](https://github.com/oasis-tcs/sarif-spec/issues/615) ·
SonarQube clean-code [software qualities](https://docs.sonarsource.com/sonarqube-server/10.8/core-concepts/clean-code/software-qualities) ·
GitHub code-scanning alert lifecycle [discussion](https://github.com/orgs/community/discussions/9175) ·
xAPI (ADL) [Statements 101](https://xapi.com/statements-101/) · IMS [xAPI/Caliper comparison](https://www.imsglobal.org/initial-xapicaliper-comparison) ·
DDD Ubiquitous Language [Fowler](https://martinfowler.com/bliki/UbiquitousLanguage.html) (Evans, *Domain-Driven Design*).
