# ADR 0054: Practice scripts call models through the runner with a second credential

**Status:** Accepted

**Date:** 2026-10-09

**Authors:** Felix T.J. Dietrich

**Builds on:** [ADR 0026](0026-per-purpose-agent-bindings-and-llm-governance.md) (per-purpose bindings),
[ADR 0036](0036-agent-runtime-runs-on-node-24.md) (one process for each script),
[ADR 0041](0041-compose-1x-kubernetes-2.md) (no replay)

## Context

A practice's script finds places for the reviewer to look before the practice is measured.
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

- A script never holds a credential.
- A model failure or a missing model never becomes a statement about the work.
- Model text never reaches the reviewer as wording, and leads never reach composition.
- Operators choose and pay for each model with the existing bindings, tiers and prices.
- Spend has a bound for each attempt that does not depend on the shared-model budget or the provider cap.

## Considered options

1. **Give each script process a narrow token.** The script would choose the model, the request fields and the retries.
   The runner would lose the token limits and the request allowlist.
2. **Store each model call in a journal and replay it.** ADR 0041 keeps no stored model output, and a replayed answer is not true for a changed prompt.
3. **One generic purpose with a kind column.** It changes the binding's unique key, both admin consoles and the routing adapter.
4. **The runner makes every call with a second credential, and each model kind is its own purpose.**

## Decision

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
