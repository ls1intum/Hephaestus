import type { PracticeReviewOverview } from "@/api/types.gen";

import {
	FINISHED_REVIEW_STATUSES,
	finishedCount,
	type OutcomeScope,
	type ReviewListTarget,
	scopeSearch,
} from "./review-outcomes";
import type { FeedbackSearch } from "./review-search";
import type { PracticeReviewOverviewState, ReviewCountState } from "./review-states";
import type { AttentionGroup } from "./ReviewAttention";

/** A count the overview already holds, in the state of the overview itself. */
export function overviewCount(
	overview: PracticeReviewOverviewState,
	count: (overview: PracticeReviewOverview) => number,
): ReviewCountState {
	return overview.status === "ready"
		? { status: "ready", total: count(overview.overview), stale: overview.stale }
		: overview;
}

/**
 * Feedback composed in `scope` whose delivery failed, in full or in part, out of the feedback sent
 * in it: the overview's own counts for the range on screen, so the Delivery card and the Feedback
 * tile beside it cannot disagree, and the count opens the list filtered to exactly those states.
 */
export function deliveryFailures(
	scope: OutcomeScope,
	overview: PracticeReviewOverviewState,
): AttentionGroup<ReviewCountState> {
	const search: Partial<FeedbackSearch> = {
		...scopeSearch(scope, "feedback"),
		deliveryState: ["FAILED", "PARTIALLY_FAILED"],
	};
	return {
		state: overviewCount(overview, ({ feedback }) => feedback.failed + feedback.partiallyFailed),
		list: { list: "feedback", search },
		of:
			overview.status === "ready"
				? overview.overview.feedback.delivered +
					overview.overview.feedback.partiallyDelivered +
					overview.overview.feedback.failed +
					overview.overview.feedback.partiallyFailed
				: undefined,
	};
}

/**
 * The reviews of the range that finished, completed, failed or timed out, as the Reviews tile counts
 * them, and the Reviews table filtered to exactly those.
 */
export function finishedInRange(
	overview: PracticeReviewOverviewState,
	scope: OutcomeScope,
): { state: ReviewCountState; list: ReviewListTarget } {
	const list: ReviewListTarget = {
		list: "runs",
		search: { ...scopeSearch(scope, "runs"), status: [...FINISHED_REVIEW_STATUSES] },
	};
	if (overview.status !== "ready") {
		return { state: overview, list };
	}
	const { reviews } = overview.overview;
	return {
		state: {
			status: "ready",
			total: finishedCount(reviews),
			stale: overview.stale,
		},
		list,
	};
}
