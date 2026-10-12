import { keepPreviousData, useQuery } from "@tanstack/react-query";

import {
	listPracticeReviewFeedbackOptions,
	listPracticeReviewObservationsOptions,
	listPracticeReviewsOptions,
} from "@/api/@tanstack/react-query.gen";
import type { ReviewTabCounts } from "@/components/admin/practice-reviews/PracticeReviewsLayout";
import {
	carriedScope,
	type ReviewScopeSearch,
} from "@/components/admin/practice-reviews/review-scope";
import {
	feedbackQuery,
	observationsQuery,
	runsQuery,
} from "@/components/admin/practice-reviews/review-search";
import { QUERY_RETRIES, sessionRetriesQueries } from "@/runtime/tanstack-query/query-defaults";

/** One row is the smallest page the endpoints serve; only its total is read. */
const COUNT_PAGE = 1;

/** How long a count that failed every retry waits before it is asked for once more. */
export const FAILED_COUNT_RETRY_MS = 60_000;

const total = (page: { page?: { totalElements?: number } }) => page.page?.totalElements;

/**
 * What every count read shares. A count is decoration on a tab, so a failed read is retried quietly
 * and, after its last retry, asked for again a minute later instead of leaving the tab without its
 * count until a reload. A user view retries nothing, as every read of it is audited. Until the new read
 * answers, the last count stands (`keepPreviousData`); a count never known shows nothing.
 */
const COUNT_READ = {
	select: total,
	placeholderData: keepPreviousData,
	retry: (failureCount: number) => sessionRetriesQueries() && failureCount < QUERY_RETRIES,
	retryDelay: (attempt: number) => Math.min(1000 * 2 ** attempt, 8000),
	refetchInterval: (query: { state: { status: string } }) =>
		query.state.status === "error" && sessionRetriesQueries() ? FAILED_COUNT_RETRY_MS : false,
} as const;

/**
 * How many reviews, observations and pieces of feedback each tab lists under the shared scope (the
 * developer, groups, practices, dates and the tracked record), read as the total of a one-row page of
 * the tab's own endpoint. Each read is the query the tab's list sends, built from the search the tab's
 * link carries, so a count is what the table shows before its own facets narrow it. A count shows
 * nothing until it is known, and keeps the last one while the scope changes (`COUNT_READ`).
 */
export function useReviewTabCounts(
	workspaceSlug: string,
	search: ReviewScopeSearch,
): ReviewTabCounts {
	const path = { workspaceSlug };
	const runs = useQuery({
		...listPracticeReviewsOptions({
			path,
			query: runsQuery(carriedScope("runs", search), COUNT_PAGE),
		}),
		...COUNT_READ,
	});
	const observations = useQuery({
		...listPracticeReviewObservationsOptions({
			path,
			query: observationsQuery(carriedScope("observations", search), COUNT_PAGE),
		}),
		...COUNT_READ,
	});
	const feedback = useQuery({
		...listPracticeReviewFeedbackOptions({
			path,
			query: feedbackQuery(carriedScope("feedback", search), COUNT_PAGE),
		}),
		...COUNT_READ,
	});
	return { runs: runs.data, observations: observations.data, feedback: feedback.data };
}
