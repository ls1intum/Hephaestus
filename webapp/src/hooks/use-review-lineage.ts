import { skipToken, useQuery } from "@tanstack/react-query";

import { getReviewLineageOptions } from "@/api/@tanstack/react-query.gen";
import type { GetReviewLineageData, ReviewLineage } from "@/api/types.gen";
import { ACTIVE_REVIEW_POLL_MS } from "@/components/admin/practice-reviews/review-search";
import { type PanelState, panelState } from "@/components/common/panel-state";
import { isActiveReview } from "@/components/practice-vocabulary/review-status-defs";
import { problemStatusOf } from "@/lib/problem-detail";
import { retryQuery } from "@/runtime/tanstack-query/query-defaults";

export type ReviewLineageTarget = GetReviewLineageData["query"];

/** Read lineage independently so its failure cannot hide the record itself. */
export function useReviewLineage(
	workspaceSlug: string,
	target: ReviewLineageTarget | undefined,
): PanelState<{ lineage: ReviewLineage }> | undefined {
	const options = getReviewLineageOptions({
		path: { workspaceSlug },
		query: target ?? { kind: "REVIEW" },
	});
	const query = useQuery({
		...options,
		queryFn: target === undefined ? skipToken : options.queryFn,
		retry: retryQuery,
		refetchInterval: ({ state }) =>
			state.status === "success" &&
			state.data?.nodes.some(
				(node) => node.review !== null && isActiveReview(node.review.status),
			) === true
				? ACTIVE_REVIEW_POLL_MS
				: false,
	});
	if (target === undefined || problemStatusOf(query.error) === 404) {
		return undefined;
	}
	return panelState(query, (lineage) => ({ status: "ready" as const, lineage }));
}
