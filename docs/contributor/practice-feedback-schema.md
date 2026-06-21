# Practice & Feedback Schema — Reference

> Status: living reference for the `practices` Spring Modulith module (entities under
> `server/src/main/java/de/tum/cit/aet/hephaestus/practices/**`). Grounded in ADR 0021
> (`docs/decisions/0021-findings-feedback-synthesis-seam.md`). Uses the **canonical vocabulary**
> throughout — *area*, never *goal* — one word per concept across code, schema, API, and UI.

---

## 1. Orientation

This schema records, for every contribution a developer makes, **what software-engineering
practices an AI agent observed** and **what feedback a student actually saw**. It answers a
research question of the shape *"do good engineering practices appear, change over time, and
respond to targeted feedback?"* To answer that honestly the schema separates three things that
naive review tools fuse: the **raw observation** (a `PracticeFinding`, audience-neutral, append-only,
deduplicated by a stable cross-run fingerprint), the **synthesised delivery** of one or more of those
observations to one person (a `Feedback` unit, with its own provenance and delivery lifecycle), and
the **rule metadata** that gives an observation meaning (a `Practice`, carrying polarity, subject
role, and trigger events, optionally rolled up into a `PracticeArea` learning bucket). Everything is
append-only so the temporal record of *what a student was shown, and how they reacted* survives
re-runs and supersession — the substrate the longitudinal research question depends on.

The vocabulary is deliberately aligned with finding-interchange standards (SARIF, SonarQube,
GitHub code-scanning) where one exists, and with learning-analytics standards (xAPI, Caliper) for the
actor/recipient distinction those finding standards lack — and it diverges from them only where the
divergence is load-bearing (sign-neutral `Observation` × `Polarity`). The naming choices below are
*grounded*, not asserted: each is justified against the standard it tracks or the reason it departs.

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
| a single observed enactment | **`PracticeFinding`** | the situated, observable trace |
| feedback synthesised for a person | **`Feedback`** | task-framed feed-up / feed-back / feed-forward |

---

## 2. Ubiquitous language — one canonical name per concept

This is the **single source of truth for naming**. Each concept has exactly one canonical name; the
"Legacy term it replaced" column names the spelling that must no longer appear in new code. Per the
DDD *Ubiquitous Language* principle, model, code, schema, API and UI must share one word per concept —
"a language structured around the domain model and used by all team members within a bounded context"
([Fowler, *UbiquitousLanguage*](https://martinfowler.com/bliki/UbiquitousLanguage.html); Evans, *DDD*).
Synonyms are a defect, not a convenience.

| Concept (one canonical name) | Canonical term | Legacy term it replaced | Why the rename |
| --- | --- | --- | --- |
| Grouping bucket over practices | **PracticeArea** / `area` | `PracticeGoal` / `goal` | "goal" implies a *target state* to reach; the entity is a neutral grouping bucket (a SARIF *taxon*), not an objective — see §2 grounding below |
| Sign-neutral presence verdict | **Observation** (`OBSERVED` / `NOT_OBSERVED` / `NOT_APPLICABLE`) | `Verdict` (the *type*; the field/column `verdict` is kept) | "verdict" implied a baked-in good/bad judgement; the value is now sign-free, direction lives on `Polarity` |
| The person whose work is evaluated | **developer** | `contributor` | one word for the SCM author across the module |
| The kind of work reviewed | **WorkArtifact** / `artifact_type` | `focus_artifact`, `target` | names the *work* (PR/issue), not a build artifact nor a vendor object |
| Cross-run finding identity | **finding_fingerprint** (`FindingFingerprint`) | `correlation_key` | "fingerprint" states the hash purpose (SARIF `partialFingerprints`); "correlation_key" was opaque |
| Cross-run feedback continuity | **feedback_thread_key** (`FeedbackThreadKey`) | `continuity_key` | names *what it threads* (successive deliveries), domain-readable |
| Delivery destination class | **FeedbackChannel** / `surface` (column) | `surface` (as the *concept* word) | the concept is "channel"; `surface` survives only as the column name |
| One physical render of a unit | **FeedbackPlacement** / **PlacementSlot** | `placement` (as a slot word) | the slot enum is `PlacementSlot`; the row is the `FeedbackPlacement` |
| Finding's weight inside a unit | **EvidenceRole** (`PRIMARY` / `SUPPORTING`) | `display_role` | it weights *evidence*, not display |
| Whose conduct a practice judges | **SubjectRole** (`AUTHOR` / `REVIEWER`) | `audience_role`, `AUDIENCE_REVIEWER` | it is the *subject's* role; `AUDIENCE_REVIEWER` collapsed to `REVIEWER` (the side is `REVIEWER_SIDE`) |
| Context-dependent polarity value | **CONTEXTUAL** | `MIXED` | "contextual" states *why* the direction is unfixed (context decides), not that it is merely mixed |

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
- **Observation × Polarity (deliberate divergence from SARIF `kind`).** SARIF `result.kind`
  (`pass`/`fail`/`notApplicable`) bakes the good/bad direction into the verdict, so a rule can only ever
  "fail". We split that into sign-free `Observation` (`OBSERVED`/`NOT_OBSERVED`/`NOT_APPLICABLE`) × `Polarity`
  (direction, on the *Practice*). This is a defensible refinement, and lossless on SARIF export:
  `(DESIRABLE,OBSERVED)→pass`, `(DESIRABLE,NOT_OBSERVED)→fail`, `(UNDESIRABLE,OBSERVED)→fail`,
  `(UNDESIRABLE,NOT_OBSERVED)→pass`, `NOT_APPLICABLE→notApplicable`. `NOT_APPLICABLE` is a verbatim match
  for SARIF `notApplicable`. Polarity is attached to the *Practice* (rule metadata), mirroring how SARIF
  attaches `defaultConfiguration` to the `reportingDescriptor` rather than to each result.
- **finding_fingerprint.** Directly analogous to SARIF `partialFingerprints` + `result.baselineState`
  (`new`/`unchanged`/`updated`/`absent`), the mechanism by which a Results-Management System decides "is
  this the *same* finding as last run?" ([SARIF issue #615](https://github.com/oasis-tcs/sarif-spec/issues/615)).
  Excluding job-id, line number and title from the digest mirrors the SARIF guidance that fingerprints be
  stable against churn.

---

## 3. Per-entity reference

### 3.1 `Practice` — table `practice`

Workspace-scoped rule definition. The `artifact_type` / `polarity` / `subject_role` triple is what lets
one detector schema serve PR and issue work, desirable and undesirable behaviour, and author- and
reviewer-side conduct without per-case branching.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | Long | `id` | no | Auto-generated PK (`IDENTITY`). | Surrogate key. |
| workspace | Workspace (`@ManyToOne`) | `workspace_id` | no | Owning workspace (FK `fk_practice_workspace`). | SQL-layer multi-tenancy (`core/tenancy`). |
| slug | String(64) | `slug` | no | Stable machine key, unique per workspace (`uk_practice_workspace_slug`). | Survives a `name` rename; the routing/catalog key. |
| name | String(128) | `name` | no | Display label. | Human-readable in dashboards/feedback. |
| artifactType | WorkArtifact | `artifact_type` | no (default `PULL_REQUEST`) | Routes trigger gate, context builder, `AgentJobType`/handler, and delivery surface. | Single pipeline discriminator; default keeps existing rows behaviour-preserving. SARIF has no analogue — it is a file format, not a pipeline router. |
| polarity | Polarity | `polarity` | no (default `DESIRABLE`) | Good/bad **direction** that `Observation` omits. | Promotes SARIF's implicit `pass`/`fail` direction to a first-class, orthogonal rule-metadata axis (ADR 0021, F-6); no mainstream standard models polarity explicitly. |
| subjectRole | SubjectRole | `subject_role` | no (default `AUTHOR`) | Whose conduct is judged; drives `subject_user_id` and delivery audience. | The firewall keeping reviewer-side lessons off the author. xAPI Actor semantics (the agent a statement is *about*) — [xAPI Statements 101](https://xapi.com/statements-101/). |
| **area** | PracticeArea (`@ManyToOne`) | `practice_area_id` | yes | Optional roll-up bucket (NULL = ungrouped); 1:N. FK `fk_practice_area`, index `idx_practice_practice_area`. | Single owning bucket keeps per-area progress denominator unambiguous. SARIF `taxa`-style grouping. |
| triggerEvents | JsonNode (jsonb) | `trigger_events` | no | Which domain events activate detection. | JSONB keeps the event set open without schema churn. |
| criteria | String (TEXT) | `criteria` | no | NL spec passed to the detection agent. | The rule body the LLM evaluates against. Detector/admin **reference** register — never delivered to a learner (§3a). |
| whyItMatters | String (TEXT) | `why_it_matters` | yes | Admin-authored learner-facing *explanation* — why this practice matters. Seeded for all 32 default practices; editable in the practices admin form. | Developer-facing Layer 1 (Nicol & Macfarlane-Dick 2006 P1 feed-up; Diátaxis *explanation*). Surfaced via `LearnerPracticeDTO` (§3a), never the detector. |
| whatGoodLooksLike | String (TEXT) | `what_good_looks_like` | yes | Admin-authored learner-facing **exemplar** — what good looks like. Seeded for all 32 default practices; an authoring guard rejects detector verdict vocabulary (`OBSERVED`/`NOT_OBSERVED`/`NOT_APPLICABLE`) in this field. | Developer-facing Layer 2 (Sadler 1989 exemplar; Hattie feed-forward). The guard keeps the rubric-leak/Goodhart vector physically closed. |
| precomputeScript | String (TEXT) | `precompute_script` | yes | Optional Bun/TS static-analysis producing *hints, not verdicts*. | Narrows the agent search space; hints never become verdicts (provenance-admission contract). |
| active | boolean | `is_active` | no (default true) | Soft delete / feature flag for detection. | Toggle without losing history. |
| createdAt | Instant | `created_at` | no (immutable) | Insert timestamp. | Audit trail. |
| updatedAt | Instant | `updated_at` | yes | Last-update timestamp. | Audit trail. |

### 3.2 `PracticeArea` — table `practice_area`

Workspace-scoped **read-model / organising** concept that groups practices into a configurable learning
bucket. A practice belongs to at most one area; the 1:N binding is load-bearing for the per-area
acted-on/total progress denominator. An area never enters `trigger_events`, `criteria`, the detector,
or the finding schema — practices remain the unit of detection.

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

### 3.3 `PracticeFinding` — table `practice_finding`

Immutable, append-only, deduplicated record of one practice evaluation. **One-to-one structural
analogue of a SARIF `result`** ([SARIF v2.1.0](https://docs.oasis-open.org/sarif/sarif/v2.1.0/csprd01/sarif-v2.1.0-csprd01.html)):
it references a rule (`practice` ≈ SARIF `ruleId`), carries a message (`title`), `reasoning`, structured
`evidence` (≈ `result.locations` + `relatedLocations` + `codeFlows`), a `severity` (≈ `result.level`), and
a stable cross-run identity (`finding_fingerprint` ≈ `partialFingerprints`). "Finding" is also the
GitHub code-scanning term; our deduped-across-runs notion (their "alert" grain) lives in
`finding_fingerprint`.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | UUID | `id` | no | PK; assigned in `@PrePersist` if null. `insertIfAbsent` bypasses it and requires explicit UUID. | Immutable identifier. |
| idempotencyKey | String(255) | `idempotency_key` | no | Dedup key, unique (`uk_practice_finding_idempotency`). | Race-safe `insertIfAbsent` upsert; same finding cannot double-insert on re-run. |
| agentJobId | UUID | `agent_job_id` | no | Producing job (FK `fk_practice_finding_agent_job`, Liquibase-managed). Raw UUID, not `@ManyToOne`. | Avoids a Modulith cycle into the `agent` module; cascade-delete with the job. |
| practice | Practice (`@ManyToOne`) | `practice_id` | no | Evaluated practice; DB `ON DELETE CASCADE`. | Deleting a practice cleans its immutable findings without lifecycle callbacks. |
| practiceRevision | PracticeRevision (`@ManyToOne`) | `practice_revision_id` | yes | Pins the finding to the `criteria`-as-it-was when the detector evaluated it; FK `fk_practice_finding_revision`, DB `ON DELETE SET NULL`. The delivery service looks up the current revision per practice and writes it on the native insert. | Reproducibility: *which criteria version fired this finding* is queryable (SCD-2 point-in-time). NULL marks pre-versioning findings honestly (deleting a revision detaches without losing the finding). See §3.8. |
| artifactType | WorkArtifact | `artifact_type` | no | PR vs ISSUE the finding targets. | Routes finding to the correct dashboard/channel. |
| artifactId | Long | `artifact_id` | no | External id of the target PR/issue. | Links to the specific artifact. |
| developer | User (`@ManyToOne`) | `developer_id` | no | The contribution author being evaluated; FK `RESTRICT` (no cascade). | Findings outlive users; deleting a user with findings is blocked. |
| subjectUserId | Long | `subject_user_id` | **no (ALWAYS populated)** | Whose conduct the finding is *about*: equals `developer` for author-side, the reviewer for reviewer-side. Raw Long FK `fk_practice_finding_subject`. | xAPI Actor (the agent a statement is about) is mandatory and unambiguous — [xAPI](https://xapi.com/statements-101/). The former *null⇒developer* fallback was collapsed to an explicit value (§4). |
| findingFingerprint | String(64) | `finding_fingerprint` | yes | Deterministic hash of what the finding is **about** (practice + target + subject + content anchor), never of *when* it was produced. | Enables supersession and reaction-continuity across re-detections. SARIF `partialFingerprints` — title excluded because the LLM re-words it every run (proven 0/26 correlation). Nullable: backfill-free, new findings only. |
| title | String(255) | `title` | no | Short headline. | SARIF `result.message`. |
| **verdict** | Observation | `verdict` | no | Sign-neutral presence verdict. | Column name `verdict` intentionally kept; only the *type* became `Observation` (§5 — legitimate keep). SARIF `result.kind`. |
| severity | Severity | `severity` | no | Impact level, orthogonal to verdict. | SARIF `kind ⟂ level`; SonarQube blocker..info ladder. |
| confidence | Float | `confidence` | no | Agent confidence 0.0–1.0. | Delivery filtering + quality; ≈ SARIF `result.rank` (diagnostic relevance). |
| evidence | JsonNode (jsonb) | `evidence` | yes | `{locations:[{path,startLine,endLine}], snippets:[…], references:[…]}`. | Location is JSON, not columns, because many practices have no file location and findings can be multi-location. ≈ SARIF `result.locations`. |
| reasoning | String (TEXT) | `reasoning` | yes | Agent's rationale. | Quality review + developer education. |
| guidance | String (TEXT) | `guidance` | yes | Actionable remediation guidance. | Synthesised into `Feedback`. |
| detectedAt | Instant | `detected_at` | no | Detection timestamp; `@PrePersist` if null. | Temporal ordering for trends. |

### 3.4 `FindingReaction` — table `finding_reaction`

Immutable, append-only record of a developer's reaction to a finding. **Explicitly excluded from agent
context (#895)** so prior disputes never contaminate detection accuracy. The latest row per
`(finding, developer)` is the current state.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | UUID | `id` | no | PK; `@PrePersist` if null. | Immutable identifier. |
| finding | PracticeFinding (`@ManyToOne`) | `finding_id` | no | Reacted finding; DB `ON DELETE CASCADE`. | Cleans reactions when a finding is deleted. |
| findingId | UUID | `finding_id` | no (read-only mirror) | Scalar access without lazy-loading the proxy. | Avoids a proxy hit in hot reads. |
| findingFingerprint | String(64) | `finding_fingerprint` | yes | Denormalised copy of the finding's fingerprint at reaction-write time. | The reacted finding is ephemeral (new row each run); the fingerprint is the stable locus that recurs, letting B2 suppression find a prior DISPUTED / NOT_APPLICABLE reaction. Index `idx_finding_reaction_correlation`. |
| developer | User (`@ManyToOne`) | `developer_id` | no | Reacting developer; FK `RESTRICT`. | Reactions outlive users. |
| developerId | Long | `developer_id` | no (read-only mirror) | Scalar access without lazy load. | Hot-read optimisation. |
| action | FindingReactionAction | `action` | no | `ENACTED` / `DISPUTED` (RESPONSE axis) / `NOT_APPLICABLE` (VALIDITY axis). | Research signal (RQ1/RQ2/RQ4), not workflow. The value set is a *recipience act* (see note below). |
| explanation | String (TEXT) | `explanation` | yes | Free-text rationale (required for `DISPUTED`; the required explanation *is* the evaluative judgement). | Qualitative dispute signal. |
| createdAt | Instant | `created_at` | no (immutable) | When the reaction was submitted. | Temporal "changed my mind" record. |

> **`FindingReactionAction` partitions into two orthogonal sub-axes** (recipience theory).
> A `FindingReaction` is the learner's answer to Lipnevich & Smith's (2022) third question — *"What
> am I going to do with the feedback?"* — i.e. a **behavioural recipience act** (Winstone et al.
> 2017, "enacting"). The values therefore split:
> - **RESPONSE sub-axis `{ENACTED, DISPUTED}`** — what the learner *did* about a finding they accept
>   is about them. `ENACTED` (renamed from `APPLIED`) records that the learner *acted to close the
>   gap* — a proxy inferred from a later artifact/self-report; it **does NOT assert the gap closed
>   correctly** (Winstone et al. 2017; Sadler 1989, feedback "used to alter the gap"). The escalation
>   branch in `ReactionSuppressionFilter` re-nags an `ENACTED`-but-still-`NOT_OBSERVED` locus, which is
>   only sound *because* the value claims action, not verified closure. `DISPUTED` is narrowed to the
>   *reasoned* rejection — the learner reason-rejects the assessment, and the enforced `explanation`
>   is the evaluative judgement (Carless & Boud 2018, "making judgements"). Affective *dismissal*
>   (reject-without-reason) is currently absorbed as silence (an absent row) pending a UI affordance —
>   a `DISMISSED` value is deliberately deferred (see §6).
> - **VALIDITY sub-axis `{NOT_APPLICABLE}`** — *"is this finding valid/relevant here?"* is a
>   finding-validity judgement, not a recipience act (Lipnevich & Smith 2022, Q3). The engagement DTO
>   (`FindingReactionEngagementDTO`) therefore **excludes `NOT_APPLICABLE` from every uptake / non-uptake
>   ratio** and reports it separately as a validity/scope signal on the developer's own reflection view
>   (Nicol & Macfarlane-Dick 2006, principle 7). The value stays in the enum for migration safety and
>   keeps its suppression co-location (do not re-nag a credibly scoped-out locus).
>
> *Sources:* Winstone, Nash, Parker & Rowntree (2017), *Supporting Learners' Agentic Engagement With
> Feedback*, Educational Psychologist 52(1):17–37 ·
> [link](https://www.tandfonline.com/doi/abs/10.1080/00461520.2016.1207538) · Carless & Boud (2018),
> *The development of student feedback literacy*, Assessment & Evaluation in HE 43(8):1315–1325 ·
> [link](https://www.tandfonline.com/doi/full/10.1080/02602938.2018.1463354) · Lipnevich & Smith
> (2022), *Student–Feedback Interaction Model: Revised*, Studies in Educational Evaluation 75:101208 ·
> [link](https://www.sciencedirect.com/science/article/abs/pii/S0191491X22000852) ·
> Nicol & Macfarlane-Dick (2006), Studies in Higher Education 31(2):199–218.

### 3.5 `Feedback` — table `feedback`

Immutable, append-only synthesised **delivery** unit for a single recipient — the developer-facing
rendering of one or more findings, with rendered body, channel, provenance,
and delivery lifecycle. A re-run inserts a new row (deduped by `idempotency_key`) and points
`supersedes_id` at the prior one rather than mutating it, so the record of what a student actually saw is
preserved. `baseline_state` is intentionally **not** a column — derived on read from the supersession
chain.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | UUID | `id` | no | PK; `@PrePersist` if null. | Immutable identifier. |
| idempotencyKey | String(255) | `idempotency_key` | no | Derived from `agent_job_id + unit_ordinal`; unique (`uk_feedback_idempotency`). | Re-emitted job cannot double-insert a unit. |
| agentJobId | UUID | `agent_job_id` | no | Producing job (FK `fk_feedback_agent_job`, Liquibase). Raw UUID. | Avoids a cycle into `agent`; cascade-delete. |
| workspaceId | Long | `workspace_id` | no | Owning workspace (FK `fk_feedback_workspace`, Liquibase). Raw Long. | Avoids a cycle into `workspace`; purge removes feedback explicitly. |
| artifactType | WorkArtifact | `artifact_type` | yes | Kind of artifact this is about. | Nullable: reflection-dashboard feedback is not anchored to one artifact. |
| artifactId | Long | `artifact_id` | yes | External id of the target. | Nullable in lockstep with `artifactType`. |
| recipientUserId | Long | `recipient_user_id` | no | The user this is delivered **to** (FK `fk_feedback_recipient`). Raw Long. | Messaging "To"-recipient; distinct from subject. ≈ xAPI Authority / audience — [xAPI/Caliper comparison](https://www.imsglobal.org/initial-xapicaliper-comparison). |
| subjectUserId | Long | `subject_user_id` | **no (ALWAYS populated)** | The user this is **about**: equals `recipientUserId` for author-facing units, the subject (e.g. the reviewer) when ≠ recipient (reviewer-side feedback). | xAPI Actor (mandatory, unambiguous). NOT NULL since the symmetry migration backfilled `subject_user_id = recipient_user_id`, matching `PracticeFinding.subjectUserId` — no delivery-row fallback remains (§4). |
| surface | FeedbackChannel | `surface` | no | Destination class (in-context / conversation / reflection). | Decouples "what we say" from "where it lands". Column name `surface`; concept word is **channel**. |
| unitOrdinal | Integer | `unit_ordinal` | no | 0-based position within the producing job's output. | Idempotency-key component + stable delivery order. |
| deliveryState | FeedbackDeliveryState | `delivery_state` | no | Lifecycle: prepared → delivered / superseded / suppressed / failed. | Conventional delivery state machine; SUPPRESSED ≈ SARIF `result.suppressions`. |
| suppressionReason | FeedbackSuppressionReason | `suppression_reason` | yes | Why a unit was withheld (set only when SUPPRESSED). | ≈ SARIF `suppression.justification`. |
| renderedBody | String (TEXT) | `rendered_body` | yes | Final student-facing body. | Null while PREPARED or when suppressed. |
| origin | FeedbackOrigin | `origin` | no | `AGENT` / `POLICY_FLOOR` / `FALLBACK`. | Provenance: policy/fallback units must not be scored as model output. |
| modelId | String(255) | `model_id` | yes | Synthesiser model id. | Provenance; null for non-agent origins. |
| composerVersion | String(255) | `composer_version` | yes | Delivery-composer version. | Render-template provenance. |
| synthesisPromptVersion | String(255) | `synthesis_prompt_version` | yes | Synthesis prompt version. | Provenance; null for non-agent origins. |
| supersedesId | UUID | `supersedes_id` | yes | Self-FK to the prior row this replaces (`fk_feedback_supersedes`). | Re-delivery without duplication; null for first delivery. ≈ SARIF `baselineState=updated`. |
| feedbackThreadKey | String(64) | `feedback_thread_key` | yes | Cross-run continuity tying successive deliveries of "the same" feedback, independent of job. Indexed (`idx_feedback_continuity`). | Conversation continuity across runs. |
| createdAt | Instant | `created_at` | no (immutable) | Insert timestamp; `@PrePersist`. | Audit + temporal ordering. |
| deliveredAt | Instant | `delivered_at` | yes | When actually delivered. | Null while PREPARED, suppressed, or failed. |

### 3.6 `FeedbackFinding` — table `feedback_finding`

Immutable many-to-many join binding a `Feedback` unit to the `PracticeFinding`s it was composed from.
Both sides live in the `practices` module, so real `@ManyToOne` associations are used. Composite PK
`(feedback_id, finding_id)` makes the binding idempotent.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | Id (embedded) | `(feedback_id, finding_id)` | no | Composite PK. | Idempotent binding; written via `insertIfAbsent`. |
| feedback | Feedback (`@ManyToOne`, `@MapsId`) | `feedback_id` | no | Composed unit; DB `ON DELETE CASCADE`. | Read-only mirror of the key. |
| finding | PracticeFinding (`@ManyToOne`, `@MapsId`) | `finding_id` | no | Bound evidence; DB `ON DELETE CASCADE`. | A finding can be reused across units. |
| **evidenceRole** | EvidenceRole | `evidence_role` | no | `PRIMARY` (anchors the headline) / `SUPPORTING` (corroborates). | One unit can fuse a PRIMARY plus SUPPORTING findings. (Renamed from `display_role`.) |
| ordinal | Integer | `ordinal` | no | Stable ordering within the unit (lower = earlier). | Deterministic narrative order. |

### 3.7 `FeedbackPlacement` — table `feedback_placement`

Immutable record of one concrete placement of a `Feedback` unit on a delivery surface — a summary block,
an inline diff anchor, or a conversation turn. 1:N from `Feedback`. Carries full diff-anchor coordinates
and per-anchor posting lifecycle so re-delivery, snapping and resolution reconcile per anchor.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | UUID | `id` | no | PK; `@PrePersist` if null. | Immutable identifier. |
| feedback | Feedback (`@ManyToOne`) | `feedback_id` | no | Parent unit; DB `ON DELETE CASCADE` (FK `fk_feedback_placement_feedback`). | Cleans placements with the unit. |
| feedbackId | UUID | `feedback_id` | no (read-only mirror) | Scalar id without lazy load. | Hot-read optimisation. |
| slot | PlacementSlot | `slot` | no | `SUMMARY` / `INLINE` / `CONVERSATION_TURN`. | Where it renders. |
| anchorKind | PlacementAnchorKind | `anchor_kind` | yes | `LINE` / `RANGE` / `FILE` / `IMAGE`. | Only INLINE placements anchor to a diff. |
| anchorPath | String (TEXT) | `anchor_path` | yes | Head-side file path. | Inline anchor. |
| anchorOldPath | String (TEXT) | `anchor_old_path` | yes | Base-side path (set on rename). | Rename-safe anchoring. |
| anchorStartLine | Integer | `anchor_start_line` | yes | First anchored line (1-based). | Inline anchor. |
| anchorEndLine | Integer | `anchor_end_line` | yes | Last anchored line. | Equals start for single line. |
| anchorSide | PlacementAnchorSide | `anchor_side` | yes | `OLD` / `NEW` for the end/single line. | Unified-diff side. |
| anchorStartSide | PlacementAnchorSide | `anchor_start_side` | yes | Side of the range start. | Multi-side ranges. |
| anchorQuote | String (TEXT) | `anchor_quote` | yes | Quoted source the anchor refers to. | Content anchor; survives line drift. |
| pinnedCommitSha | String(64) | `pinned_commit_sha` | yes | Commit the anchor was pinned to. | Reproducible anchoring. |
| externalRef | String (TEXT) | `external_ref` | yes | External comment/note id (indexed). | Reconciliation; 1 per placement. |
| threadExternalRef | String (TEXT) | `thread_external_ref` | yes | External thread/discussion id. | Threaded reconciliation. |
| resolved | Boolean | `resolved` | yes | Whether the thread/note is resolved on the surface. | Resolution tracking. |
| resolvedAt | Instant | `resolved_at` | yes | When resolved. | Temporal resolution. |
| resolvedExternalRef | String (TEXT) | `resolved_external_ref` | yes | Resolving action id, if distinct. | Audit of resolution. |
| postedState | PlacementPostedState | `posted_state` | no | Posting lifecycle vs the external surface. | Per-anchor reconciliation. |
| createdAt | Instant | `created_at` | no (immutable) | Insert timestamp. | Audit. |

### 3.8 `PracticeRevision` — table `practice_revision`

Append-only **slowly-changing-dimension (SCD Type 2)** history of a practice's `criteria` text. Every
time the `criteria` actually changes (value-compared), `PracticeService` appends a new revision; revision
1 is written on practice create. `Practice.criteria` stays the **current projection** (no read path
breaks), while `practice_revision` records every prior wording, so the recursive ostensive↔performative
loop — admins reshaping the criteria over time in response to what they see enacted — is *recorded,
not overwritten* (D'Adderio 2011 translation loop; the qualitative-coding codebook audit-trail / IRR
norm; data-warehousing SCD-2). `PracticeFinding.practice_revision_id` (§3.3) pins each finding to the
revision in force when it was detected, making *which criteria version fired this finding* queryable.

| Field | Type | Column | Nullable | Description | Justification |
| --- | --- | --- | --- | --- | --- |
| id | Long | `id` | no | Auto-generated PK. | Surrogate key. |
| practice | Practice (`@ManyToOne`) | `practice_id` | no | Owning practice; FK `fk_practice_revision_practice`, DB `ON DELETE CASCADE`. | Revisions are part of the practice's lifecycle; deleting the practice removes its history. |
| revisionNumber | int | `revision_number` | no | Monotonic per-practice revision counter; revision 1 on create, `+1` on each criteria change. Unique with `practice_id` (`uk_practice_revision_practice_number`). | Point-in-time ordering; the cross-finding identity of a criteria version. |
| criteria | String (TEXT) | `criteria` | no | Snapshot of the `criteria` text as of this revision. | The audit-trail record of the in-force rubric wording. |
| createdAt | Instant | `created_at` | no (immutable) | When this revision was written. | Temporal reconstruction. |

> A backfill changeset creates revision 1 for every practice that existed before versioning shipped, so
> the history is complete from the migration forward; findings detected before versioning pin to NULL
> (an honest "pre-versioning" marker, not a reproducible rubric snapshot).

### 3.9 Enumerations

| Enum | Values & meaning | Grounding |
| --- | --- | --- |
| **WorkArtifact** | `PULL_REQUEST` (code diff + commits + review thread, delivered in-PR), `ISSUE` (title, body, labels, assignees, comment thread, state-transition timeline — no diff). | Named for the *work*, not a tool; closed set grows in lockstep with a runtime that can build that artifact's context (design doc, chat thread). |
| **Polarity** | `DESIRABLE` (OBSERVED=strength, NOT_OBSERVED=gap), `UNDESIRABLE` (OBSERVED=problem, NOT_OBSERVED=clean), `CONTEXTUAL` (per-finding severity carries the direction). | Orthogonal direction axis SARIF lacks (ADR 0021, F-6). `CONTEXTUAL` is a genuine extension — replaces legacy `MIXED`. |
| **SubjectRole** | `AUTHOR` (subject == developer), `REVIEWER` (subject == reviewer, distinct from developer). | xAPI Actor. The firewall keeping reviewer-side lessons off the author. Replaces `audience_role`/`AUDIENCE_REVIEWER`. |
| **Observation** | `OBSERVED` (present), `NOT_OBSERVED` (absent where expected), `NOT_APPLICABLE` (practice irrelevant to the work). | Sign-neutral; ≈ SARIF `result.kind` with the direction factored out. `NOT_APPLICABLE` is a verbatim SARIF match. |
| **Severity** | `CRITICAL`, `MAJOR`, `MINOR`, `INFO` — impact, orthogonal to verdict. | SARIF `result.level` / SonarQube blocker..info. |
| **FindingReactionAction** | **RESPONSE axis:** `ENACTED` ("acted to close the gap"; outcome unverified — RQ2), `DISPUTED` ("AI is wrong", reasoned rejection, requires explanation — RQ1/RQ4). **VALIDITY axis:** `NOT_APPLICABLE` ("valid but irrelevant" — RQ4; excluded from uptake ratios). | Recipience act (Winstone 2017 "enacting"); not workflow. `ENACTED` renamed from `APPLIED` (claimed action ≠ verified closure). No `DISMISSED`/`ACKNOWLEDGED` — non-action = absence of a row; affective dismissal deferred behind a UI affordance (§6). |
| **EvidenceRole** | `PRIMARY` (anchors the headline), `SUPPORTING` (corroborates). | Synthesis-time weighting; replaces `display_role`. |
| **FeedbackChannel** | `IN_CONTEXT` (on the PR/issue), `CONVERSATION` (mentor turn), `REFLECTION_DASHBOARD` (recipient's private dashboard). | Decouples message from channel. Every channel is developer-facing. |
| **FeedbackDeliveryState** | `PREPARED`, `DELIVERED`, `SUPERSEDED` (replaced via `supersedes_id`), `SUPPRESSED` (withheld; see reason), `FAILED`. | Delivery state machine + review-tool edit-in-place (SUPERSEDED) + SARIF `suppressions` (SUPPRESSED). |
| **FeedbackSuppressionReason** | `REVIEWER_SIDE`, `BELOW_THRESHOLD`, `LOW_CONFIDENCE`, `POLICY_FLOOR_DROP`, `REACTED_DISPUTED` (subject DISPUTED this locus earlier — B2), `REACTED_NOT_APPLICABLE` (subject marked N/A earlier — B2). | ≈ SARIF `suppression.justification`. `REVIEWER_SIDE` replaces legacy `AUDIENCE_REVIEWER`. |
| **FeedbackOrigin** | `AGENT` (LLM), `POLICY_FLOOR` (deterministic guaranteed-coverage), `FALLBACK` (synthesis unavailable/failed). | Provenance for honest quality measurement. |
| **PlacementSlot** | `SUMMARY`, `INLINE`, `CONVERSATION_TURN`. | Where a placement renders. Replaces the `placement`-as-slot word. |
| **PlacementPostedState** | `PENDING`, `POSTED`, `SNAPPED` (anchor moved to nearest valid line), `FELL_BACK` (posted as summary/thread instead of inline), `OUTDATED` (diff line changed), `ORPHANED` (anchored code/thread gone), `GONE` (comment deleted out-of-band), `FAILED`. | GitHub/GitLab review-comment lifecycle. |
| **PlacementAnchorKind** | `LINE`, `RANGE`, `FILE`, `IMAGE`. | Diff-anchor granularity. |
| **PlacementAnchorSide** | `OLD` (left/base), `NEW` (right/head). | Unified-diff side. |

---

## 3a. Stakeholder display model (the projection contract)

The schema encodes two orthogonal axes that the display layer must keep separate:

- **Actor axis** = `Practice.subjectRole` (`AUTHOR`/`REVIEWER`) + the finding's `subject_user_id` — *who*
  the practice evaluates. Already encoded.
- **Audience axis** = *who reads the analytic*. The display model adds this. Every feedback unit is
  developer-facing — findings, verdicts and aggregates go to the developer, never to a mentor, instructor,
  or grader. The only non-developer reader is the researcher analysing **anonymised** study data and the
  workspace admin who edits the catalog (criteria, area labels); neither receives a developer's feedback.

**Field-by-audience matrix — enforce server-side, never in the webapp.** "Developer" and "Reviewer" are
the *same human*; the column that applies is selected by the **finding's `subjectRole`**, not a static
user role, so reviewer-craft never leaks to the author. The `whyItMatters` / `whatGoodLooksLike`
learner-layer columns are **implemented** — admin-authored `Practice` columns served to learners through
`LearnerPracticeDTO` (`GET /practices/learner`), which carries no `criteria` field by construction.

| Field | Developer / Learner | Reviewer (finding `subjectRole`=REVIEWER) | Researcher / Admin |
| --- | --- | --- | --- |
| `name` | yes | yes | yes |
| `whyItMatters` | yes — Layer 1 | yes | yes |
| `whatGoodLooksLike` | yes — Layer 2 (on request) | yes | yes |
| area / area progress | yes — own | yes — own | yes — anonymised |
| per-finding `Feedback` (task-framed) | yes — own only | yes — own only | yes — anonymised |
| **`criteria`** | **NEVER** | **NEVER** | yes — edit (admin) |
| `precomputeScript`, `triggerEvents`, `polarity`, `subjectRole` | no | no | yes — edit (admin) |
| raw `OBSERVED`/`NOT_OBSERVED` verdict label | no — delivered as task-framed feedback | no | yes — anonymised |
| reaction `NOT_APPLICABLE` (validity signal) | own — scope signal, **not** uptake | n/a | yes — anonymised |

**Two hard rules from theory (not UX taste):**

1. **`criteria` is NEVER delivered to a learner.** Three independent groundings: Diátaxis register
   mismatch (`criteria` is *reference* material for the detector/admin; a learner needs *explanation* +
   *how-to/example*); Kluger & DeNisi (1996) — a rubric+score frame directs attention to the self/standard
   and can *depress* performance; and Goodhart-style gaming of an exposed detection rubric. Omission is
   **physical**, not policy: `LearnerPracticeDTO` is a record with no `criteria` component, so the field
   cannot reach a learner even by accident (an integration test asserts the raw `GET /practices/learner`
   JSON contains no `"criteria"`) — mirroring how CodeQL physically separates the developer-facing
   `.qhelp` from the `.ql` query metadata. An authoring guard additionally rejects detector verdict
   vocabulary (`OBSERVED`/`NOT_OBSERVED`/`NOT_APPLICABLE`) in `whatGoodLooksLike`, keeping the rubric out
   of the learner copy at the source.
2. **Visibility keys off the *finding's* `subjectRole`, not a static user role.** Reviewer-craft feedback
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
  `PracticeArea` roll-up (SARIF-`taxon`-style grouping) and with `polarity`/`subject_role` for the
  semantics it had tried to carry. A practice's grouping is now its *area*, not a frozen category.
- **The `FeedbackPost` subsystem** — removed entirely (zero references remain in `server/src/main/java`).
  It was an earlier delivery ledger superseded by the `Feedback` + `FeedbackFinding` + `FeedbackPlacement`
  triad of ADR 0021: `Feedback` owns the rendered unit and lifecycle, `FeedbackPlacement` owns the
  physical posting/reconciliation, `FeedbackFinding` owns the composition. Keeping a parallel ledger with
  zero writers was dead weight and a second source of truth.
- **The `null ⇒ developer` / `null ⇒ recipient` subject fallbacks** — removed on **both** sides.
  `PracticeFinding.subjectUserId` and `Feedback.subjectUserId` are now `NOT NULL` and always explicitly
  populated (the feedback side backfilled `subject_user_id = recipient_user_id`). Every reader can trust
  the column without a fallback, and reviewer-side findings/feedback (subject ≠ developer/recipient) are
  representable. The two sides are now symmetric — both match xAPI's requirement that the Actor (the agent
  a statement is about) be mandatory and unambiguous ([xAPI](https://xapi.com/statements-101/)).
- **Legacy enum values** — `MIXED` → `CONTEXTUAL` (states *why* the direction is unfixed);
  `AUDIENCE_REVIEWER` → `REVIEWER` (the role) with `REVIEWER_SIDE` as the suppression reason. The old
  `Verdict` enum *type* became `Observation` (sign-neutral); the `verdict` *column/field name* is kept
  deliberately (the changelog never renamed it — a legitimate keep, not debt).
- **Dead anchor/location columns on findings** — never introduced: location lives in the `evidence`
  JSONB, not as top-level columns, because many practices have no file location and findings can be
  multi-location (mirrors SARIF `result.locations` being an array).
- **`baseline_state` as a column** — intentionally absent on `Feedback`; derived on read from the
  supersession chain (the SARIF concept kept implicit — see §6).

---

## 5. Naming consistency — intentional keeps

The grouping bucket is named **area** throughout (code, schema, API, UI) — one word per concept across
the bounded context, model to UI (DDD *Ubiquitous Language*:
[Fowler](https://martinfowler.com/bliki/UbiquitousLanguage.html); Evans, *Domain-Driven Design*).

A few identifiers intentionally retain an older spelling — these are deliberate, not debt, and must NOT
be "fixed":

- The `verdict` **column/field name** on `PracticeFinding` — only the enum *type* is `Observation`; the
  column stays `verdict` (so do `VerdictCount`, `countByVerdictForDeveloper`,
  `priorVerdict`/`currentVerdict`).
- Index **names** `idx_practice_finding_correlation`, `idx_finding_reaction_correlation`,
  `idx_feedback_continuity` are stable while their column references are `finding_fingerprint` /
  `feedback_thread_key` — renaming an index is cosmetic churn with no schema benefit.

---

## 6. Standards-divergence design decisions

This section records where the schema **deliberately diverges from** SARIF and the learning-analytics
standards, and *why each divergence is the right call* — not a backlog. Each is a documented decision,
not an open TODO.

The schema also makes three larger decisions worth calling out:

- **Practice criteria versioning (SCD-2)** — `PracticeRevision` / `practice_revision` (§3.8) +
  `PracticeFinding.practice_revision_id` (§3.3). `PracticeService` appends revision 1 on create and a new
  revision whenever `criteria` actually changes (value-compared); `Practice.criteria` stays the current
  projection. Each finding pins to the criteria-as-it-was, so *which criteria version fired this finding*
  is queryable.
- **Developer-facing layer + physical anti-leak projection** — `Practice.whyItMatters` /
  `whatGoodLooksLike` (§3.1, seeded for the default practices, editable in the admin form, guarded against
  detector verdict vocabulary) are served through `LearnerPracticeDTO` / `GET /practices/learner`, which
  carries no `criteria` field **by construction** (§3a): "criteria never reaches a learner" is a physical
  guarantee, asserted by an integration test on the raw learner JSON.
- **`Feedback.subjectUserId` NOT NULL** (§3.5, §4) — set equal to `recipient_user_id`, closing the
  asymmetry with `PracticeFinding.subjectUserId` (xAPI mandatory, unambiguous Actor on both sides).

**Documented design decisions (deliberate keeps — do not "fix"):**

- **`baseline_state` stays derived, not stored.** SARIF stores `result.baselineState`
  (`new`/`unchanged`/`updated`/`absent`) as a first-class field; we compute the equivalent on read from
  the supersession chain. Storing it would *duplicate* state the chain already encodes (a new row with no
  `supersedes_id` is "new"; one with `supersedes_id` is "updated"; an unreplaced prior is "superseded"),
  so a stored column would be a second source of truth that could drift from the chain. We accept the one
  cost — a consumer wanting "only NEW findings this run" walks the chain rather than filtering a column
  ([SARIF #615](https://github.com/oasis-tcs/sarif-spec/issues/615)).
- **Per-occurrence id *and* cross-run fingerprint — both grains are present.** SARIF separates a per-run
  assigned `guid`/`correlationGuid` (a stable id assigned on ingest) from `partialFingerprints` (the
  heuristic recurrence bucket). We already have **both**: `PracticeFinding.id` is a per-occurrence UUID
  (the assigned-id grain), and `finding_fingerprint` is the cross-run recurrence key (the
  `partialFingerprints` grain). Nothing is folded away or missing — the two SARIF grains map onto two
  existing columns.
- **`finding_fingerprint` is a *locus* hash, by design.** It hashes (practice, artifact, subject,
  file-path) — the **locus** the finding is about — deliberately **not** a line number or content/title
  hash. That keeps it stable across line moves and LLM re-wording of the title (proven 0/26 title
  correlation), which is exactly what a recurrence key must do. Cross-file-rename stability is **out of
  scope on purpose**: a moved locus is a *different* locus, and conflating the two would mis-merge
  unrelated findings. This is the SARIF `partialFingerprints` philosophy (stable against churn), applied
  to our domain.
- **No first-class polarity in any standard — our orthogonal axis is the extension.** `Polarity` (esp.
  `CONTEXTUAL`) has no analogue in SARIF, SonarQube, or code-scanning — all assume a rule fires only on
  something wrong. The orthogonal-axis design is defensible and lossless on SARIF export (the mapping in
  §2), but it is *our* extension; interoperability tooling needs that export mapping to understand
  `CONTEXTUAL`.
- **`confidence` (0.0–1.0) kept distinct from SARIF `result.rank` (-1.0–1.0).** The two are different
  scales with different semantics; an export chooses one rather than pretending they are the same number.

### Honest caveats (state, don't hide)

- Any **verbatim Routine-Dynamics-2016 quotation** must be pulled from a library copy before publication;
  the 2016 PDF is paywalled (verified via abstract + repec/Edinburgh records + the 2008 restatement).
- Findings detected **before** versioning shipped pin to NULL criteria-version (an honest "pre-versioning"
  marker — those early findings are not reproducible against an exact rubric snapshot). Every finding from
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
