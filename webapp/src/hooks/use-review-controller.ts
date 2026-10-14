import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { toast } from "sonner";

import {
	cancelReviewMutation,
	getReviewOptions,
	listReviewFeedbackChecksOptions,
	listReviewsQueryKey,
} from "@/api/@tanstack/react-query.gen";
import type { FeedbackCheck, ReviewDetail } from "@/api/types.gen";
import {
	ACTIVE_REVIEW_POLL_MS,
	PRACTICE_REVIEW_READS,
} from "@/components/admin/practice-reviews/review-search";
import { type PanelState, panelState } from "@/components/common/panel-state";
import { isActiveReview } from "@/components/practice-vocabulary/review-status-defs";
import { problemDetailOf } from "@/lib/problem-detail";
import { invalidateWorkspaceReads } from "@/runtime/tanstack-query/invalidate-workspace-reads";
import { retryUnlessNotFound } from "@/runtime/tanstack-query/query-defaults";

/**
 * Everything the review's sheet needs, already resolved: the review, the feedback checks it ran,
 * and the cancel action. Its results and feedback are other records: the sheet does not read them.
 *
 * The screen is handed states, not queries. That a running review is re-asked for on a timer, and
 * that the timer stops by itself once the review and its result processing are done, is this
 * module's business alone.
 */
export interface ReviewController {
	review: PanelState<{ review: ReviewDetail }>;
	feedbackChecks: PanelState<{ checks: FeedbackCheck[] }>;
	onCancel: () => void;
	cancelPending: boolean;
}

/** A review is still moving while it waits or runs, or while a job's results are processed. */
function isMoving(review: ReviewDetail): boolean {
	return (
		isActiveReview(review.status) ||
		(review.runtime?.jobs ?? []).some((job) => job.processingStatus === "PENDING")
	);
}

export function useReviewController(workspaceSlug: string, reviewId: string): ReviewController {
	const queryClient = useQueryClient();
	const reviewQuery = useQuery({
		...getReviewOptions({ path: { workspaceSlug, reviewId } }),
		retry: retryUnlessNotFound,
		refetchInterval: (result) =>
			result.state.data !== undefined && isMoving(result.state.data)
				? ACTIVE_REVIEW_POLL_MS
				: false,
	});
	const checksOptions = listReviewFeedbackChecksOptions({ path: { workspaceSlug, reviewId } });
	const checksQuery = useQuery({ ...checksOptions, retry: retryUnlessNotFound });

	// The checks are written when the results are processed, and the review's row (its counts, which
	// the lists read) with its results: the review settling is what asks both once more.
	const settled = reviewQuery.data !== undefined && !isMoving(reviewQuery.data);
	// Undefined until the review is first read, so arriving at a settled review asks nothing again.
	const wasSettled = useRef<boolean | undefined>(undefined);
	useEffect(() => {
		const before = wasSettled.current;
		wasSettled.current = reviewQuery.data === undefined ? undefined : settled;
		if (before === false && settled) {
			void queryClient.invalidateQueries({ queryKey: checksOptions.queryKey });
			// Any read of the review's row, whatever its page size.
			void queryClient.invalidateQueries({
				queryKey: listReviewsQueryKey({ path: { workspaceSlug }, query: { reviewId } }),
			});
		}
	});

	// Cancelling answers the review's summary, not its detail: every read it can move is asked again.
	const cancelReview = useMutation({
		...cancelReviewMutation(),
		onSuccess: () => {
			void invalidateWorkspaceReads(queryClient, workspaceSlug, PRACTICE_REVIEW_READS);
			toast.success("Review canceled");
		},
		onError: (error) =>
			toast.error("We could not cancel the review", {
				description: problemDetailOf(error, "Try again in a moment."),
			}),
	});

	return {
		review: panelState(reviewQuery, (review) => ({ status: "ready" as const, review })),
		feedbackChecks: panelState(checksQuery, (checks) => ({ status: "ready" as const, checks })),
		onCancel: () => cancelReview.mutate({ path: { workspaceSlug, reviewId } }),
		cancelPending: cancelReview.isPending,
	};
}
