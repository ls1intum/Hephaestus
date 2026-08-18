/** Review attempt timing within the detector's budget. */
export function deriveTimeouts(agentBudgetMs, compositionEnabled = false) {
    const compositionMs = compositionEnabled ? Math.max(30_000, Math.floor(agentBudgetMs * 0.15)) : 0;
    const reviewBudgetMs = agentBudgetMs - compositionMs;
    const initialMs = Math.max(60_000, Math.floor(reviewBudgetMs * 0.85));
    return {
        initialMs,
        retryMs: Math.max(30_000, reviewBudgetMs - initialMs),
        softNudgeMs: Math.max(45_000, Math.floor(initialMs * 0.5)),
        compositionMs,
    };
}
