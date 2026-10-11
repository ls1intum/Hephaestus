# ADR 0055: Practice scripts give leads through one contract and call models through the runner

**Status:** Accepted

**Date:** 2026-10-09

**Authors:** Felix T.J. Dietrich

**Builds on:** [ADR 0026](0026-per-purpose-agent-bindings-and-llm-governance.md) (per-purpose bindings),
[ADR 0036](0036-agent-runtime-runs-on-node-24.md) (one process for each script),
[ADR 0041](0041-compose-1x-kubernetes-2.md) (no replay)

## Context

A practice's script finds places for the reviewer to look before the practice is measured.
Before this decision, a script was a plain function of positional arguments, `(repo, diffFiles, metadata, contextDir, changeDir, contextReference)`.
It returned hints, metrics and directions.
It could not read why its practice was in the review, or what the previous review of the practice found.

Many practices need a judgment that a text pattern cannot make, such as "this comment only restates the code".
A model can make that judgment for a small, fixed set of answers at a low cost.
Embeddings and reranking can find near-duplicates and choose which candidates to show.

A script is code that a workspace admin can write.
A model call needs a credential, and the review's own job token reaches every review path of the LLM proxy.
Node's permission model does not restrict the network.
Thus, a script that holds a token can send any request with it.

The output of a model can be wrong, and the reviewed work can contain instructions to a model.
Neither may become a claim about a developer's work (`docs/contributor/practice-review-pipeline.mdx`).

## Decision drivers

- One contract for every script, bundled or written by a workspace admin.
- A script gives leads. The review's model judges the practice.
- A script never holds a credential.
- A model failure or a missing model never becomes a statement about the work.
- Model text never reaches the reviewer as wording, and leads never reach composition.
- Operators choose and pay for each model with the existing bindings, tiers and prices.
- Spend has a bound for each attempt that does not depend on the shared-model budget or the provider cap.

## Considered options for model calls

1. **Give each script process a narrow token.** The script would choose the model, the request fields and the retries.
   The runner would lose the token limits and the request allowlist.
2. **Store each model call in a journal and replay it.** ADR 0041 keeps no stored model output, and a replayed answer is not true for a changed prompt.
3. **One generic purpose with a kind column.** It changes the binding's unique key, both admin consoles and the routing adapter.
4. **The runner makes every call with a second credential, and each model kind is its own purpose.**

## Decision

### One contract

A script has one default export, `definePracticeScript({ meta, run })` from `lib/practice-script.ts`.
`meta` declares the contract version, the lead kinds, the model slots and a token limit.
The contract version is required and is `1`, the leads contract.
A later contract that changes what `run` returns takes the next number, so the runner never infers the contract from a result.
`run(ctx)` returns leads, facts, unrated items and directions.
Every bundled script uses this contract.

The positional form is retired.
The runner does not run a script whose default export is not a definition.
Such a script fails with the status `error` and one error line that tells the admin to rewrite it with `definePracticeScript`.
The review continues without its leads.

### Scripts give leads, and the model judges

A lead is a citation, a declared kind, scalar facts and an optional rating.
The runner keeps only a lead whose kind the script declares and whose citation the source holds.
It writes each quote from the source.
A lead reaches only the turn that measures its practice.
It never becomes an observation or feedback.

The review's model checks each lead against the practice's criteria and decides.
A script never decides that the practice is met.

### Cause and previous

The context tells a script why its practice is in the review, as `cause`:

- `moment`: the practice is bound to the moment of the review, and its review conditions match.
- `recheck`: the practice's current result about the author is not met, and the work changed after it.
- `requested`: a person asked for the review, or an admin started a review of earlier work.

The practice's moments and review conditions stay the only home of when a practice is reviewed.
A script reads the cause to do different work at different moments, and never decides when it runs.

`previous` is the practice's latest current result about the same person on the same work, or `null`.
It holds the outcome, the severity and the citations, and no model text or delivery state.
For a pull request, it holds the earlier head, and the runner gives the change since that head when the head is an ancestor.
The runner marks each lead on a line that this change adds.

The server stages the cause and the previous result beside each script.
The runner validates them, and the record of the work, before the script starts.
An input that is not valid fails the script, never the review.

### Models through the runner

Option 4.

The server mints a second job token, scope `llm_practice_script`, for each practice review attempt.
It expires with the review token.
The proxy accepts it only while the job is running, for the same attempt on the same worker.
The Spring Security chain checks the scope of each path.
The practice script token reaches only `/internal/llm/practice-script/{slot}/**`, and the review token cannot reach those paths.
Only the practice script runner holds it.

A script's model objects send each call to the runner over IPC.
The runner keeps only the allowed request fields, checks the token limits, and makes the call.

The `chat` slot is the practice review's own model.
`PRACTICE_DECISION`, `PRACTICE_EMBEDDING` and `PRACTICE_RERANKING` are purposes, each routed through the reviewed developer's tier ceiling like the review model.
A `PRACTICE_DECISION` binding also accepts an `openai-completions` model, because few self-hosted deployments serve the OpenAI Decisions API.
The runner then reads each answer from `top_logprobs`.

The proxy meters each call from a practice script for each attempt and model kind.
It keeps the buckets of the review's own row: input, cache reads, cache writes and output.
It also keeps the search units that a Cohere reranking call bills instead of tokens.
When the attempt ends, each kind other than chat becomes one ledger row.

Each attempt has a token limit and a search unit limit.
A search unit is not converted to tokens, because no token count is true for one.
The runner checks the token limit before each call.

The proxy enforces both limits.
It counts only calls that have returned, so the calls in flight together can end above a limit.
The proxy answers a spent limit, budget or cap with 402, because no rate limit uses that status.
[Cost and limits](../admin/ai-providers.mdx#cost-and-limits) owns the limits and the overshoot.

A script returns leads: a citation, a declared kind, scalar facts and an optional rating.
The runner writes each quote from the source, and a failure becomes a "Not rated" line with its reason.

## Consequences

- A workspace script in the positional form stops giving leads after the upgrade.
  Its review shows the error line until an admin rewrites the script.
  The bundled practices changed their scripts and criteria.
  Thus, each workspace that adopted one gets an update offer.
- A script can do different work for a moment, a recheck and a request.
  It can look first at what changed since the previous result.
- A script that a workspace admin wrote can use models and holds no credential.
- A leaked practice script token reaches only model calls of one job attempt.
  The two attempt limits bound its spend, above them by its calls in flight.
- A workspace that binds nothing loses nothing. Scripts with optional slots still give the leads that their code finds.
- Every practice review attempt gets the practice script token, because the chat slot always exists.
- Chat calls run on the review model, which can be a slow reasoning model.
- The practice script stage takes its time from the review runner. The [workspace ABI](../contributor/agent/workspace-abi.mdx#practice-script-validation-and-limits) gives the split.
- [What a script can reach](../contributor/practice-script-runner.mdx#what-a-script-can-reach) owns the threat model: which process holds which token.
- A priced reranking model can give an unpriced ledger row, because a price has rates for tokens only.
  This occurs when its provider bills search units or reports no tokens.
  The row keeps the search units.
  A model with **No metered API cost** records a confirmed $0.
- The connection form offers all five protocols, so an admin creates every connection for practice scripts in the UI.
- The server does not parse scripts, because a parser would only guess at what a script calls.
  A script declares its models when it runs.
  The review runner reports the status, leads and declared models of each script in `practice-scripts.json` in `out/`.
  The server keeps one `agent_job_practice_script_run` row for each script in each attempt.
  Thus, **AI models** knows which practices use a model only after the current script declared its models.
- The runner names the practice of each script call in `x-hephaestus-practice`.
  The proxy refuses a call without it.
  Usage is kept by practice, and the usage report splits each ledger row between practices by priced token weight.
  The ledger keeps one row for each model kind and attempt.
- The runner's result reports no tokens or calls. The proxy and the ledger own those counts.

## Revisit trigger

- A practice needs a cheaper chat model than the review model.
- A call from a practice script needs streaming or provider options that the request allowlist drops.
- Node gains a network grant that the image can use.
- The product starts to retain model output. This needs an ADR 0041 change first.
