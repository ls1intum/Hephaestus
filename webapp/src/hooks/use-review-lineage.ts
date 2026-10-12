import { keepPreviousData, skipToken, useQuery } from "@tanstack/react-query";

import { getPracticeReviewLineageOptions } from "@/api/@tanstack/react-query.gen";
import type { GetPracticeReviewLineageData, ReviewLineage } from "@/api/types.gen";
import { type PanelState, panelState } from "@/components/common/panel-state";
import { problemStatusOf } from "@/lib/problem-detail";
import { retryUnlessNotFound } from "@/runtime/tanstack-query/query-defaults";

/** What a lineage starts from: a record by id, or a piece of work by kind and id. */
export type ReviewLineageTarget = GetPracticeReviewLineageData["query"];

/**
 * How one record came about, for the chain beside its level. Read apart from the record itself, so
 * a failure here costs the reader the chain and not the record.
 *
 * Undefined when there is nothing to trace: a link that names no work, or work no review recorded
 * anything about (a 404, which asking again answers the same way). A story centred on another record
 * keeps the one it showed until the new one arrives, so it does not blank out between two steps.
 */
export function useReviewLineage(
	workspaceSlug: string,
	target: ReviewLineageTarget | undefined,
): PanelState<{ lineage: ReviewLineage }> | undefined {
	const options = getPracticeReviewLineageOptions({
		path: { workspaceSlug },
		query: target ?? { kind: "REVIEW" },
	});
	const query = useQuery({
		...options,
		queryFn: target === undefined ? skipToken : options.queryFn,
		placeholderData: keepPreviousData,
		retry: retryUnlessNotFound,
	});
	if (target === undefined || problemStatusOf(query.error) === 404) {
		return undefined;
	}
	return panelState(query, (lineage) => ({ status: "ready" as const, lineage }));
}
