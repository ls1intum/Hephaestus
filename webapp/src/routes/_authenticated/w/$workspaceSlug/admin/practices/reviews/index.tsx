import { useInfiniteQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";

import { listReviewsInfiniteOptions } from "@/api/@tanstack/react-query.gen";
import { REVIEWABLE_ON_DEMAND } from "@/components/admin/practice-reviews/job-utils";
import {
	parsePracticeReviewLevels,
	practiceReviewsHead,
} from "@/components/admin/practice-reviews/review-levels";
import {
	ACTIVE_REVIEW_POLL_MS,
	REVIEW_PAGE_SIZE,
	type ReviewsSearch,
	reviewsQuery,
	reviewsSearchSchema,
} from "@/components/admin/practice-reviews/review-search";
import { ReviewsPage } from "@/components/admin/practice-reviews/ReviewsPage";
import { RefusalFixLink } from "@/components/practice-trace/RefusalFixLink";
import { TraceRefusalAlert } from "@/components/practice-trace/TraceRefusalAlert";
import { isActiveReview } from "@/components/practice-vocabulary/review-status-defs";
import { useRequestPracticeReview } from "@/hooks/use-request-practice-review";
import { useReviewScopeSources } from "@/hooks/use-review-scope-sources";
import { useScopeRecordFilter } from "@/hooks/use-scope-record-filter";
import { useSearchState } from "@/lib/search-params";
import { pagedListState } from "@/runtime/tanstack-query/infinite-list";
import { loadedPages, springPageParams } from "@/runtime/tanstack-query/spring-page";
import { useQueryReadRefusal } from "@/runtime/tanstack-query/use-query-read-refusal";

export const Route = createFileRoute("/_authenticated/w/$workspaceSlug/admin/practices/reviews/")({
	validateSearch: reviewsSearchSchema,
	head: practiceReviewsHead("Reviews"),
	component: ReviewsRoute,
});

function ReviewsRoute() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const setSearch = useSearchState();
	const updateSearch = (patch: Partial<ReviewsSearch>) => {
		void setSearch((previous) => ({ ...previous, ...patch }), { replace: true });
	};
	const { groups, practices, people } = useReviewScopeSources(workspaceSlug);
	const listOptions = listReviewsInfiniteOptions({
		path: { workspaceSlug },
		query: reviewsQuery(search, REVIEW_PAGE_SIZE),
	});
	const reviewsResult = useInfiniteQuery({
		...listOptions,
		...springPageParams,
		refetchInterval: (query) =>
			loadedPages(query.state.data).some((page) =>
				page.content.some((review) => isActiveReview(review.status)),
			)
				? ACTIVE_REVIEW_POLL_MS
				: false,
	});
	useQueryReadRefusal(listOptions.queryKey, reviewsResult.isError, reviewsResult.error);
	const reviews = pagedListState(reviewsResult, (row) => row.id);

	const review = useRequestPracticeReview(workspaceSlug, { showsInline: () => true });
	const record = useScopeRecordFilter(workspaceSlug, search);
	const front = parsePracticeReviewLevels(search.detail).at(-1);

	return (
		<ReviewsPage
			search={search}
			onSearchChange={updateSearch}
			reviews={reviews}
			requestReview={{
				canRequest: (item) => REVIEWABLE_ON_DEMAND.has(item.kind),
				onRequest: (itemId) => review.ask(itemId),
				isRequesting: (itemId) => review.asking === itemId,
			}}
			refusal={
				review.refusal && (
					<TraceRefusalAlert
						refusal={review.refusal.outcome}
						fix={
							review.refusal.outcome.reason !== null && (
								<RefusalFixLink
									workspaceSlug={workspaceSlug}
									reason={review.refusal.outcome.reason}
								/>
							)
						}
					/>
				)
			}
			groups={groups}
			practices={practices}
			people={people}
			record={record}
			openReviewId={front?.kind === "review" ? front.id : undefined}
		/>
	);
}
