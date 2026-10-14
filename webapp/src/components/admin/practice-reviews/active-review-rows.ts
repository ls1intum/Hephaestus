import type { ReviewSummary } from "@/api/types.gen";
import { isActiveReview as isActiveStatus } from "@/components/practice-vocabulary/review-status-defs";

/** The statuses a review still moves through, and the only ones the list re-reads on a timer. */
export const ACTIVE_REVIEW_STATUSES = ["QUEUED", "RUNNING"] as const;

export const isActiveReview = (review: Pick<ReviewSummary, "status">) =>
	isActiveStatus(review.status);

/** How many active reviews one poll reads at most: the server's largest page. */
export const ACTIVE_REVIEW_POLL_SIZE = 100;

export interface ActiveRowsReconciliation {
	/** The loaded rows the poll has news of, by id, to replace in place. */
	updated: ReadonlyMap<string, ReviewSummary>;
	/**
	 * Whether the loaded pages must be read again once: a loaded review left the active set, so its
	 * new status is not in the poll, or a new review arrived among the loaded rows.
	 */
	refetch: boolean;
}

/**
 * What one poll of the active reviews means for the rows already loaded. The poll reads only the
 * reviews still queued or running, so an open list costs one small read every few seconds however
 * many pages it holds; the whole list is read again only when a review starts or stops being
 * active. A review older than every loaded row is not on screen, so its news is no reason to re-read.
 */
export function reconcileActiveRows(
	loaded: readonly ReviewSummary[],
	active: readonly ReviewSummary[],
	activeComplete: boolean,
	follows: (review: ReviewSummary) => boolean = isActiveReview,
): ActiveRowsReconciliation {
	const loadedById = new Map(loaded.map((review) => [review.id, review]));
	const activeIds = new Set(active.map((review) => review.id));
	const updated = new Map<string, ReviewSummary>();
	for (const review of active) {
		if (loadedById.has(review.id)) {
			updated.set(review.id, review);
		}
	}
	const oldestLoaded = Math.min(...loaded.map((review) => review.createdAt.getTime()));
	const arrived = active.some(
		(review) => !loadedById.has(review.id) && review.createdAt.getTime() >= oldestLoaded,
	);
	// A truncated poll cannot say a loaded review left the active set.
	const left =
		activeComplete && loaded.some((review) => follows(review) && !activeIds.has(review.id));
	return { updated, refetch: arrived || left };
}
