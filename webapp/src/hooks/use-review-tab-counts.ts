import { useQuery } from "@tanstack/react-query";

import {
	listPracticeReviewFeedbackOptions,
	listPracticeReviewObservationsOptions,
	listPracticeReviewResultsOptions,
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
	resultsQuery,
	runsQuery,
} from "@/components/admin/practice-reviews/review-search";
import { QUERY_RETRIES, sessionRetriesQueries } from "@/runtime/tanstack-query/query-defaults";

/** One row is the smallest page the endpoints serve; only its total is read. */
const COUNT_PAGE = 1;

/** Count reads refresh once a minute while the page is visible. */
const COUNT_REFRESH_MS = 60_000;

const total = (page: { page?: { totalElements?: number } }) => page.page?.totalElements;

/**
 * What every count read shares. Counts refresh quietly as reviews progress, including after a
 * failed read exhausts its retries. A user view repeats nothing, as every read of it is audited. A count of
 * another scope or workspace is never shown as this one's: until its own read answers, it shows nothing.
 */
const COUNT_READ = {
	select: total,
	retry: (failureCount: number) => sessionRetriesQueries() && failureCount < QUERY_RETRIES,
	retryDelay: (attempt: number) => Math.min(1000 * 2 ** attempt, 8000),
	refetchInterval: () => (sessionRetriesQueries() ? COUNT_REFRESH_MS : false),
} as const;

/**
 * How many reviews, results, observations and pieces of feedback each tab lists under the shared scope (the
 * developer, groups, practices, dates and the tracked record), read as the total of a one-row page of
 * the tab's own endpoint. Each read is the query the tab's list sends, built from the search the tab's
 * link carries, so a count is what the table shows before its own facets narrow it. A count shows
 * nothing until it is known for this scope (`COUNT_READ`).
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
	const results = useQuery({
		...listPracticeReviewResultsOptions({
			path,
			query: resultsQuery(carriedScope("results", search), COUNT_PAGE),
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
	return {
		runs: runs.data,
		results: results.data,
		observations: observations.data,
		feedback: feedback.data,
	};
}
