import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import {
	getPracticeReviewResultOptions,
	getPracticeReviewResultQueryKey,
	updatePracticeReviewResultValidityMutation,
} from "@/api/@tanstack/react-query.gen";
import type { GetPracticeReviewResultResponse, PracticeResultInvalidation } from "@/api/types.gen";
import { PRACTICE_REVIEW_READS } from "@/components/admin/practice-reviews/review-search";
import { type PanelState, panelState } from "@/components/common/panel-state";
import { problemDetailOf } from "@/lib/problem-detail";
import { invalidateWorkspaceReads } from "@/runtime/tanstack-query/invalidate-workspace-reads";

/** How often to re-read a correction the server is still settling; a final outcome is not re-read. */
const PROVIDER_COPY_POLL_MS: Partial<Record<PracticeResultInvalidation["providerCopy"], number>> = {
	PENDING: 10_000,
	UNRESOLVED: 60_000,
};

/**
 * Every read a result's validity moves: the admin's, and the developer's own surfaces that count it
 * or deliver feedback about it.
 */
const READS_OF_RESULT_VALIDITY: ReadonlySet<string> = new Set([
	...PRACTICE_REVIEW_READS,
	"getArtifactTrace",
	"listResults",
	"getPracticeResult",
	"getSummary",
	"listPracticeStandings",
	"listPracticeGroupStandings",
	"getPracticeGroupTrend",
	"listPracticeGroupReviewRuns",
	"getPracticeProfileOverview",
	"getPracticeProfileReviewRun",
	"listPracticeProfileReviewRuns",
	"getInAppFeedback",
	"getFeedbackResolutionCounts",
]);

interface ResultController {
	result: PanelState<{ result: GetPracticeReviewResultResponse }>;
	onChangeValidity: (valid: boolean, reason: string) => Promise<unknown>;
	isChangingValidity: boolean;
}

/** One result as its level reads it, and the one decision an admin can take on it. */
export function useResultController(
	workspaceSlug: string,
	practiceResultId: string,
): ResultController {
	const queryClient = useQueryClient();
	const resultQuery = useQuery({
		...getPracticeReviewResultOptions({ path: { workspaceSlug, practiceResultId } }),
		refetchInterval: (query) => {
			const providerCopy = query.state.data?.invalidations.at(0)?.providerCopy;
			return (providerCopy && PROVIDER_COPY_POLL_MS[providerCopy]) ?? false;
		},
	});
	const validity = useMutation({
		...updatePracticeReviewResultValidityMutation(),
		onSuccess: (updated, variables) => {
			queryClient.setQueryData(
				getPracticeReviewResultQueryKey({ path: { workspaceSlug, practiceResultId } }),
				updated,
			);
			void invalidateWorkspaceReads(queryClient, workspaceSlug, READS_OF_RESULT_VALIDITY);
			toast.success(variables.body.valid ? "Result restored" : "Result marked as incorrect");
		},
		onError: (error) => {
			void resultQuery.refetch();
			toast.error("We could not change this result", { description: problemDetailOf(error) });
		},
	});

	return {
		result: panelState(resultQuery, (result) => ({ status: "ready" as const, result })),
		isChangingValidity: validity.isPending,
		onChangeValidity: async (valid, reason) =>
			validity.mutateAsync({ path: { workspaceSlug, practiceResultId }, body: { valid, reason } }),
	};
}
