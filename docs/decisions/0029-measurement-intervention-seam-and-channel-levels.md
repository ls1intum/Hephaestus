# ADR 0029: Measurement and intervention are separate turns; a channel names where feedback lands, and its level follows

**Status:** Accepted
**Date:** 2026-08-16
**Authors:** Felix T.J. Dietrich
**Builds on:** [ADR 0021](0021-observations-feedback-synthesis-seam.md) (observations and feedback are separate records), [ADR 0022](0022-observation-presence-assessment-and-schema-cleanup.md) (observation = presence × assessment), [ADR 0007](0007-sandbox-spi-shape.md) (the Pi agent sandbox)
**Completes:** ADR 0021's _Open calls for the owner_ — "Hattie `level` derived, not stored", which was parked without a mapping. This ADR records the mapping and keeps it derived. It also revives F-2 / F-10, which the [2026-07-31 update](0021-observations-feedback-synthesis-seam.md#update--2026-07-31-issue-1423) recorded as _did not ship_: a `report_feedback` tool now exists.

> **IN_CHAT contract:** `notes.{situation, capability, evidenceSummary, inConversationSignal}` are notes _to_
> the mentor, not dialogue. The mentor chooses whether a question, direct feedback, or another move fits
> the live conversation; the brief does not force a canned Socratic sequence. This is the only accepted
> conversation-note schema.

## Context

Hephaestus does two different things to a piece of work, and until this change it did them in one
breath.

**Measurement** is recording what is there — _"this change adds a tax-exempt branch, and nothing in the
change tests it"_. That sentence is true or false, and you check it by opening the file. It is a
reading off an instrument, so it is kept forever and never edited.

**Intervention** is deciding to say something to a person and choosing the words — _"write the
assertion that distinguishes the new branch before you write the branch"_. That sentence cannot be
true or false. It can only be useful or useless, and its usefulness depends on who is reading it,
where, what they were told last week, and whether anyone has said it already.

The good version of each ruins the other. A measurement authored as advice bends toward whatever makes
good advice — an observation you can write a nice tip about gets reported, one you cannot gets quietly
dropped. And advice written at the moment of measurement can only ever be about the one thing just
measured: it cannot know this is the third time, it cannot know the developer was already told, and it
cannot decide to stay quiet.

Three further forces made the seam urgent rather than tidy:

- **Three lanes, three mechanisms, three moments.** In-context text was authored by the detector during
  measurement; in-app text by a second sandbox turn after the review; chat text by the mentor's own
  turn, days later. Only one of the three could see history, and no two shared a vocabulary — and the
  three lane _names_ did not share one either, which is the subject of its own subsection below.
- **Advice had no durable home on the lane that used it most.** `guidance` is not a column —
  `Observation` carries an `evidenceRationale` and no advice, per ADR 0021 and ADR 0022 — so for an
  inline-only delivery the words the developer received survived only inside `agent_job.output`,
  unindexed and unjoined. Every downstream question ("did we already say this?", "does this supersede
  that?") was being asked against text that was never stored.
- **A review of one merge request is a partial picture.** The fuller picture is assembled across many
  reviews, and eventually by a scheduled sweep with no artifact in hand. Whatever writes the words has
  to be the same machinery pointed at a different window.

## Decision drivers

- A measurement must stay falsifiable by opening the artifact, and must not be shaped by whether good
  advice can be written about it.
- The same fact must be able to read differently on each surface, or the per-surface split buys
  nothing; templating one `guidance` string cannot do this.
- Whatever decides what to say must see prior measurements, what was already delivered, and what is
  queued and still unread — none of which exists at measurement time.
- Deciding to say nothing must be a recorded decision, not a gap.
- The model must not be able to invent a citation, a file path, a line number, or a supersession
  target.
- A run whose composition never happened — budget spent, container killed, stage not requested — must
  still deliver what shipped before composition existed.
- The seam must survive being pointed at a workspace with no artifact, because the scheduled sweep is
  the next thing built on it.
- The three lane names must answer one question, so that placing a message on a lane is a lookup rather
  than an argument. Three names on three axes is what produced three vocabularies.

## Considered options

1. **Keep authoring intervention inside measurement.** Rejected: it is the status quo whose two
   failure modes are named above, and it structurally cannot express "we already said this", "this is
   the third time", or "last week's gap is closed".
2. **One composer turn per lane — three turns.** Rejected on two counts. The per-lane rules are only
   legible _in contrast_ ("the note on the work is already written; do not write it again" is
   unstatable in a single-lane prompt), and the budget has no room: the stage runs on what the review
   left over, ceilinged at `max(60s, 15% of the agent budget)` with a 30s floor
   (`pi-runner-timings.mjs`). Three turns means three prompt payloads and three histories for the same
   decision.
3. **Compose deterministically in Java from the observations.** Rejected: per-surface adaptation is a
   writing problem, and the interesting moves — withholding, superseding, naming a habit across three
   merge requests — are judgements about a person's record, not template selection.
4. **One LLM turn, in its own session, writing for every enabled lane at once.** Chosen.

## Decision

### The seam

A practice review is two phases inside one agent job.

**Phase 1 — measurement.** `report_observation` records a neutral `summary`, a discriminated `outcome`,
the exact evidence branch that outcome requires, and an `evidenceRationale`. It records no advice and no
self-reported confidence. Observations are immutable and kept forever. Java maps the wire outcome onto
the persisted presence/assessment vocabulary and assigns occurrence and recurrence identity.

**Phase 2 — intervention.** After the review's result file is final, `runCompositionStage` opens a
**separate in-memory session** — tools `read`, `grep`, `report_feedback`, no `bash`, no
`report_observation`, so it cannot touch the review's state and cannot re-bill the review's conversation.
It is prompted with `agent/feedback-composer.md` and reads this run's projected observations plus four
staged history files: prior observations, feedback already delivered, feedback **prepared but not yet
read**, and a Java-computed delta. It emits `out/feedback.json`, parsed by
`FeedbackCompositionResultParser` into `ComposedFeedbackUnit`s.

**The model proposes; Java admits.** Every gate that existed before the composer still sits after it:
`FeedbackAdmission.delivers(origin, tier, channel)`, `FeedbackChannelRouter`,
`InAppFeedbackRouter`, the in-context admission gate, and every per-lane cap. More context for the
model is not a reason to relax the gate; it is a reason the gate has more to refuse.

**Presence of `inputs/feedback-composition.json` is the on switch**, and it carries the lane set. Only
pull-request and issue reviews stage it, and never for a `BACKFILL` origin.

### The channel _is_ the level — recorded, still derived

`FeedbackChannel` is not three renderers of one message. Each value is a different level of Hattie &
Timperley's model, and that is the whole reason a 1:1 map from observations to feedback is the wrong
shape:

|                                      | `IN_CONTEXT`                                                              | `IN_APP`                                 | `IN_CHAT`                                                                                        |
| ------------------------------------ | ------------------------------------------------------------------------- | ---------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Lands                                | on the work artifact — pull request summary or inline note, issue comment | on the developer's own practice pages    | in a turn of a conversation, wherever it runs — the in-app mentor at `/w/:slug/mentor`, or Slack |
| Level                                | **task**                                                                  | **process**                              | **self-regulation**                                                                              |
| Answers                              | "what is wrong here?"                                                     | "what keeps happening in how I work?"    | "how would I have caught this myself?"                                                           |
| Audience                             | public — the team reads it                                                | private                                  | private, in a live turn                                                                          |
| Evidence is                          | a quoted line, spliced by the server from the citation                    | several pieces of work, named            | the same, held back until they answer                                                            |
| Next step is                         | one edit, in this change                                                  | a habit, for next time                   | a self-check they commit to                                                                      |
| Catalogue principle (`whyItMatters`) | appended verbatim by the server                                           | situated by the composer, never appended | not used                                                                                         |
| Anchor                               | required, and only onto this change's diff                                | forbidden                                | forbidden                                                                                        |

Two consequences are load-bearing and neither is a matter of taste. The public lane never says _"you
keep doing this"_, because that is a performance review in front of the team. The private lanes never
re-quote a line of code, because the line is on the merge request where it can be read in context.

**The level stays derived, never stored.** ADR 0021 parked this as an open call — _"Hattie `level`
derived, not stored; persist only if the RQ analysis needs it frozen at delivery time"_ — and left the
mapping unwritten, which is why three lanes drifted into three vocabularies. The mapping is now
recorded here and in `FeedbackChannel`'s own javadoc, and `feedback` gains no `level` column: it is a
function of `channel`, and a stored copy is a second truth that can disagree.

### The three names sit on one axis: where the feedback lands

`IN_CONTEXT`, `IN_APP`, `IN_CHAT`. Every value answers one question and only that question — _where does
this land?_ The previous set answered three: a place (`IN_CONTEXT`), a mechanism (`CONVERSATION`) and an
activity (`REFLECTION`). A set of values on three axes cannot be reasoned about as a set, which is how
three lanes drifted into three vocabularies for the same fact.

- **`IN_CONTEXT`** — placed directly on the work artifact: the pull request summary or an inline note,
  an issue comment.
- **`IN_CHAT`** — the dialogic channel: the unit prepares evidence-bound notes for a later turn in a
  conversation with the developer, wherever that conversation runs — the in-app mentor at
  `/w/:slug/mentor`, or Slack.
- **`IN_APP`** — the non-dialogic practice surface: the developer's own practice pages.

**"But the mentor also renders in-app" is answered by those two definitions, not by a screen.**
`IN_CHAT` is defined by being _dialogic_ — the unit informs a turn the developer can answer — never
by where it is rendered. `IN_APP` is defined by being the _non-dialogic_ practice surface — the unit is
written down, and reading it is the whole of the interaction. So the in-app mentor and Slack are one
channel because both are turns, and a second written surface inside Hephaestus is `IN_APP` rather than a
fourth value. The question that places a message is _is it a turn?_, asked before _which screen?_

### The tool contract

`report_feedback` takes exactly one unit per call:

- `channel`, restricted at the schema to the lanes this run enabled; `practiceSlug`, restricted to
  composable slugs; `basedOn`, at least one entry; `action` ∈ `NEW | SUPERSEDE | WITHHOLD`.
- `title` + `placement` + `nextStep` for `IN_CONTEXT`; `title` + `body` + `nextStep` for `IN_APP`; `notes.{situation, capability,
evidenceSummary, inConversationSignal}` for `IN_CHAT`, which is **notes to the mentor**, not a turn — the mentor writes
  every word of the turn, so nothing goes stale waiting to be raised.
- `withholdReason` ∈ `NO_MATERIAL_CHANGE | ALREADY_SAID | BELOW_BAR` for `WITHHOLD`.
- `placement: { kind: DIFF, observationId, citationIndex }` or `placement: { kind: ARTIFACT }` for
  `IN_CONTEXT` only. The occasion restricts which kinds are available.

Three refusals in that shape are the point:

- **No `presence`, `assessment`, `severity`, `confidence` or composer-typed citation.** An intervention
  that could carry a verdict would eventually be read back as one.
- **A diff placement names an observation and one of _its_ citation indexes** — never a path and never a
  line. The server resolves file, side and line from the staged citation and refuses a citation whose
  `anchorable` flag is false. An artifact placement carries no coordinates and must bind to a current
  observation of the same practice. The composer therefore cannot invent a locus or turn history alone
  into a public claim.
- **`SUPERSEDE` must name a `threadKey` that was staged in `prepared.json`.** The model may not invent
  a supersession target.

`ComposedFeedbackUnit.WithholdReason` is deliberately _not_ `FeedbackSuppressionReason`: the latter is
a database check constraint listing reasons the **server** withheld, and the two must not merge.

### Java does the bookkeeping; the model does the writing

`ObservationDelta.classify` is set arithmetic over recurrence keys, computed from the same read that
stages the observation history, grouped per artifact: the newest run of an artifact is "now", earlier
runs in the window are "before". It yields `NEW`, `RECURRING` (something moved), `UNCHANGED` (still
there, nothing moved) and `RESOLVED`.

`RESOLVED` is why the delta exists. _"The gap from last week is closed"_ originates from a locus that
is in the prior set and absent from the newest run — there is no current measurement to map from, so a
1:1 mapping can never emit it. `UNCHANGED` is the matching bar in the other direction: a message about
an unchanged locus must rest on a fact from this run, not on the fact that it is still there. A
vanished _strength_ is not classified at all, because crediting someone with fixing what was already
right is worse than saying nothing.

### Supersession — nothing received may be un-said

One key computation, `FeedbackThreadKey`, a SHA-256 over `kind ␟ locus ␟ recipient ␟ channel`. The
longitudinal lanes use `forPractice(practiceSlug, recipientUserId, channel)`, because a second card
about the same habit should replace the first rather than stack beside it; the in-context lane keys on
the artifact as before.

`FeedbackSupersession` is a per-channel compare-and-swap: find the latest row on the thread, then
`UPDATE feedback SET delivery_state='SUPERSEDED' WHERE id = ? AND delivery_state='PREPARED'`, retried
a bounded number of times while the head of the thread keeps moving. Three outcomes, and **the new unit
is written in all three**: `SUPERSEDED` (we claimed a queued message), `CONTINUED` (the recipient read
it first — it keeps its state and the new row still links back via `replaces_id`), `NEW` (nothing live
on the thread). `thread_key` stays non-unique: a thread is a chain of rows over time, and the
uniqueness that matters — at most one live `PREPARED` per thread — is the CAS's job, not a
constraint's.

`DELIVERED` is never retired on the longitudinal lanes. The in-context lane still retires a `DELIVERED`
summary, and that is not an exception: the provider comment is **edited in place**, so the old text
stops being visible and the ledger has to say so.

Two mechanisms with two jobs, not to be merged: **the CAS protects the ledger's integrity; the
per-practice cooldown protects the developer's inbox.** The CAS cannot stop a near-duplicate and is not
meant to.

### Availability floor

There is no measurement-text fallback. If composition cannot produce an admitted unit, the system records
that no feedback was prepared; it never turns an observation rationale into accidental advice. This keeps
the measurement/intervention seam true during failures as well as successful runs.

The lanes that have no such floor are made durable instead. `agent_job` carries a per-lane preparation
mark, and `FeedbackLanePreparationSweeper` re-runs any lane whose async listener did not — selecting on
the missing mark rather than on missing rows, because both lanes legitimately prepare nothing.

### Constraints a future author must meet

These are not deferred work items; they are conditions on work that touches these surfaces.

- **Any cross-person aggregate needs a small-cell rule _and its complement_ before it ships.** Nothing
  in the server implements k-anonymity today and this seam adds no cross-person aggregate. Suppressing
  small cells alone still leaks: when a category covers _almost all_ of a cohort, the complement is the
  small cell. Whoever builds the first cohort or team comparison meets both halves or does not ship it.
- **Per-person rate limiting across artifacts does not exist.** Cooldowns key on the artifact and the
  budget caps the workspace, so a prolific author still receives one review per piece of work. Real for
  a mentoring product, orthogonal to this seam, and not silently fixed by any cap named above.
- **Reviewer-attributed feedback does not reach reviewers.** `aboutUserId` resolves to the artifact
  author, and both longitudinal routers refuse the class by name — `REVIEWER_ATTRIBUTED` on `IN_APP`,
  `REVIEWER_DEFERRED` on `IN_CHAT`. Shipped catalogue practices judge reviewer conduct. Keep the
  refusals until attribution is fixed as its own piece of work; a router that stopped refusing before
  then would deliver one person's feedback to another.

## Consequences

**Easier.** The system can finally answer what it told a developer: every lane's words land on
`feedback.body`, bound to the observations the message rested on, with a channel and a delivery state.
Withholding is a recorded decision with a reason rather than an absence. A second card about the same
habit replaces the queued first one instead of stacking. Adding the scheduled workspace sweep is now a
job type and a cadence, not a new authoring path: the composition stage has no `bash`, never reads the
diff and takes no artifact, so it is already workspace-shaped — a sweep passes a narrower channel set
and stages an empty observations file.

**Neutral.** The measurement prompt keeps its guidance-authoring rules, because the fallback needs
them. Advice is still not persisted per observation, and should stay that way: advice is a property of
a _delivery_, and it now has a durable home on `feedback.body` for every lane. The naming half is paid
in one change rather than spread: the enum values, the `chk_feedback_channel` constraint, the
`practices/feedback/inapp` and `agent/handler/inapp` packages, `GET /practices/feedback/in-app` and
every `Reflection*` type move together. The alternative to paying it is a fourth rename.

**Harder, and deliberately so.** A run has two places text can come from, so "why does this comment
read like that?" is now two questions — check whether a composed unit was claimed before blaming the
prompt. The composer is a second LLM turn with its own budget and its own failure mode; it is bounded,
non-fatal and logged, but it is real cost. And three lanes reading one prompt means a prompt edit for
one lane can move the other two, which is exactly the coupling that makes the contrast statable and is
therefore not a defect to remove.

**Not built, on purpose.** The sweep scheduler (the seam only). Cross-practice grouping, which is
structurally blocked because `FeedbackAdmission` is evaluated per practice and two practices can carry
different effective tiers. Superseding a `DELIVERED` `IN_APP` card, which needs a read-receipt semantic
we only half have. `filterByDiffScope` still drops non-anchorable observations from the in-context lane
rather than merely routing them elsewhere.

## Revisit trigger

Any of the following re-opens this decision:

- **Composition stops being optional.** If in-context quality regresses far enough that the
  `reasoning` + `guidance` fallback is no longer an acceptable rendering, the floor has moved and the
  budget guarantee has to become a reservation rather than a leftover.
- **A fourth surface appears** that is neither on the artifact, nor a turn, nor the practice pages, and
  is not one of the three Hattie levels — the channel/level identity above is the thing it would break,
  and the mapping would then have to be stored rather than derived. A new _screen_ is not that: it joins
  `IN_APP` if it is written and `IN_CHAT` if it is a turn.
- **A cross-person aggregate is proposed**, which activates the k-anonymity constraint above before any
  code is written.
- **Reviewer attribution is fixed**, at which point the two named refusals become dead code and the
  recipient of a unit stops being derivable from the artifact's author.
- **The scheduled sweep runs and the delta proves too coarse.** `ObservationDelta` compares per
  artifact because the recurrence key folds `artifactId`; a sweep that wants "this locus across your
  work" needs a second identity, and that is a schema decision, not a prompt change.

## Where the current contracts live

This ADR is the decision and its rationale, not a field inventory. For what the code holds today:

- [Practice review pipeline](../contributor/practice-review-pipeline.mdx) — the two phases, the three
  channels, and what runs where;
- [Practice review glossary](../contributor/practice-review-glossary.mdx) — the vocabulary;
- [Practice review data model](../contributor/practice-feedback-schema.md) and the
  [generated database schema](../contributor/database-schema.mdx);
- `agent/feedback-composer.md`, `pi-runner.mjs`, and the `agent.handler.composition` package.

## Sources

- Hattie & Timperley, [_The Power of Feedback_](https://doi.org/10.3102/003465430298487), Review of
  Educational Research 77(1), 2007 — the four levels, and why praise directed at the person moves
  performance least. The three delivery channels are the task, process and self-regulation levels.
- Sadler, [_Formative assessment and the design of instructional systems_](https://doi.org/10.1007/BF00117714),
  Instructional Science 18, 1989 — feedback is only formative if the learner can use it to close the
  gap, which is why every message carries evidence _and_ a next step.
