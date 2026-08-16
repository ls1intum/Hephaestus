// How one agent budget is divided, and what is left for the feedback-composition stage.
//
// Its own module because pi-runner.mjs cannot be imported: it reads /workspace and the environment at
// module scope, so the only way to exercise these rules is to have them somewhere a test can call.

/**
 * The smallest leftover worth starting a composition turn with. The stage is one prompt over two small
 * history files and the practice index; below this the hard abort lands mid-turn, so it would spend a
 * model call and persist nothing.
 */
export const COMPOSITION_MIN_BUDGET_MS = 30_000;

/**
 * 85% initial attempt / 15% retry, with a nudge at half the initial attempt.
 *
 * The nudge is a steer — it tells the agent to start persisting — and it fires at 42.5% of the whole
 * budget, which any review of real size passes. It is not a signal that a review is short of time.
 */
export function deriveTimeouts(agentBudgetMs) {
    const initialMs = Math.max(60_000, Math.floor(agentBudgetMs * 0.85));
    return {
        initialMs,
        retryMs: Math.max(30_000, agentBudgetMs - initialMs),
        softNudgeMs: Math.max(45_000, Math.floor(initialMs * 0.5)),
        compositionCeilingMs: Math.max(60_000, Math.floor(agentBudgetMs * 0.15)),
    };
}

/**
 * What the composition stage may spend: never more than its ceiling, and never an allowance the review
 * can still claim.
 *
 * The retry exists for exactly one failure — a review that reached the end without a valid
 * `out/result.json` — and the caller answers that question before it asks this one. Once the answer is
 * "the file is valid", the retry cannot fire, so reserving its allowance would set aside time nothing
 * can spend: with `initialMs + retryMs === agentBudgetMs` for any realistic budget, a review that used
 * its whole initial allowance would be left with a leftover of exactly 0 and compose nothing, on every
 * healthy run. `resultFileValid` therefore releases the reservation, and the healthy tail gets the full
 * ceiling.
 *
 * Defaults to `false`: a caller that has not established the file's validity yet must be told the
 * pessimistic number, because for that caller the retry really can still fire.
 *
 * Goes negative once the review has spent everything, which is the case the caller refuses on.
 */
export function compositionBudgetMs({
    agentBudgetMs,
    elapsedMs,
    retryMs,
    compositionCeilingMs,
    resultFileValid = false,
}) {
    const reservedForRetryMs = resultFileValid ? 0 : retryMs;
    return Math.min(compositionCeilingMs, agentBudgetMs - elapsedMs - reservedForRetryMs);
}

/**
 * Whether to compose. Durable review state is the precondition — there is nothing to compose from
 * without it. Past that the answer is the clock's: what the review left behind, not how it behaved on
 * the way there.
 *
 * A hard abort forfeits the stage only while the retry can still fire, which is the same question
 * {@link compositionBudgetMs} already answers with `resultFileValid`: the retry exists for exactly one
 * failure, a review that ended without a valid `out/result.json`. When the file IS valid the retry
 * cannot fire, so forfeiting on a hard abort sets aside nothing and only costs the developer a message.
 *
 * That is not a corner case, it is the ordinary shape of a review worth composing about. A review large
 * enough to spend its whole initial allowance is hard-aborted at the timeout *by design*, having already
 * persisted every finding through `report_finding` — so the old rule silenced precisely the runs with
 * the most to say, while the budget it was protecting could never be claimed.
 */
export function shouldCompose({ hasPersistedReviewState, hardAborted, resultFileValid = false, budgetMs }) {
    if (hardAborted && !resultFileValid) {
        return false;
    }
    return hasPersistedReviewState && budgetMs >= COMPOSITION_MIN_BUDGET_MS;
}
