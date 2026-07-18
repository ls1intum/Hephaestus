# Evaluation Provenance Contract

> Status: living reference, part of the detection replay & reliability-metrics epic
> ([#1353](https://github.com/ls1intum/Hephaestus/issues/1353)). Companion to the schema reference in
> `practice-feedback-schema.md` (ADR 0021 / ADR 0022).

What each detection run persists, how an evaluation joins it, and the invariants to preserve.

---

## 1. What is persisted per detection run

Every detection run is one `agent_job` row. The provenance columns are written **before the sandbox
starts** (so failed and cancelled runs keep their provenance too) or frozen at submit time:

| Dimension | Where it lives | Written when |
| --- | --- | --- |
| Pinned model id + version | `agent_job.config_snapshot` (JSONB, frozen `ConfigSnapshot`) and denormalised `agent_job.llm_model` / `llm_model_version` | snapshot at submit; denormalised columns at completion |
| LLM provider, base URL, credential mode, timeout | `agent_job.config_snapshot` | submit |
| Criteria / practice revision | `observation.practice_revision_id` → `practice_revision` (SCD-2 snapshot of `Practice.criteria`) | persist time, pinned **as of `agent_job.started_at`** (see §3) |
| Prompt template version | `agent_job.prompt_digest` — SHA-256 root digest of the prompt scaffolding (`pi-orchestrator.md` + runner script + sidecar scripts), computed by `PiRuntimeFactory` | prepare, before sandbox start |
| Input snapshot reference | `agent_job.inputs_digest` — SHA-256 root digest over **every** file materialised into the sandbox workspace (context files, practice criteria, precompute scripts, task envelope, prompt scaffolding, Pi settings), computed by `AgentJobExecutor` over the final merged file set | prepare, before sandbox start |
| Per-file content hashes | `inputs/manifest.json` (`ContextManifestBuilder`) — per-file `sha256` for `inputs/context/*`, blobs stored in the content-addressed store, manifest persisted under the fabric's `jobs/{jobId}/` | prepare (best-effort; the DB root digest above is the authoritative record) |
| Repository state | `agent_job.metadata.commit_sha` (head ref) — the read-only repo mount is **not** hashed; the commit SHA pins it | submit |
| Usage outcomes | `agent_job.llm_total_*`, `llm_cache_*`, `llm_cost_usd` | completion |

Digest semantics (`ProvenanceDigest`): root digest = SHA-256 over the path-sorted sequence of
`path NUL sha256(content) LF`. Two runs with equal `inputs_digest` consumed byte-identical inputs.
`prompt_digest` is stable across models and workspaces — an evaluation **groups runs by
`prompt_digest`** and must not aggregate precision across different values.

### Sampling parameters

Hephaestus does not set temperature, top_p, or max output tokens for detection runs — sampling is the
provider default for the pinned `(provider, model, model_version)`. That triple, plus `prompt_digest`,
IS the sampling provenance today. **Invariant:** if sampling knobs are ever introduced, they must be
added to `AgentConfig` and carried through `ConfigSnapshot` (the per-job frozen config) — never set
ad hoc in the runtime — so each run keeps its own record.

---

## 2. The feedback ledger: delivered vs withheld

User reactions are only usable as labels if "the developer saw it" is a fact, not a guess. Every
**prepared unit** — a composed review that reached the delivery layer — lands exactly one `feedback`
row, in the state that says what became of it (`FeedbackDeliveryState`, defined in
[practice-feedback-schema.md](./practice-feedback-schema.md)). Only `DELIVERED` counts as exposure;
`SUPPRESSED` means policy withheld it and always carries a reason:

| Reason | Written by | Granularity |
| --- | --- | --- |
| `REACTED_DISPUTED` / `REACTED_NOT_APPLICABLE` | `ReactionSuppressionFilter` — the developer already reacted to this locus | per observation |
| `VOLUME_CAPPED` | `DeliveryComposer` volume cap on the non-blocking improvement tail, reported via `DeliveryContent.withheld` and recorded by `FeedbackLedgerRecorder` | per observation |
| `COMPOSER_DEDUPED` | `DeliveryComposer` near-duplicate collapse (epic-structure pair, co-occurrence pairs) | per observation |
| `CONVERSATION_EXPIRED` | TTL sweeper — a `PREPARED` conversational unit aged out unraised | per unit |
| `VOLUME_CAPPED` (conversation) | `ConversationalFeedbackPreparer` — over the per-recipient cap for a mentor turn | per observation |
| `ARTIFACT_GONE` / `ARTIFACT_CLOSED` / `ARTIFACT_MERGED` / `ARTIFACT_DRAFT` | whole-review delivery gates in `FeedbackDeliveryService` / `IssueReviewHandler` | one unit per job (ordinal 5000) |
| `RECIPIENT_OPTED_OUT` | author disabled AI review | one unit per job |
| `EMPTY_AFTER_SANITIZE` | composed body sanitised to blank and no inline note landed — nothing reached the developer | one unit per job |

Unit ordinal bases on `(agent_job_id, position)`: `0` live in-context unit, `1000+` reaction
suppression, `2000+` composer-withheld, `3000+` conversational, `4000` failed, `5000` gate-suppressed.

**Invariant:** any new decision point that can withhold a prepared unit — a gate, cap, dedup, filter —
MUST write a `SUPPRESSED` row with a reason (add the enum value + its `chk_feedback_suppression_reason`
entry). A silent drop reopens the gap: an evaluator can no longer tell "model missed" from "policy
withheld" from "delivered and ignored". The converse holds too — every reason above names a live writer;
reviewer-audience routing (ADR-0021-C2) will add its own when it lands, rather than keeping an unwritten
value on the books.

---

## 3. Join chains an evaluation uses

- **Observation → what produced it:** `observation.agent_job_id` → `agent_job`
  (`config_snapshot`, `prompt_digest`, `inputs_digest`, `metadata.commit_sha`) and
  `observation.practice_revision_id` → `practice_revision.criteria`.
- **Observation → was it seen:** `feedback_observation` → `feedback.delivery_state`
  (+ `suppression_reason`). An observation bound to a `DELIVERED` unit was rendered (BAD as
  `PRIMARY`; GOOD strengths bind `SUPPORTING` but may render abridged — see §4). An observation
  bound only to `SUPPRESSED`/`FAILED` units was never seen.
- **Reaction → label:** `reaction.feedback_id` → `feedback` (reactions require
  `delivery_state = DELIVERED`, so a reaction is always a label on real exposure) →
  `feedback_observation` → observations. The posted surface is recoverable via
  `feedback_placement.posted_comment_ref` / `chat_message_id`.

Criteria-revision pinning is **as-of `agent_job.started_at`**: `started_at` is stamped at claim,
immediately before the catalog injector reads `Practice.criteria` into the sandbox, so a revision an
admin appends while the sandbox runs is a rubric the detector never saw and is not pinned. (Residual
race: an edit landing in the few milliseconds between claim and injection can pin one revision early
— negligible next to the minutes-long sandbox window this closes.)

---

## 4. Known limits (documented, deliberate)

- **Pre-observation filters:** parser discards, the diff-scope filter, unknown-slug discards, and
  duplicate-key discards happen **before** an `Observation` exists; they are counted in logs only.
  The observation set is "validated, in-scope findings", not raw model output.
- **`NOT_APPLICABLE` abstentions** persist as observations but are never delivered and get no
  ledger row — they are abstentions, not withheld findings.
- **GOOD strengths** bound `SUPPORTING` to a `DELIVERED` unit may have been rendered only as a
  brief acknowledgement line, not verbatim. Exposure for strengths is coarser than for problems;
  precision evaluation should score BAD observations.
- **The repo mount is not part of `inputs_digest`** — `metadata.commit_sha` pins it. A replay must
  check out that SHA.
- **`prompt_digest` covers the shipped scaffolding**, not the per-job task prompt (PR number, repo
  name); those are in `agent_job.metadata` and the task envelope (which IS in `inputs_digest`).
- **`inputs_digest` is blind to the job's own id, and to nothing else by design.** Any other
  per-run-varying byte makes it unique per run and silently useless for grouping. The known drift source
  is unordered SQL feeding a context file — the catalogue (`PracticeCatalogInjector` sorts by slug),
  review threads and the repository inventory are pinned; **this is not an exhaustive sweep**, so a
  context provider that materialises an unordered or unstably-truncated result set reopens it. Verify
  against a real run before trusting a grouping query.
- **Legacy rows** (before these columns landed) have NULL digests and possibly NULL
  `practice_revision_id` — exclude them from evaluation rather than guessing.
