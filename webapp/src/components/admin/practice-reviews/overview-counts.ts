import type { ReviewOverview } from "@/api/types.gen";

import {
	FINISHED_REVIEW_STATUSES,
	finishedCount,
	type OutcomeScope,
	type ReviewListTarget,
	scopeSearch,
} from "./review-outcomes";
import type { FeedbackSearch } from "./review-search";
import type { ReviewCountState, ReviewOverviewState } from "./review-states";
import type { AttentionGroup } from "./ReviewAttention";

/** A count the overview already holds, in the state of the overview itself. */
export function overviewCount(
	overview: ReviewOverviewState,
	count: (overview: ReviewOverview) => number,
): ReviewCountState {
	return overview.status === "ready"
		? { status: "ready", total: count(overview.overview), stale: overview.stale }
		: overview;
}

/**
 * Feedback composed in `scope` whose delivery failed, out of the feedback sent in it — delivered,
 * delivery unconfirmed, or failed: the overview's own counts for the range on screen, so the Delivery
 * card and the Feedback tile beside it cannot disagree, and the count opens the list filtered to
 * exactly that state. A package of which only some copies posted is delivered; its copy counts say
 * which copies failed.
 */
export function deliveryFailures(
	scope: OutcomeScope,
	overview: ReviewOverviewState,
): AttentionGroup<ReviewCountState> {
	const search: Partial<FeedbackSearch> = {
		...scopeSearch(scope, "feedback"),
		state: ["FAILED"],
	};
	return {
		state: overviewCount(overview, ({ feedback }) => feedback.failed),
		list: { list: "feedback", search },
		of:
			overview.status === "ready"
				? overview.overview.feedback.delivered +
					overview.overview.feedback.unconfirmed +
					overview.overview.feedback.failed
				: undefined,
	};
}

/**
 * The reviews of the range that finished, complete, partial or failed, as the Reviews tile counts
 * them, and the Reviews table filtered to exactly those.
 */
export function finishedInRange(
	overview: ReviewOverviewState,
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
