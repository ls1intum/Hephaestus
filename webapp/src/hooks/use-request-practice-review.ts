import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";

import { requestReviewMutation } from "@/api/@tanstack/react-query.gen";
import type { CreateReviewRequest, ReviewRequestOutcome } from "@/api/types.gen";
import { PRACTICE_REVIEW_READS } from "@/components/admin/practice-reviews/review-search";
import { problemDetailOf } from "@/lib/problem-detail";
import { invalidateWorkspaceReads } from "@/runtime/tanstack-query/invalidate-workspace-reads";

const READS_OF_STARTED_REVIEW: ReadonlySet<string> = new Set([
	...PRACTICE_REVIEW_READS,
	"getOwnItemTrace",
	"listOwnItemReviews",
	"listOwnReviews",
	"getOwnPracticeProfileOverview",
]);

export interface ReviewRefusal {
	itemId: string;
	outcome: ReviewRequestOutcome;
}

interface RequestPracticeReviewOptions {
	/**
	 * Whether a level on screen shows this item, and so says its refusal inline; asked when the
	 * answer lands, since the reader may have moved on by then. Anywhere else a refusal is a toast.
	 */
	showsInline?: (itemId: string) => boolean;
}

/** Keep a refusal inline only while its item level is visible; navigation can change before the answer arrives. */
export function useRequestPracticeReview(
	workspaceSlug: string,
	{ showsInline }: RequestPracticeReviewOptions = {},
) {
	const queryClient = useQueryClient();
	const [refusal, setRefusal] = useState<ReviewRefusal>();
	// A level that closed before the answer landed can no longer say it.
	const mounted = useRef(false);
	useEffect(() => {
		mounted.current = true;
		return () => {
			mounted.current = false;
		};
	}, []);
	const mutation = useMutation({
		...requestReviewMutation(),
		onSuccess: (outcome, { path, body }) => {
			if (outcome.status === "REFUSED") {
				if (mounted.current && showsInline?.(body.itemId) === true) {
					setRefusal({ itemId: body.itemId, outcome });
				} else {
					toast.warning("No review was started", {
						description: outcome.reasonDescription ?? undefined,
					});
				}
				return;
			}
			void invalidateWorkspaceReads(queryClient, path.workspaceSlug, READS_OF_STARTED_REVIEW);
			toast.success("Review started");
		},
		onError: (error) =>
			toast.error("We could not request the review", {
				description: problemDetailOf(error, "Try again in a moment."),
			}),
	});
	return {
		ask: (itemId: string) => {
			// A new ask replaces the answer to the last one, so an alert never outlives its refusal.
			setRefusal(undefined);
			const body: CreateReviewRequest = { itemId };
			mutation.mutate({ path: { workspaceSlug }, body });
		},
		asking: mutation.isPending ? mutation.variables.body.itemId : undefined,
		refusal,
		/** Forgets the refusal, for a surface whose level moved on to something else. */
		forgetRefusal: () => setRefusal(undefined),
	};
}
