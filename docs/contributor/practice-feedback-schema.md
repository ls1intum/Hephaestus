# Practice & Feedback Schema — Reference

> Status: living reference for the `practices` Spring Modulith module (entities under
> `server/src/main/java/de/tum/cit/aet/hephaestus/practices/**`). Grounded in ADR 0021
> (`docs/decisions/0021-findings-feedback-synthesis-seam.md`). Uses the **canonical vocabulary**
> throughout — *area*, never *goal*. The legacy *goal* spelling has been **eliminated** across
> code, schema, API, and UI; §5 records the completed migration.

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
| Finding's weight inside a unit | **EvidenceRole** (`PRIMARY` / `SUPPORTING`) | `display_role` | it weights *evidence*, not display; the stale `@Param("displayRole")` is residue (§5) |
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
| criteria | String (TEXT) | `criteria` | no | NL spec passed to the detection agent. | The rule body the LLM evaluates against. |
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
| name | String(128) | `name` | no | Facilitator-renameable display label. | Dashboard label. |
| description | String (TEXT) | `description` | yes | Optional blurb on the area card. | Context for the bucket. |
| active | boolean | `is_active` | no (default true) | Cohort-level visibility toggle; independent of `Practice.active`. | Surface/hide on dashboards without touching detection. |
| displayOrder | int | `display_order` | no (default 0) | Facilitator dashboard ordering. | Deterministic UI sequence. |
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
| action | FindingReactionAction | `action` | no | `APPLIED` / `DISPUTED` / `NOT_APPLICABLE`. | Research signal (RQ1/RQ2/RQ4), not workflow. |
| explanation | String (TEXT) | `explanation` | yes | Free-text rationale (required for `DISPUTED`). | Qualitative dispute signal. |
| createdAt | Instant | `created_at` | no (immutable) | When the reaction was submitted. | Temporal "changed my mind" record. |

### 3.5 `Feedback` — table `feedback`

Immutable, append-only synthesised **delivery** unit for a single recipient — the author-facing (or
reviewer-/facilitator-facing) rendering of one or more findings, with rendered body, channel, provenance,
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
| artifactType | WorkArtifact | `artifact_type` | yes | Kind of artifact this is about. | Nullable: reflection-dashboard / facilitator-digest feedback is not anchored to one artifact. |
| artifactId | Long | `artifact_id` | yes | External id of the target. | Nullable in lockstep with `artifactType`. |
| recipientUserId | Long | `recipient_user_id` | no | The user this is delivered **to** (FK `fk_feedback_recipient`). Raw Long. | Messaging "To"-recipient; distinct from subject. ≈ xAPI Authority / audience — [xAPI/Caliper comparison](https://www.imsglobal.org/initial-xapicaliper-comparison). |
| subjectUserId | Long | `subject_user_id` | yes | The user this is **about** when ≠ recipient (e.g. reviewer-side feedback to a facilitator). | xAPI Actor. Nullable defaulting to recipient — see §6 gap. |
| surface | FeedbackChannel | `surface` | no | Destination class (in-context / conversation / reflection / facilitator). | Decouples "what we say" from "where it lands". Column name `surface`; concept word is **channel**. |
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

### 3.8 Enumerations

| Enum | Values & meaning | Grounding |
| --- | --- | --- |
| **WorkArtifact** | `PULL_REQUEST` (code diff + commits + review thread, delivered in-PR), `ISSUE` (title, body, labels, assignees, comment thread, state-transition timeline — no diff). | Named for the *work*, not a tool; closed set grows in lockstep with a runtime that can build that artifact's context (design doc, chat thread). |
| **Polarity** | `DESIRABLE` (OBSERVED=strength, NOT_OBSERVED=gap), `UNDESIRABLE` (OBSERVED=problem, NOT_OBSERVED=clean), `CONTEXTUAL` (per-finding severity carries the direction). | Orthogonal direction axis SARIF lacks (ADR 0021, F-6). `CONTEXTUAL` is a genuine extension — replaces legacy `MIXED`. |
| **SubjectRole** | `AUTHOR` (subject == developer), `REVIEWER` (subject == reviewer, distinct from developer). | xAPI Actor. The firewall keeping reviewer-side lessons off the author. Replaces `audience_role`/`AUDIENCE_REVIEWER`. |
| **Observation** | `OBSERVED` (present), `NOT_OBSERVED` (absent where expected), `NOT_APPLICABLE` (practice irrelevant to the work). | Sign-neutral; ≈ SARIF `result.kind` with the direction factored out. `NOT_APPLICABLE` is a verbatim SARIF match. |
| **Severity** | `CRITICAL`, `MAJOR`, `MINOR`, `INFO` — impact, orthogonal to verdict. | SARIF `result.level` / SonarQube blocker..info. |
| **FindingReactionAction** | `APPLIED` ("I fixed/will fix" — RQ2), `DISPUTED` ("AI is wrong", requires explanation — RQ1/RQ4), `NOT_APPLICABLE` ("valid but irrelevant" — RQ4). | Research signal, not workflow; no DISMISSED/ACKNOWLEDGED (non-action = absence of a row). |
| **EvidenceRole** | `PRIMARY` (anchors the headline), `SUPPORTING` (corroborates). | Synthesis-time weighting; replaces `display_role`. |
| **FeedbackChannel** | `IN_CONTEXT` (on the PR/issue), `CONVERSATION` (mentor turn), `REFLECTION_DASHBOARD` (recipient's private dashboard), `FACILITATOR` (instructor). | Decouples message from channel. |
| **FeedbackDeliveryState** | `PREPARED`, `DELIVERED`, `SUPERSEDED` (replaced via `supersedes_id`), `SUPPRESSED` (withheld; see reason), `FAILED`. | Delivery state machine + review-tool edit-in-place (SUPERSEDED) + SARIF `suppressions` (SUPPRESSED). |
| **FeedbackSuppressionReason** | `REVIEWER_SIDE`, `BELOW_THRESHOLD`, `LOW_CONFIDENCE`, `POLICY_FLOOR_DROP`, `REACTED_DISPUTED` (subject DISPUTED this locus earlier — B2), `REACTED_NOT_APPLICABLE` (subject marked N/A earlier — B2). | ≈ SARIF `suppression.justification`. `REVIEWER_SIDE` replaces legacy `AUDIENCE_REVIEWER`. |
| **FeedbackOrigin** | `AGENT` (LLM), `POLICY_FLOOR` (deterministic guaranteed-coverage), `FALLBACK` (synthesis unavailable/failed). | Provenance for honest quality measurement. |
| **PlacementSlot** | `SUMMARY`, `INLINE`, `CONVERSATION_TURN`. | Where a placement renders. Replaces the `placement`-as-slot word. |
| **PlacementPostedState** | `PENDING`, `POSTED`, `SNAPPED` (anchor moved to nearest valid line), `FELL_BACK` (posted as summary/thread instead of inline), `OUTDATED` (diff line changed), `ORPHANED` (anchored code/thread gone), `GONE` (comment deleted out-of-band), `FAILED`. | GitHub/GitLab review-comment lifecycle. |
| **PlacementAnchorKind** | `LINE`, `RANGE`, `FILE`, `IMAGE`. | Diff-anchor granularity. |
| **PlacementAnchorSide** | `OLD` (left/base), `NEW` (right/head). | Unified-diff side. |

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
- **The `null ⇒ developer` subject fallback** — removed. `PracticeFinding.subjectUserId` is now
  `NOT NULL` and always explicitly populated. Every reader can trust the column without a fallback, and
  reviewer-side findings (subject ≠ developer) are representable. This matches xAPI's requirement that the
  Actor (the agent a statement is about) be mandatory and unambiguous
  ([xAPI](https://xapi.com/statements-101/)).
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

## 5. Legacy terminology eliminated — the `goal → area` migration (completed)

The canonical word for the grouping bucket is **area**; **`goal` was legacy** and has been removed from
code, schema, API, and UI. This satisfies the DDD *Ubiquitous Language* mandate: one word per concept
across the whole bounded context, model through UI
([Fowler](https://martinfowler.com/bliki/UbiquitousLanguage.html); Evans, *Domain-Driven Design*). A
synonym surviving in one layer (the DB table while the entity was already renamed) is a defect that
breaks the "same word everywhere" contract and forces translation at every boundary.

**Renamed to `area` (done — verified by an empty `db:draft-changelog` drift diff and the regenerated
`openapi.yaml` / client / ERD):**

- `Practice.goal` field + `getGoal()`/`setGoal()` → `area` / `getArea()`/`setArea()`
  (`Practice.java`, `PracticeAreaService`, `PracticeDTO`, `PracticeCatalogInjector`,
  `PracticeStandingAspectProvider`, `PracticeFindingRepository` JPQL `p.goal`, `PracticeRepository`
  `attributePaths="goal"` + `findByWorkspaceIdAndGoalId`).
- DB table `practice_goal` + PK `practice_goalPK` → `practice_area` / `practice_areaPK`
  (`PracticeArea.java` `@Table`; changelog `1781092589259-58`/`-59` rename table + PK/uk/idx/fk; ERD `schema.mmd`).
- FK `fk_practice_goal` (practice→area) → `fk_practice_area`.
- Index `idx_practice_practice_goal` (on `practice.practice_area_id`) → `idx_practice_practice_area`.
- `fk_practice_goal_workspace` / `uk_practice_goal_workspace_slug` / `idx_practice_goal_workspace_active`
  → `*_practice_area_*`.
- `PracticeAreaService` member vocab: `goalService`, `practiceGoalRepository`, `goalRepository`,
  `createGoal`/`getGoal`/`updateGoal`/`deleteGoal`/`listGoals`, `bindPractice(goalSlug)` →
  `areaService`, `practiceAreaRepository`, `areaRepository`, `createArea`/`getArea`/`updateArea`/
  `deleteArea`/`listAreas`, `bindPractice(areaSlug)`.
- REST routes `/practice-goals` + `/practices/{slug}/goal`; operationIds
  `listGoals`/`getGoal`/`createGoal`/`updateGoal`/`deleteGoal`/`reorderGoals`/`bindGoal`; path param
  `goalSlug`; tag `"Practice Goals"` → `/practice-areas` + `/practices/{slug}/area`; `listAreas`/…/
  `bindArea`; `areaSlug`; tag `"Practice Areas"` (regenerate `openapi.yaml`).
- DTO fields `PracticeDTO.goalSlug`, `BindPracticeAreaRequestDTO.goalSlug` → `areaSlug`.
- `PracticeFindingRepository` projection `GoalStandingRow` + `getGoalSlug()`/`getGoalName()` +
  `findGoalStandingByDeveloperAndWorkspace` + JPQL aliases `goalSlug`/`goalName` → `AreaStandingRow` +
  `getAreaSlug()`/`getAreaName()` + `findAreaStandingByDeveloperAndWorkspace`.
- `PracticeStandingAspectProvider` inner `GoalAcc` + `var goals` + `practice_standing.json` keys
  `"goals"`/`"goalSlug"`/`"goalName"` → `AreaAcc` + `areas` + `"areas"`/`"areaSlug"`/`"areaName"`
  (mentor ABI — coordinate with `mentor system.md`).
- `PracticeCatalogInjector` runner entry key `"goal"` → `"area"`.
- `default-catalog.json` top-level array key `"goals"` + seeder `catalog.path("goals")`/`goalNode` →
  `"areas"`.
- `PracticesControllerAdvice.handleGoalSlugConflict` + message `"Practice goal slug conflict"` →
  `handleAreaSlugConflict` + `"Practice area slug conflict"`.
- Webapp `PracticeGoalsPanel` (+ `.stories`), route `goals.tsx`, `PracticeGoalsContainer`, `byGoal`/
  `goalSections`/`goalSlug`, `NO_GOAL`, `mockGoals`, and generated `listGoals`/…/`bindGoal` helpers →
  `PracticeAreasPanel`, `areas.tsx`, `PracticeAreasContainer`, area-prefixed names (client regenerated
  from OpenAPI via `openapi-ts`).
- `FeedbackFindingRepository` `@Param("displayRole")` + native-SQL `:displayRole` → `@Param("evidenceRole")`
  / `:evidenceRole` (the last non-`goal` residual; column was already `evidence_role`, field already
  `evidenceRole`).

**Legitimate keeps (NOT debt — do not "fix"):**

- The `verdict` *column/field name* on `PracticeFinding` — only the enum *type* became `Observation`; the
  changelog never renamed the column. (`VerdictCount` projection, `countByVerdictForDeveloper`,
  `priorVerdict`/`currentVerdict` likewise stay.)
- Index *names* `idx_practice_finding_correlation`, `idx_finding_reaction_correlation`,
  `idx_feedback_continuity` — the migration deliberately kept the index names while their column refs were
  auto-updated to `finding_fingerprint` / `feedback_thread_key` (cosmetic-only; changelog comments say so).
- "correlation key" / "continuity key" prose in Javadoc — the columns are already
  `finding_fingerprint` / `feedback_thread_key` and the helper classes already `FindingFingerprint` /
  `FeedbackThreadKey`; only prose references the old names.

---

## 6. Open gaps the literature exposes

- **Fingerprint conflates two SARIF concepts.** SARIF separates `partialFingerprints` (a heuristic bucket
  an RMS may further disambiguate) from `guid`/`correlationGuid` (a stable id assigned on ingest). We fold
  both into one `finding_fingerprint` column. Acceptable today, but a reviewer should know the assigned-id
  half is absent — re-bucketing churn cannot be disambiguated downstream the way an RMS could
  ([SARIF #615](https://github.com/oasis-tcs/sarif-spec/issues/615)).
- **`baselineState` is implicit, not stored.** SARIF stores `result.baselineState`
  (`new`/`unchanged`/`updated`/`absent`) as a first-class field; we compute the equivalent on read from the
  supersession chain. Equivalent in outcome, but a consumer cannot query "show me only NEW findings this
  run" without walking the chain.
- **`Feedback.subjectUserId` is nullable; `PracticeFinding.subjectUserId` is not.** The finding side
  matches xAPI's mandatory, unambiguous Actor; the feedback side defaults subject⇒recipient via NULL. This
  is a weaker contract than the finding side and re-introduces a (smaller) fallback on the delivery row —
  worth tightening for symmetry ([xAPI](https://xapi.com/statements-101/)).
- **No first-class polarity in any standard.** `Polarity` (esp. `CONTEXTUAL`) has no analogue in SARIF,
  SonarQube, or code-scanning — all assume a rule fires only on something wrong. The orthogonal-axis design
  is defensible and lossless on SARIF export, but it is *our* extension; interoperability tooling will not
  understand `CONTEXTUAL` without the export mapping in §2.
- **Confidence vs rank stored separately, not unified.** We keep `confidence` (0.0–1.0) where SARIF would
  use `result.rank` (-1.0–1.0 diagnostic relevance). Fine, but the two are not the same scale and an export
  must choose one.

---

*Standards cited:* SARIF v2.1.0 (OASIS) ·
[spec](https://docs.oasis-open.org/sarif/sarif/v2.1.0/csprd01/sarif-v2.1.0-csprd01.html) ·
[fingerprints #615](https://github.com/oasis-tcs/sarif-spec/issues/615) ·
SonarQube clean-code [software qualities](https://docs.sonarsource.com/sonarqube-server/10.8/core-concepts/clean-code/software-qualities) ·
GitHub code-scanning alert lifecycle [discussion](https://github.com/orgs/community/discussions/9175) ·
xAPI (ADL) [Statements 101](https://xapi.com/statements-101/) · IMS [xAPI/Caliper comparison](https://www.imsglobal.org/initial-xapicaliper-comparison) ·
DDD Ubiquitous Language [Fowler](https://martinfowler.com/bliki/UbiquitousLanguage.html) (Evans, *Domain-Driven Design*).
