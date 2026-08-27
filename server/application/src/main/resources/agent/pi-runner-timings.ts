export function deriveTimeouts(agentBudgetMs: number, compositionEnabled = false) {
	if (!Number.isFinite(agentBudgetMs) || agentBudgetMs <= 0) {
		throw new Error(`agentBudgetMs must be a positive number, got: ${agentBudgetMs}`);
	}

	const compositionMs = compositionEnabled ? Math.floor(agentBudgetMs * 0.15) : 0;
	const reviewBudgetMs = agentBudgetMs - compositionMs;
	const initialMs = Math.floor(reviewBudgetMs * 0.85);
	return {
		initialMs,
		retryMs: reviewBudgetMs - initialMs,
		compositionMs,
	};
}

export function deriveTurnTiming(remainingMs: number, remainingTurns: number) {
	if (!Number.isFinite(remainingMs) || remainingMs < 0) {
		throw new Error(`remainingMs must be a non-negative number, got: ${remainingMs}`);
	}
	if (!Number.isInteger(remainingTurns) || remainingTurns <= 0) {
		throw new Error(`remainingTurns must be a positive integer, got: ${remainingTurns}`);
	}

	const fairShareMs = Math.floor(remainingMs / remainingTurns);
	return {
		fairShareMs,
		softNudgeMs: Math.floor(fairShareMs * 0.6),
	};
}

export function deriveWorkstreamBudget(
	remainingMs: number,
	activeSlots: number,
	remainingWorkstreams: number,
) {
	if (!Number.isFinite(remainingMs) || remainingMs < 0) {
		throw new Error(`remainingMs must be a non-negative number, got: ${remainingMs}`);
	}
	if (!Number.isInteger(activeSlots) || activeSlots <= 0) {
		throw new Error(`activeSlots must be a positive integer, got: ${activeSlots}`);
	}
	if (!Number.isInteger(remainingWorkstreams) || remainingWorkstreams <= 0) {
		throw new Error(
			`remainingWorkstreams must be a positive integer, got: ${remainingWorkstreams}`,
		);
	}
	return Math.max(1, Math.floor((remainingMs * activeSlots) / remainingWorkstreams));
}
