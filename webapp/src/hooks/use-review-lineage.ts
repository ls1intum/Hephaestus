import { skipToken, useQuery } from "@tanstack/react-query";

import { getReviewLineageOptions } from "@/api/@tanstack/react-query.gen";
import type { GetReviewLineageData, ReviewLineage } from "@/api/types.gen";
import { ACTIVE_REVIEW_POLL_MS } from "@/components/admin/practice-reviews/review-search";
import { type PanelState, panelState } from "@/components/common/panel-state";
import { isActiveReview } from "@/components/practice-vocabulary/review-status-defs";
import { problemStatusOf } from "@/lib/problem-detail";
import { retryUnlessNotFound } from "@/runtime/tanstack-query/query-defaults";

/** What a lineage starts from: an item, review, result, problem or feedback, by kind and id. */
export type ReviewLineageTarget = GetReviewLineageData["query"];

/**
 * How one record came about, for the chain beside its level. Read apart from the record itself, so
 * a failure here costs the reader the chain and not the record.
 *
 * Undefined when there is nothing to trace: a link that names no record, or an item no review
 * recorded anything about (a 404, which asking again answers the same way). Another record or
 * workspace never shows the previous chain: its review would be read under the new key.
 */
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
		retry: retryUnlessNotFound,
		refetchInterval: ({ state }) =>
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
