import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import {
	createResultMarkMutation,
	getReviewResultOptions,
	removeResultMarkMutation,
} from "@/api/@tanstack/react-query.gen";
import type { GetReviewResultResponse, ResultMark } from "@/api/types.gen";
import { PRACTICE_REVIEW_READS } from "@/components/admin/practice-reviews/review-search";
import { type PanelState, panelState } from "@/components/common/panel-state";
import { RESULT_MARK_DEFS } from "@/components/practice-vocabulary/result-mark-defs";
import { problemDetailOf } from "@/lib/problem-detail";
import { invalidateWorkspaceReads } from "@/runtime/tanstack-query/invalidate-workspace-reads";

/**
 * Every read a result's marks move: the admin's lists, counts, lineage and bound feedback, and the
 * developer's own surfaces that count the result or deliver feedback about it.
 */
const READS_OF_RESULT_MARKS: ReadonlySet<string> = new Set([
	...PRACTICE_REVIEW_READS,
	"listOwnResults",
	"getOwnResult",
	"getOwnResultSummary",
	"getOwnPracticeStandings",
	"getOwnGroupStandings",
	"listOwnGroupReviews",
	"getOwnPracticeProfileOverview",
	"getOwnReview",
	"listOwnReviews",
	"listOwnItemReviews",
	"getOwnItemTrace",
	"getOwnPracticePageFeedback",
	"getOwnFeedbackResolutionCounts",
]);

export interface ResultController {
	result: PanelState<{ result: GetReviewResultResponse }>;
	/** Records an admin's assessment of the result, with their reason. */
	onMark: (kind: ResultMark["kind"], reason: string) => Promise<unknown>;
	/** Removes one active mark, with the reason it no longer holds. */
	onRemoveMark: (markId: string, reason: string) => Promise<unknown>;
	isMarking: boolean;
}

/** One result as its level reads it, and the marks an admin can add to it or remove from it. */
export function useResultController(workspaceSlug: string, resultId: string): ResultController {
	const queryClient = useQueryClient();
	const path = { workspaceSlug, resultId };
	const resultQuery = useQuery(getReviewResultOptions({ path }));
	const settled = {
		onError: (error: Error) => {
			void resultQuery.refetch();
			toast.error("We could not change this result", { description: problemDetailOf(error) });
		},
	};
	const mark = useMutation({
		...createResultMarkMutation(),
		...settled,
		onSuccess: (created) => {
			void invalidateWorkspaceReads(queryClient, workspaceSlug, READS_OF_RESULT_MARKS);
			toast.success(RESULT_MARK_DEFS[created.kind].label);
		},
	});
	const removal = useMutation({
		...removeResultMarkMutation(),
		...settled,
		onSuccess: () => {
			void invalidateWorkspaceReads(queryClient, workspaceSlug, READS_OF_RESULT_MARKS);
			toast.success("Mark removed");
		},
	});

	return {
		result: panelState(resultQuery, (result) => ({ status: "ready" as const, result })),
		isMarking: mark.isPending || removal.isPending,
		onMark: async (kind, reason) => mark.mutateAsync({ path, body: { kind, reason } }),
		onRemoveMark: async (markId, reason) =>
			removal.mutateAsync({ path: { ...path, markId }, body: { reason } }),
	};
}
