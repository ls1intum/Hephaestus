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
 * What the composition stage may spend: never more than its ceiling, and never the retry allowance,
 * which stays reserved for the review whether or not it turns out to need it. Goes negative once the
 * review has spent everything, which is the case the caller refuses on.
 */
export function compositionBudgetMs({ agentBudgetMs, elapsedMs, retryMs, compositionCeilingMs }) {
    return Math.min(compositionCeilingMs, agentBudgetMs - elapsedMs - retryMs);
}

/**
 * Whether to compose. Durable review state is the precondition — there is nothing to compose from
 * without it. A hard abort forfeits the stage outright, because a review that lost its turn needs its
 * retry allowance more than the reflection surface needs a message today. Past those two the answer is
 * the clock's: what the review left behind, not how it behaved on the way there.
 */
export function shouldCompose({ hasPersistedReviewState, hardAborted, budgetMs }) {
    return hasPersistedReviewState && !hardAborted && budgetMs >= COMPOSITION_MIN_BUDGET_MS;
}
