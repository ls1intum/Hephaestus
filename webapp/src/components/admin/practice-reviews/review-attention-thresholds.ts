// Reserve red for failed delivery or budget holds an admin can fix; advice stays neutral.

/**
 * The queue's oldest wait (report F1): marked in words from 30 minutes, the same wait the trace
 * calls slow. The old mark at 10 minutes sat at the course's median wait and lit up in every session.
 */
export const QUEUE_WAIT_THRESHOLDS = {
	warningSeconds: 30 * 60,
} as const;

/**
 * Report F4, over the range on screen: feedback that failed to deliver is red above 5 % of the
 * feedback sent; below, it is plain. A rate, not a count, so a longer range does not colour more.
 * The reviews that failed, timed out or whose results could not be processed (F3) stay plain: an
 * admin cannot fix a failed run from here, and a comment that could not be posted is already in
 * the feedback that failed to deliver.
 */
export const PROBLEM_THRESHOLDS = {
	deliveriesDestructiveAbove: 0.05,
} as const;

// Require enough results before marking a practice; small samples do not establish a pattern.
export const PRACTICE_MARKER_THRESHOLDS = {
	neverAppliesShare: 0.9,
	oftenUndecidedShare: 0.3,
	minimumResults: 20,
} as const;
