# ADR 0021: Findings vs feedback — the agent produces evidence *and* granular feedback; we deliver feedback, never findings

**Status:** Accepted
**Date:** 2026-06-14
**Authors:** (practice-mentoring owner)
**Builds on:** [ADR 0020](0020-context-fabric-everything-is-an-integration.md) (the content-fabric / `ContentProvider` seam and history-in-sandbox), [ADR 0007](0007-sandbox-spi-shape.md) (the Pi agent sandbox)
**Partially superseded by:** [ADR 0022](0022-observation-presence-assessment-and-schema-cleanup.md) (F-6 / F-13 / F-24)

> [!NOTE]
> This ADR captures the **decision and rationale**. Some field/enum names below were renamed during
> implementation (e.g. `correlation_key` → `finding_fingerprint`, `continuity_key` → `feedback_thread_key`,
> `display_role` → `evidence_role`, `contributor_id` → `developer_id`). For the **authoritative shipped
> schema and ubiquitous-language**, see `docs/contributor/practice-feedback-schema.md` §2.
>
> The `FACILITATOR` delivery channel discussed below was **subsequently removed**: every `FeedbackChannel`
> is developer-facing — feedback is delivered to the developer it is about, never to a mentor, instructor,
> or grader. The system has no facilitator/evaluative delivery surface.

## Context

The umbrella research question is: **how can AI provide effective mentoring support for developers — where *effective* means developers act on the feedback and their practices change.** Delivery and reception are therefore first-class.

Today three concerns are conflated. `PracticeFinding` (`@Immutable`, idempotent) is genuine atomic evidence — but it carries `reasoning`/`guidance` TEXT (proto-feedback baked into the measurement). `DeliveryComposer` is a ~991-line pure Java step that renders the *in-memory* parser output (not the persisted rows) into one MR/PR comment **every review**, surfacing essentially every finding. There is **no `Feedback` entity**: the delivered artifact is unpersisted, the posted id lives on `AgentJob.deliveryCommentId` (one scalar id), and `PracticeDetectionCompletedEvent` is published-into-the-void (no consumer). The result: "one finding → one comment" bot-noise (Wessel et al.), no record of *what was actually delivered*, no reception signal, and no seam for the dashboard or mentor channels.

This ADR makes the **agent produce findings *and* feedback**, persists feedback as a **granular** artifact, and locks a schema that needs **no future migration** for any planned surface.

## Principle

> **A finding is immutable evidence that a practice was (or was not) enacted on an event. Feedback is a prepared pedagogical artifact synthesised from a *selection* of findings and shaped for a delivery channel. We deliver feedback, never raw findings.**

A finding is the evidence record (database + the reflective dashboard). What reaches a developer on *any* channel is always a prepared *feedback* artifact — no path posts a finding's raw verdict/severity/evidence to a developer.

**Producer / renderer split.** The **agent** is the feedback content producer (`report_feedback`, a terminal in-session turn after `report_finding`). The server-side **renderer** (the refocused `DeliveryComposer`) is deterministic — it assembles `Feedback` rows into the channel's wire payload (summary comment + positioned diff notes) and owns only the mechanical concerns (self-marker, `DiffHunkValidator` position-snapping, locale-safety, severity emoji, image-strip). It renders *feedback*, never findings, and decides no content.

**Feedback is granular.** A `Feedback` row is **one lifted/fused pedagogical unit**, not the whole comment. The MR comment is the *render* of ordered `Feedback` rows — never persisted as a blob. "Referenced **and** inlined" is two orthogonal structures with no special case: *reference* is the `FeedbackFinding` M:N; *placement* is `FeedbackPlacement` rows (a `SUMMARY` row and/or an `INLINE` row with a diff anchor). A unit shown as a summary bullet **and** inlined as a diff note is two `FeedbackPlacement` rows sharing one `feedback_id`, each with its own posted id. The granular grain is final because it is the finest independently-addressable unit any surface delivers (per-bullet reaction, per-note anchor, partial supersession); the whole comment is its `GROUP BY` projection.

## Decision register

`[DECIDED]` = owner-signed-off; `[OWNER]` = recommendation, needs the owner's explicit call.

| ID | Status | Decision |
|---|---|---|
| **F-1** | DECIDED | **`Feedback` is a granular *item*** (one row = one pedagogical unit), with a **`FeedbackFinding` M:N** (which findings it draws on) and a **`FeedbackPlacement`** child (where it is shown + the inline anchor + the per-placement posted id). The whole-comment row of an earlier draft is rejected — it cannot hold a per-bullet reaction, a file-line anchor, or partial supersession without a forced migration. |
| **F-2** | DECIDED | **The detection session produces both findings and feedback; delivered in-job.** `report_feedback` runs as a terminal `await session.prompt(SYNTHESIS_PROMPT)` turn (not `followUp`, which no-ops on an idle session) over the warm cache that holds every finding. Delivery stays **in the job** (proven synchronous NATS retry / dead-letter), *not* on an `@Async AFTER_COMMIT` listener (a verified silent-MR-drop risk). |
| **F-3** | DECIDED | **Cross-channel synthesis (mentor/dashboard re-shaping, cross-MR digests) is a separate consumer of the (re-grounded) `PracticeDetectionCompletedEvent`, not built now** — but every surface's **columns exist now** (F-5), so it needs zero schema change. |
| **F-4** | DECIDED | **Selection = deterministic policy floor + LLM shaping.** Every CRITICAL/MAJOR (blocking) finding is mandatory in-context; the LLM groups/orders/words and promotes improvements but can never demote a blocking finding. The server enforces `{blocking finding ids} ⊆ ⋃ PRIMARY findingRefs`; on violation it creates a real `Feedback(origin=POLICY_FLOOR)` + `PRIMARY FeedbackFinding` from the finding's `guidance`. The LLM has no authority over the floor. |
| **F-5** | DECIDED | **In-context stays generous** (covers the set, grouped/ordered, honest "+N more") **until a developer-facing dashboard route ships.** All practice screens are admin-only today; until a contributor can reach the "complete record", in-context is the only thing they see. |
| **F-6** | SHIPPED | **Verdict values renamed `POSITIVE/NEGATIVE/NOT_APPLICABLE` → `OBSERVED/NOT_OBSERVED/NOT_APPLICABLE` + new `Practice.polarity {DESIRABLE,UNDESIRABLE,MIXED}` (default DESIRABLE).** Meaning = `(verdict, polarity)`, centralised in `Polarity.isProblem/isStrength(Verdict)`. **Clean break — no back-compat: the parser rejects the old vocabulary (commits `6a64172dd` + `53bcbd6bb` + `2529042ab`).** Polarity is wired through `PracticeDTO`/create/update + service + catalog seeder, and the delivery partition consults it per-practice (slug→polarity via `PracticeCatalogInjector`), so an UNDESIRABLE practice's `OBSERVED` is delivered as a problem. Behaviour-preserving today (every catalogued practice is DESIRABLE); calibration re-validation remains a live-eval gate. |
| **F-7** | DECIDED | **`DeliveryComposer` → `InContextFeedbackRenderer`.** Delete the finding-iterating composition (`dedupEpicStructure`, `capImprovementTail`, the no-issues opener, the max-2-named-positives cap); keep and extract the wire machinery onto `Feedback` + `FeedbackPlacement`. It renders feedback, never findings. |
| **F-8** | DECIDED | **Robustness ladder, never silence, never a finding dump:** agent feedback fails → retry; the F-4 auto-append and the retry-exhausted fallback produce **minimal `Feedback(origin=FALLBACK)` for the blocking subset only** (from each finding's `guidance`), assembled by the renderer; last resort → a content-bearing count note. No path renders the non-blocking long tail or a raw finding. |
| **F-9** | DECIDED | **`#895` firewall is structural.** Findings are emitted reaction-blind and locked before the feedback turn; only the feedback turn and the mentor read prior findings/feedback/reactions (history-in-sandbox). An ArchUnit/contract test asserts no reaction/feedback token enters the finding-emission context across retries. |
| **F-10** | DECIDED | **`report_finding` returns the finding's stable `idempotency_key`; `report_feedback.findingRefs` carry it** — never `(slug, title)`. |
| **F-11** | DECIDED | **Audit on `Feedback`:** `origin {AGENT,POLICY_FLOOR,FALLBACK}` (the policy-vs-LLM boundary is `GROUP BY origin`), `model_id`, `composer_version`. (`policy_floor_delta` jsonb is **dropped** — `origin` restates it.) |
| **F-12** | DECIDED | **Reviewer-side guards exist now, latent:** `PracticeFinding.subject_user_id` (null ⇒ contributor) + `Practice.audience_role {AUTHOR,REVIEWER}` (default `AUTHOR`; a `REVIEWER` finding is persisted, `state=SUPPRESSED`, never posted in-context to the author). No `REVIEWER` writer this PR. |
| **F-13** | DECIDED | **Reactions move to feedback.** `FindingReaction` → **`FeedbackReaction`** wholesale: `feedback_id NOT NULL` (the developer reacts to the unit they saw), `finding_id NULLABLE` (granular per-evidence dispute, and the finding-less OVERVIEW unit). RQ2 attributes APPLIED/DISPUTED to the *delivered unit*. |
| **F-14** | DECIDED | **Delete `FeedbackPost` outright** (verified 0 production writers; live edit-in-place is the marker-sweep, not this ledger). Keep `SubjectClass.SLACK_MESSAGE_THREAD` for the SPI. The posted id lives on `FeedbackPlacement.external_ref` (1:N — a summary id, inline ids, GitHub place-then-fallback), so the `AgentJob.deliveryCommentId` scalar mistake is not repeated. |
| **F-15** | DECIDED | **Mentor surface is migration-free:** `Feedback.rendered_body` is **nullable**, with `body_storage {SNAPSHOT,REFERENCE}` (`SNAPSHOT` default for IN_CONTEXT; the CONVERSATION writer later sets `REFERENCE` + a placement pointer to the transcript turn, zero ALTER). |
| **F-16** | DECIDED | **Supersession by `continuity_key`** (a cross-job natural key), not the job-scoped `idempotency_key` — re-review is a *new* `agent_job`; `supersedes_id` resolves via `continuity_key`. |
| **F-17** | SUPERSEDED by F-24 | ~~Defer the `FeedbackInteraction` passive-engagement table.~~ The pressure-test showed the *table* should never exist: fold passive verbs into `FeedbackReaction` as additive enum values (F-24). Defer the passive *writer*, not a table. |
| **F-18** | DECIDED | **`PracticeFinding.correlation_key`** (indexed, not unique) — the cross-run equivalence key. **Fatal fix:** without it the headline RQ ("do practices change over time") is unanswerable at the evidence layer — `idempotency_key` is job-scoped + positional (`practiceSlug:i:…:jobId`, verified line 128), and `continuity_key` is only on the *delivered* `Feedback` subset, so withheld findings have no cross-run identity. Derived at persist from `hash(practice_id, target_type, target_id, COALESCE(subject_user_id,contributor_id), content-anchor)` — never the line number (Code Climate's documented fixed-and-new bug), never the job id. `baseline_state {NEW,UNCHANGED,UPDATED,ABSENT}` is then **derived on read** (= SARIF baselineState; `ABSENT` = the developer fixed it). Grounded: SARIF `correlationGuid`, SonarQube line-hash, Code Climate fingerprint. |
| **F-19** | DECIDED | **`Feedback.target_type/target_id` NULLABLE** — a cross-MR `CONVERSATION` digest (F-3) has no single target; per-target linkage lives in `FeedbackFinding`. As drafted, non-null `target` silently contradicted F-3. |
| **F-20** | DECIDED | **`Feedback.suppression_reason`** (= SARIF `suppression.justification`; set only when `SUPPRESSED`) closes the "why didn't the developer see this finding" audit gap. **`Feedback.synthesis_prompt_version`** (= PROV-O `prov:used` of the prompt) — `composer_version` versions the renderer, not the prompt that shaped the AI's words; required to reproduce a feedback diff. |
| **F-21** | DECIDED | **Harden the inline anchor** (keep it *logical*, F-7): add `anchor_old_path` (GitLab rename-mandatory — **verified live bug** at `GitlabInlineFindingChannel:207`, oldPath=new path → silent fallback), `anchor_kind {LINE,RANGE,FILE,IMAGE}` (file-level/non-line stop faking line 1; `IMAGE` reserves the seam with **no** coordinate columns), `anchor_quote` (content re-anchor fallback = WADM `TextQuoteSelector`, survives a moved line — the stale-diff-note bug class), `anchor_start_side` (cross-side ranges). Rename `anchor_commit_sha → pinned_commit_sha` (a **staleness witness**, never the wire SHA — the adapter recomputes the position from live `diff_refs`). `posted_state` gains `OUTDATED/ORPHANED/GONE` (separate staleness/deletion from API error). Thread `old_path` through the `FindingAnchor.DiffAnchor` SPI. |
| **F-22** | DECIDED | **`placement` gains `CONVERSATION_TURN`** so a mentor chat turn has a real selector instead of being shoehorned into `SUMMARY`. |
| **F-23** | DECIDED | **Capture native resolution on the placement** (not on `Feedback` — resolution is about the posted artifact, reaction is about the lesson): `thread_external_ref` (edit-in-place + reply across re-reviews) + `resolved/resolved_at/resolved_external_ref`. GitLab "resolve thread" / Gerrit `unresolved` is a workflow-native "acted-on" signal at **zero UI cost** — the single biggest free RQ2 win. |
| **F-24** | DECIDED | **Reshape `FeedbackReaction` into an event log:** replace the closed `action` enum with an **open `verb`** discriminator + `occurred_at` + `supersedes_id` (= xAPI `actor-verb-object` / AS2). Reaction verbs (`APPLIED/DISPUTED/NOT_APPLICABLE`) now; passive verbs (`VIEWED/EXPANDED/DWELLED`) become additive enum values in the **same** table — so the deferred passive-engagement log is **never** a second table. Reject the xAPI URI-verbs / JSON-LD wire format — steal the shape, not the serialization. |
| **F-25** | DECIDED | **`Feedback.body_storage` CUT as theatre** — a `NULL rendered_body` already expresses "this is a reference"; the enum was a 0-writer column (the very trap F-17/F-25 forbid). |

## Final schema (5 tables + 2 `Practice` columns)

> Hardened by a standards-grounded ego-death pressure-test (SARIF, GitHub/GitLab/Gerrit, SonarQube, xAPI/Caliper/AS2/PROV-O, W3C Web Annotation). Every field is justified to a precedent or a breaking scenario in **§ Grounding** below; theatre is cut in **§ Deliberately not adopted**. The single fatal pre-test gap — the schema could not answer its own RQ ("do practices change over time") — is closed by `PracticeFinding.correlation_key` (F-18).

```
PracticeFinding  (@Immutable — KEEP; +correlation_key, +subject_user_id; verdict renamed)
  id uuid PK · idempotency_key varchar UNIQUE        # per-job OCCURRENCE id (= SARIF guid); positional, keep
  correlation_key varchar INDEXED (not unique)       # F-18 — cross-run equivalence (= SARIF correlationGuid /
                                                     #   SonarQube line-hash). Derived at persist from
                                                     #   hash(practice_id, target_type, target_id,
                                                     #   COALESCE(subject_user_id,contributor_id), content-anchor)
                                                     #   — NEVER the line number, NEVER the job id / index.
  agent_job_id uuid (scalar) · practice_id FK · target_type WorkArtifact · target_id bigint · contributor_id FK users
  subject_user_id bigint NULL FK users               # F-12 (null ⇒ contributor)
  title · verdict {OBSERVED,NOT_OBSERVED,NOT_APPLICABLE}   # F-6  (= SARIF kind; applicability)
  severity {CRITICAL,MAJOR,MINOR,INFO}               # = SARIF level
  confidence float                                   # = SARIF rank
  evidence jsonb                                     # region-shaped: locations[{path,startLine,startCol,endLine,endCol,snippet}], references[]
  reasoning text · guidance text · detected_at
  # baseline_state {NEW,UNCHANGED,UPDATED,ABSENT} is DERIVED ON READ over a correlation_key chain (not stored —
  #   would break @Immutable; = SARIF baselineState, emitted per-run). ABSENT/UPDATED-to-clean = "they fixed it" = RQ2 signal.

Practice  (mutable — +2)                              # = SARIF reportingDescriptor; practice_goal/category = taxa
  polarity {DESIRABLE,UNDESIRABLE,MIXED} NOT NULL     # F-6
  audience_role {AUTHOR,REVIEWER} NOT NULL DEFAULT 'AUTHOR'   # F-12 (who the finding is about)

Feedback  (@Immutable — NEW — the granular pedagogical unit = W3C Web Annotation "body")
  id uuid PK · idempotency_key varchar UNIQUE (agent_job_id + unit_ordinal)
  agent_job_id uuid (scalar) · workspace_id bigint (scalar)
  target_type NULL · target_id NULL                  # F-19 NULLABLE — a cross-MR digest has no single target (linkage in FeedbackFinding)
  recipient_user_id bigint FK users NOT NULL         # who sees it (this PR = author)
  subject_user_id bigint NULL FK users               # who it is about (null ⇒ recipient; COALESCE)
  surface {IN_CONTEXT,CONVERSATION,REFLECTION_DASHBOARD,FACILITATOR} NOT NULL   # only IN_CONTEXT written now
  unit_ordinal int NOT NULL
  state {PREPARED,DELIVERED,SUPERSEDED,SUPPRESSED,FAILED} NOT NULL
  suppression_reason {AUDIENCE_REVIEWER,BELOW_THRESHOLD,LOW_CONFIDENCE,POLICY_FLOOR_DROP} NULL  # F-20 (= SARIF suppression.justification); set only when SUPPRESSED
  rendered_body text NULL                            # null ⇒ a referenced body (mentor transcript turn); NULL itself expresses "reference" (body_storage CUT)
  origin {AGENT,POLICY_FLOOR,FALLBACK} NOT NULL       # F-11 (= PROV-O wasAssociatedWith)
  model_id varchar NULL · composer_version varchar NULL          # F-11 (renderer version)
  synthesis_prompt_version varchar NULL              # F-20 (= PROV-O prov:used of the SYNTHESIS_PROMPT; reproducibility)
  supersedes_id uuid NULL self-FK · continuity_key varchar NULL  # F-16 (which prior delivered UNIT this supersedes)
  created_at · delivered_at timestamptz NULL

FeedbackFinding  (@Immutable — NEW — M:N reference = WADM "target")
  feedback_id uuid FK CASCADE · finding_id uuid FK CASCADE
  display_role {PRIMARY,SUPPORTING} NOT NULL         # PRIMARY drives severity + the F-4 floor
  ordinal int NOT NULL · PK (feedback_id, finding_id) · INDEX(finding_id)

FeedbackPlacement  (@Immutable — NEW — where shown + LOGICAL anchor + per-placement posted id = WADM "selector")
  id uuid PK · feedback_id uuid FK CASCADE NOT NULL
  placement {SUMMARY,INLINE,CONVERSATION_TURN} NOT NULL    # F-22 (+CONVERSATION_TURN — a mentor turn is a real selector)
  anchor_kind {LINE,RANGE,FILE,IMAGE} NULL           # F-21 (file-level + non-line stop faking line 1; IMAGE reserves the seam, no coord cols)
  anchor_path varchar NULL · anchor_old_path varchar NULL  # F-21 (old_path is GitLab-mandatory on renames — verified live bug)
  anchor_start_line int NULL · anchor_end_line int NULL
  anchor_side {OLD,NEW} NULL · anchor_start_side {OLD,NEW} NULL   # F-21 (cross-side multi-line ranges)
  anchor_quote text NULL                             # F-21 (content re-anchor fallback — WADM TextQuoteSelector; survives a moved line)
  pinned_commit_sha varchar NULL                     # F-21 RENAMED — a STALENESS WITNESS only; the channel recomputes the wire position from live diff_refs
  external_ref varchar NULL                          # the posted NOTE id (1:N — F-14)
  thread_external_ref varchar NULL                   # F-23 — the discussion/thread id (edit-in-place + reply across re-reviews)
  resolved boolean NOT NULL DEFAULT false · resolved_at timestamptz NULL · resolved_external_ref varchar NULL  # F-23 — workflow-native "acted-on" (GitLab resolve-thread / Gerrit unresolved); the cheapest RQ2 signal
  posted_state {PENDING,POSTED,SNAPPED,FELL_BACK,OUTDATED,ORPHANED,GONE,FAILED} NOT NULL DEFAULT 'PENDING'  # F-21 (separate staleness/deletion from API error)
  created_at

FeedbackReaction  (@Immutable — RENAME of FindingReaction; reshaped into an EVENT log — F-13, F-24)
  id uuid PK · feedback_id uuid FK CASCADE NOT NULL · finding_id uuid NULL FK SET NULL
  contributor_id bigint FK users
  verb varchar NOT NULL                              # F-24 — OPEN discriminator (= xAPI verb / AS2 type): APPLIED, DISPUTED,
                                                     #   NOT_APPLICABLE now; VIEWED, EXPANDED, DWELLED added later as enum VALUES,
                                                     #   so the deferred passive-engagement log is NEVER a second table
  explanation text NULL · supersedes_id uuid NULL self-FK · occurred_at timestamptz NOT NULL
```

Dashboard (`REFLECTION_DASHBOARD`) and facilitator surfaces write **no** rows — derive-on-read over `PracticeFinding` (+ derived `baseline_state`) + `Feedback` + `FeedbackReaction` via the shipping `PracticeFindingController`.

## Grounding (per-entity / per-field justification)

Every entity and field traces to an interoperability standard or a breaking scenario, so the schema is defensible field-by-field.

| Our concept | Precedent | Why it is the right shape |
|---|---|---|
| `Feedback` / `FeedbackFinding` / `FeedbackPlacement` triad | **W3C Web Annotation Data Model** (`body` / `target` / `selector`) | Independently re-derived the exact split — a unit of commentary (`body`), what it is *about* (`target`), where it is *anchored* (`selector`). Not over-built; it is the standard. |
| `PracticeFinding` = evidence; `Feedback` = message | **SARIF** result vs **GitHub/Gerrit** review comment | A finding is a machine result (SARIF); a comment is its delivery. Separating them is what every tool does. |
| `verdict` {OBSERVED/NOT_OBSERVED/NOT_APPLICABLE} | **SARIF `kind`** {pass/fail/open/review/notApplicable} | Applicability/outcome axis, orthogonal to severity. |
| `severity` / `confidence` | **SARIF `level` / `rank`** | Level = how bad; rank = how sure. Distinct axes. |
| `Practice.polarity` | (our addition over SARIF) | SARIF conflates good/bad into `level`; `(verdict, polarity)` removes the `NEGATIVE==bad` sign-overload bug. |
| `PracticeFinding.correlation_key` (F-18) | **SARIF `correlationGuid`**, **SonarQube line-hash**, **Code Climate fingerprint** | The cross-run identity every tool puts on the *evidence* (not the message) to trend a finding over time. The RQ depends on it. |
| `baseline_state` (derived) | **SARIF `baselineState`** {new/unchanged/updated/absent} | Per-run projection, not stored (keeps `@Immutable`). `ABSENT` = the practice was enacted = the positive signal. |
| `evidence` jsonb (region-shaped) | **SARIF region** field *names* | Borrow `startLine/endLine/snippet` inside one flexible jsonb — not SARIF's typed location/codeFlow tables most practices never populate. |
| `FeedbackPlacement.anchor_*` (logical) | **SARIF physicalLocation** (logical) + **GitLab/GitHub/Gerrit** wire positions | Store a logical anchor; the channel computes the vendor wire position from live `diff_refs`. `anchor_old_path` is GitLab-mandatory on renames; `anchor_quote` is WADM `TextQuoteSelector`. |
| `external_ref` per placement (F-14) | **GitHub/GitLab note id** (1:N) | One unit → many posted ids (summary id, inline ids, place-then-fallback). A scalar id is the `deliveryCommentId` mistake. |
| `resolved/thread_external_ref` (F-23) | **GitLab `resolvable/resolved_by`**, **Gerrit `unresolved`** | Workflow-native "acted-on" signal at zero UI cost. |
| `suppression_reason` (F-20) | **SARIF `suppression`** {kind, status, justification} | Suppression is a first-class object with a reason, never a bare flag. |
| `origin` / `model_id` / `synthesis_prompt_version` | **PROV-O** `wasAssociatedWith` / `prov:used` | Auditable provenance of an AI-generated artifact. |
| `FeedbackReaction.verb` (open) + `occurred_at` (F-24) | **xAPI** `actor-verb-object`, **AS2** | An append-only event log; one table for explicit + passive verbs. |
| `Gerrit robot_run_id` ≈ our `agent_job_id` | **Gerrit RobotComment** | Which run produced the artifact. |

## Deliberately NOT adopted (theatre, cut with reason)

- **`body_storage` enum** — cut; a `NULL rendered_body` already says "reference" (0-writer column).
- **SARIF `fingerprints`/`partialFingerprints`** as columns — no; we own producer + store, so one deterministic `correlation_key` suffices (partial-fingerprint fuzzy matching exists only for third-party RMS re-bucketing).
- **SARIF typed `location`/`region`/`codeFlows` tables** — no; one `evidence` jsonb is correctly flatter (most practices have no file location; we never produce data-flow traces). Borrow the field *names*, not the tables.
- **SARIF `fixes` / Gerrit `fixSuggestions`** (machine-applicable patches) — no; a mentoring product delivers pedagogy, not auto-apply diffs. `guidance`-as-prose is a deliberate, defensible divergence.
- **GitLab `base_sha/start_sha/head_sha` + `line_code`** as durable columns — no; MR-snapshot-specific, stale on every push, GitHub-incompatible. Recompute from live `diff_refs` (the channel already does).
- **Gerrit character-precise ranges, GitLab image coordinates** — no; we anchor and post by line. `anchor_kind=IMAGE` reserves the seam with zero coordinate columns.
- **Mutable SonarQube `status/resolution` column on the finding** — no; would break `@Immutable` and contend on the evidence table. `baseline_state` is derived.
- **xAPI/AS2 URI verbs + JSON-LD `@context`** — no; interop wire-format theatre for a single-app Postgres store. Steal the event *shape*, reject the serialization.
- **A separate `FeedbackInteraction` table (now or later)** — no; F-24 makes it the same event log with additive verbs.
- **A per-branch baseline snapshot** (GitLab Code Quality framing) — no; prior findings are already rows; per-finding correlation is finer and better for the RQ.

## No-future-migration proof

Every planned surface maps to a column/table/enum present now: (1) reflection dashboard derives over the shipping controller; (2) facilitator uses `recipient_user_id`/`subject_user_id`; (3) conversation uses `surface=CONVERSATION` + `body_storage=REFERENCE` + placement pointer (`rendered_body` nullable); (4) reviewer-side uses `audience_role`+`subject_user_id`+`state=SUPPRESSED`; (5) referenced-and-inlined = `FeedbackPlacement` × `FeedbackFinding`; (6) per-item reaction = `FeedbackReaction.feedback_id` + nullable `finding_id`; (7) supersession = `supersedes_id`+`continuity_key`+`state=SUPERSEDED`; (8) a Slack channel = a new `surface` enum value; (9) GitHub two-id posts = `FeedbackPlacement` 1:N; (10) audit = `origin`+`model_id`+`composer_version`; (11) cross-MR synthesis = the reserved event writing `REFLECTION` rows of the same shape. **Over-build check:** every new table has the IN_CONTEXT writer; the one real 0-writer table (`FeedbackPost`) is deleted; the one speculative table (`FeedbackInteraction`) is deferred with a named trigger. Forward-compat columns are cheap nullable/enum with documented latency triggers — not speculative tables.

## Legacy to delete (leave it behind)

- **`FeedbackPost` + service + repository + `PostKind` + table** — delete outright (F-14).
- **`DeliveryComposer` finding-iterating composition** — delete; rename to `InContextFeedbackRenderer`, keep the wire machinery (F-7).
- **`AgentJob.deliveryCommentId` as the delivery record** — rewire its 4 readers + the DTO field to read the posted id off `FeedbackPlacement`, regenerate OpenAPI/client, then drop the column (verified: not costless — 4 readers + a public API field).
- **`FeedbackDeliveryService` always-post-new path** — replaced by persist-findings-locked → `report_feedback` → persist `Feedback` rows → `renderer.render` → post in-job → write `external_ref` per placement. Suppression moves from early-return-no-record to `state=SUPPRESSED`.
- **`FindingReaction`** — renamed wholesale to `FeedbackReaction` (F-13).

## Runtime flow (in-context)

1. Detection session: `report_finding` per finding (unchanged) → findings persisted idempotently and **locked** (reaction-blind, F-9).
2. Terminal `await session.prompt(SYNTHESIS_PROMPT)` → one or more `report_feedback` calls emitting granular units `{title, findingRefs[], placements[{placement, anchor?}], role}` → `out/feedback.json`.
3. Server `FeedbackSynthesisService`: map `findingRefs` (idempotency keys) → finding ids; **F-4 floor** (`{blocking} ⊆ PRIMARY refs`, auto-append `POLICY_FLOOR` units); persist `Feedback` + `FeedbackFinding` + `FeedbackPlacement` rows.
4. `InContextFeedbackRenderer` assembles the rows (SUMMARY → summary comment, INLINE → diff notes snapped by `DiffHunkValidator`, one self-marker) and posts **in-job**; writes `external_ref` + `posted_state` per placement.
5. On agent-feedback failure → F-8 ladder; an in-context mark always appears; the long tail is never dumped.

## Implementation plan (one PR, no follow-ups — dependency order)

1. **Verdict rename + `Practice.polarity`** (commit 1, critical path): rename `Verdict` values; add `polarity`; re-ground `NEGATIVE` branches to `(verdict, polarity)` blocking; back-compat parser map; **re-validate calibration**. *Risk: everything depends on this; a missed polarity seed flips a practice's meaning.*
2. **`subject_user_id` + `audience_role`** (F-12): additive nullable columns; no `REVIEWER` writer.
3. **`Feedback` + `FeedbackFinding` + `FeedbackPlacement`** entities + one consolidated changelog (epoch > `1781092589259`). *Risk: keep `continuity_key` and `idempotency_key` distinct; scalar `agent_job_id`/`workspace_id` to avoid a module cycle.*
4. **`report_feedback` tool + terminal synthesis turn** in the runner + parser branch (`feedback.json`). *Riskiest: under GPU saturation degrade to `POLICY_FLOOR` blocking-only, never below today.*
5. **F-9 firewall ArchUnit guard** (no reaction/feedback token in finding-emission context).
6. **F-4 policy-floor guard + F-11 audit** → new `FeedbackSynthesisService`.
7. **Refocus `DeliveryComposer` → `InContextFeedbackRenderer`** (F-7): delete finding-composition, extract wire machinery onto `Feedback`/`FeedbackPlacement`; group SUMMARY by `unit_ordinal`. Rewire `PullRequestReviewHandler`/`IssueReviewHandler`.
8. **Delete `FeedbackPost`; drop `deliveryCommentId` as the record** — rewire its 4 readers to `FeedbackPlacement.external_ref`, regenerate OpenAPI/client, drop the column.
9. **Rename `FindingReaction` → `FeedbackReaction`** (F-13): entity/table/controller/repo/DTOs/action enum; `feedback_id NOT NULL`, `finding_id NULL`. *Risk: Lombok-accessor + Spring-Data derived-query sed-rename traps; only specs-boot catches the latter.*
10. **Regenerate + full gate**: `hasNegative`→`hasBlocking` on the event; `pnpm run format && check`; regenerate OpenAPI/client/ERD; finalise the one changelog; the **calibration re-validation is the no-follow-up gate**.

## Open calls for the owner

- **`correlation_key` derivation tuple** (F-18) — confirm `hash(practice_id, target_type, target_id, COALESCE(subject_user_id,contributor_id), content-anchor)`. For location-less practices (`mr-description-quality`, `commit-discipline`) the content-anchor is absent → fall back to `(practice + target + subject)` and accept the coarser bucket rather than over-engineer. *Recommendation: accept.*
- **`recipient_user_id` rename** (from `contributor_id` on `Feedback`) — a real DTO/OpenAPI/Spring-Data migration for *zero* current behaviour (no facilitator writer this PR). *Recommendation: keep the recipient/subject two-column **semantics** but **defer the rename** until the facilitator surface writes a row.*
- **`Feedback.motivation` {ASSESSING,COMMENTING,EDITING,QUESTIONING}** (W3C Web Annotation speech-act axis) — thesis-strengthening, not load-bearing. *Recommendation: defer-with-trigger (add when the dashboard distinguishes "assessing" cards from "editing" inline suggestions). Your call to add now for analysis richness.*
- **`prior_finding_id` self-FK on `PracticeFinding`** — `correlation_key` alone unblocks the RQ; this only sharpens UPDATED-vs-UNCHANGED for multi-occurrence practices. *Recommendation: add if cheap in the same changelog, else defer.*
- **Hattie `level` derived, not stored** — dropped per the praxis decision; persist only if the RQ analysis needs it frozen at delivery time.

## Consequences

The in-context comment becomes an agent-synthesised, grouped, lifted, **granular** artifact instead of a per-finding render; everything delivered is persisted, placement-tracked, and reaction-linkable (RQ2). The schema is final for every planned surface (proof above). Costs: two feedback content sources (agent + the deterministic blocking-only fallback) feed one renderer; a cross-language `report_feedback` contract (mitigated by a `.mjs`↔Java round-trip test + a blocking-mark E2E test); under GPU saturation the synthesis turn truncates first, degrading to today's quality, never below.

## Sources

Grounded in the formative-assessment and feedback-uptake literature (feed-up / feed-back / feed-forward; feedback that changes the gap; the limits of person-level praise) and in SARIF / xAPI schema conventions. Schema precedents are distilled in `.context/schema-references/findings-feedback-precedents.md`.
