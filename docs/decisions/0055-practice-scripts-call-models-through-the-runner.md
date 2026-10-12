# ADR 0055: Practice scripts are the review program, with one admission

**Status:** Accepted

**Date:** 2026-10-12

**Authors:** Felix T.J. Dietrich

**Builds on:**

- [ADR 0021](0021-observations-feedback-synthesis-seam.md): measurement before feedback.
- [ADR 0026](0026-per-purpose-agent-bindings-and-llm-governance.md): per-purpose bindings.
- [ADR 0029](0029-measurement-intervention-seam-and-channel-levels.md): measurement and intervention.
- [ADR 0036](0036-agent-runtime-runs-on-node-24.md): bounded Node processes.
- [ADR 0041](0041-compose-1x-kubernetes-2.md): no replay.
- [ADR 0047](0047-the-practice-profile-has-one-reader.md): one reader of a profile.
- [ADR 0050](0050-one-practice-standard-one-outcome.md): one outcome.

## Context

Before this decision, a practice's script was a plain function of positional arguments.
It returned hints, metrics and directions for the reviewer.
It could not read why its practice was in the review, or what the previous review found.

A first version of this decision kept scripts as a stage before the review.
A script gave leads, the review judged, and a second proxy credential reached the model routes of the scripts.
That design had five costs:

- Some practices have exact answers in complete captured records, such as a count of approvals. The script found them, but it could not record them.
- The script stage took its own share of the timeout, at most 90 seconds, before the review started.
- A later contract that returns results needed a second version, a second runner path, and a rewrite of each script.
- The result of a practice was one flat row. Each located problem, its severity and its evidence had no record of its own.
- The second credential protected a process boundary that no longer exists when one host runs the programs and the review.

Many practices need a judgment that a pattern cannot make, such as "this comment only restates the code".
A model can make that judgment for a small, fixed set of answers at a low cost.
The output of a model can be wrong, and the reviewed work can contain instructions to a model.
Neither may become a claim about a developer's work without the checks of admission.

## Decision drivers

- One contract for every program, bundled or written by a workspace admin, with no version to guess.
- Every producer reaches the same admission. No path skips the checks.
- A failure to capture or to run never becomes a claim about the work.
- One result per practice, with each located problem as its own record.
- One budget and one credential for each attempt.
- Operators choose and pay for each model with the existing bindings, tiers and prices.

## Considered options

### What a program returns

1. **Facts only.** A script computes facts, and the review model always judges.
   Every exact check then costs a model session, and a correct count can still be judged wrong.
2. **Leads only, the first version of this decision.** A script returns leads, and the review judges.
   It needs a separate stage, its own time split and its own credential.
   It cannot record a decided outcome.
3. **Two contracts.** Version 1 returns leads, and version 2 returns results.
   The host would run a version 1 script as a review with its leads.
   Admins rewrite each script twice, and the host keeps two producer paths and a grace runner.
4. **One result contract.** The program returns one candidate result.
   `ctx.review()` is a capability of the program, and leads are its input.

### How a result is stored

1. **Collapse problems into the flat row.** Loses the identity, severity, citations and recurrence of each problem.
2. **Store the candidate in the evidence JSON of the old row.** Keeps the bytes, but no reader, correction or erasure path understands them.
3. **One old row for each problem.** Repeats the verdict, multiplies standing and trends by the number of problems, and breaks one result per practice.
4. **A result and its observations.** One result for each practice, and one child for each problem.

### What a child can be

1. **A kind on each child: problem or good example.** Every reader of children must filter by kind. A missing filter posts an example as a problem.
2. **An outcome on each child.** A place cannot meet a practice alone, and holistic practices have no places.
3. **A richer taxonomy: suggestion, question, praise.** It records optional improvements and mixes message intent with evaluation.
4. **Problems only.** The example and the open questions belong to the result.

### How a program calls models

1. **Give each program process a narrow token.** The program would choose the model, the request fields and the retries. The host would lose the request allowlist.
2. **Store each model call in a journal and replay it.** ADR 0041 keeps no stored model output, and a replayed answer is not true for a changed prompt.
3. **One generic purpose with a kind column.** It changes the binding's unique key, both admin consoles and the routing adapter.
4. **A second job credential for the model routes of scripts, the first version of this decision.**
   With one host for programs and review, the host holds both credentials, so the second one protects nothing.
5. **The host makes every call with the one job credential, and each model kind is its own purpose.**

## Decision

Option 4 for what a program returns, option 4 for storage, option 4 for children, and option 5 for model calls.

### The program

Each admitted practice runs one program in each review attempt.
A practice with a script runs its script, a default export of `definePracticeScript({ meta, run })`.
A practice without a script runs the built-in program, `run: (ctx) => ctx.review()`.
A script that is not valid fails its practice. It never falls back to the built-in program.

`meta` declares only the direct models that the program needs: `models`, slot → `required` or `optional`.
It has no version, no lead kinds and no token limit.
`run(ctx)` returns `Promise<Candidate>`: one candidate result, discriminated by outcome.

A program can decide in code, call narrow models, call `ctx.review()`, or combine these steps.
`ctx.review(options)` opens a fresh review session for the practice.
Its input is optional leads, facts, directions, prepared context and limitations.
It returns a candidate to the program and has no effect of its own.
Only the final value of `run` reaches admission.
One review is active at a time, and a concurrent or nested call is refused.

The bundled scripts keep their evidence selection.
Each prepares its input in code and returns `ctx.review({ … })`.
They make no direct model calls.

A program cannot choose the workspace, the practice, the work, the scope, the developer, a recipient, or provenance.
The host attaches the practice identity, and the server takes the rest from the job credential and the job snapshot.

### The result and its observations

A candidate has an outcome, a summary, an explanation, result citations, evidence and coverage.

- `NOT_MET` has one or more problems. Each problem becomes one **observation**: severity, summary, explanation, citations, claim basis and an optional next step.
- Every observation is a problem. There is no observation kind. An optional improvement is never recorded.
- The result's severity is at least the severity of its worst observation. Admission uses the worst when the program gives none.
- `INFO` is a real gap with trivial impact. The app labels it "Trivial".
- One optional **example** belongs to a `MET` or `NOT_MET` result: one citation and a short note. It cannot cite a place that a problem cites.
- An **open question** is a located semantic doubt in the result's evidence. `UNDETERMINED` requires one. `NOT_MET` can have them. `MET` and `NOT_APPLICABLE` cannot.
- `MET` needs complete coverage. Coverage is either counted by the host from read receipts, or claimed by program code and kept as a claim.
- A failed read, a timeout, a spent budget or a program error is an execution reason, not an open question.

The server stores the result in `practice_result` and each observation in `practice_observation`.
Existing results keep their IDs, and each legacy `NOT_MET` result gets one observation with a new ID.
Feedback names its evidence in `feedback_evidence`: a result, and optionally one of its observations.
The [product vocabulary](../contributor/practice-feedback-language.md) owns the words.
[ADR 0047](0047-the-practice-profile-has-one-reader.md) still owns who reads a developer's results.

### One admission

Every producer reaches one endpoint: the built-in program, a script that decides in code, and a script that returns a review's result.
The host closes all program execution, and then submits the final candidates of the attempt in one call.
The server parses each candidate with the same rules as the host.
It then checks each quote against the captured sources, the sources against the review's grants, and each duty against the criteria.

A candidate is atomic.
If one problem, the example or one citation fails, the server withholds the whole result of that practice.
It never removes one problem and keeps a summary or severity that depended on it.
A withheld `NOT_MET` result never becomes `MET`.
Other practices continue.
A malformed submission, a wrong identity or an unauthorized source refuses the whole submission.

Composition reads only admitted results and their observations.
It never sends an open question, and it never sends an example alone.

### One budget and one credential

The host gets the whole attempt timeout, less the shutdown reserve of the runtime.
No time is reserved for programs before the review.
Practices run one after the other, and a program's deadline is the end of measurement.

The review sessions keep the existing work budget of each review turn.
Direct model calls of all programs share one account for each attempt: a token limit and a search unit limit.
The host reserves tokens before each direct call and settles them with the reported usage.
A call that fails, is cancelled, or reports no usage keeps its reservation.

The attempt has one job credential.
The host holds it, and a custom program's process holds none.
A program's model objects send each call to the host over IPC.
The host keeps only the allowed request fields, names the practice in `x-hephaestus-practice`, and calls `/internal/llm/practice-script/{slot}/**`.
The proxy refuses a mentor credential on that route, because the route bills a review attempt.

The `chat` slot is the practice review's own model.
`PRACTICE_DECISION`, `PRACTICE_EMBEDDING` and `PRACTICE_RERANKING` are purposes, each routed through the reviewed developer's tier ceiling like the review model.
A `PRACTICE_DECISION` binding also accepts an `openai-completions` model, and the host then reads each answer from `top_logprobs`.

The proxy meters each direct call for each attempt, model kind and practice.
It keeps the search units that a Cohere reranking call bills instead of tokens.
It enforces both limits, the shared-model budget and the provider cap, and answers a spent limit with 402.
[Cost and limits](../admin/ai-providers.mdx#cost-and-limits) owns the limits and the overshoot of calls in flight.

### No old contract

There is no leads contract, no compatibility wrapper, no shape guessing and no grace runner.
A script in the positional form of earlier releases fails its practice with one error line that tells the admin to rewrite it.
So does a script with `meta.version`, `meta.kinds` or `meta.tokens`, or one that returns leads, from a preview build of this decision.
At its first start, the server gives each unchanged copy of a bundled script its new version.
A changed copy keeps its text and fails until an admin acts.

## Consequences

- A practice with an exact answer in its records can record it without a model session.
- A practice that needs judgment keeps the review session, now with prepared input that the program chooses.
- Standing and trends count results, not observations. One result with five observations counts once.
- A reader that took an observation ID as a verdict ID now finds a result ID with the same value. A new observation ID never reuses an old one.
- One bad citation withholds a whole practice result. Admission records the reason, and the review page shows it.
- A slow program leaves less time for the practices after it.
- A custom script from an earlier release fails until an admin rewrites it. The release notes and the migration guide say so.
- The release renames the API routes, fields and tables of the old verdict. The webapp, the extension and custom clients upgrade together.
- The migration is a single-release contraction that the maintainer approved before 1.0. The [database migration guide](../contributor/database-migration.mdx#practice-result-transition-before-10) records it.
- The server does not parse scripts, because a parser would only guess at what a script calls. A program declares its models when it runs. The execution report records each admitted practice: program kind and digest, status, stop reason, declared models, calls and coverage reached.
- A workspace that binds no model for practice scripts loses nothing. The bundled scripts call no such model.
- [What a script can reach](../contributor/practice-script-runner.mdx#what-a-script-can-reach) owns the threat model.

## Revisit trigger

- A practice needs a whole-work review, a base tree, or a result state basis. Each is additive to this contract.
- Evaluations show that a direct path is unsafe for a practice. That practice then calls `ctx.review()`, or adds a bounded check.
- Admission needs to repair one refused problem instead of withholding the result.
- A practice needs a cheaper chat model than the review model.
- A direct call needs streaming or provider options that the request allowlist drops.
- The product starts to retain model output. This needs an ADR 0041 change first.
