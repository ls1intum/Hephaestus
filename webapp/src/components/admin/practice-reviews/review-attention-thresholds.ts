/**
 * Every threshold the overview's attention signals use, in one place, so no screen marks the same
 * figure at a different point. The values come from the research report "Admin Practice reviews:
 * which signals show where to look" (09.10.2026, sections 2 and 3.1), which set them against the
 * first course's data (intro course, 05.–08.10.2026).
 *
 * The overview's one colour rule: red means broken, and nothing else is coloured. A figure is red
 * when developers do not get their feedback and an admin can fix it now: feedback that failed to
 * deliver past its mark, and reviews held for budget. There is no amber. Advice, such as a long wait
 * or a practice marker, is said in words and an icon, in the colour of the text around it.
 */

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

/**
 * Markers on the overview's practices (report F8): "Never applies here" from 90 % of a practice's
 * observations not applicable, "Often undecided" from 30 % undetermined. Each only once the practice
 * has 20 observations in the range, so a handful of reviews never marks it. In the first course,
 * three practices were not applicable in every observation, and two undecided in a third to a half.
 */
export const PRACTICE_MARKER_THRESHOLDS = {
	neverAppliesShare: 0.9,
	oftenUndecidedShare: 0.3,
	minimumObservations: 20,
} as const;
