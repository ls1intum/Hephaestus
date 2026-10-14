import {
	type InfiniteData,
	type QueryKey,
	useInfiniteQuery,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";

import { listReviewsInfiniteOptions, listReviewsOptions } from "@/api/@tanstack/react-query.gen";
import { listReviews, type Options } from "@/api/sdk.gen";
import type { ListReviewsData, PageResponseDtoReviewSummary, ReviewSummary } from "@/api/types.gen";
import {
	ACTIVE_REVIEW_POLL_SIZE,
	ACTIVE_REVIEW_STATUSES,
	isActiveReview,
	reconcileActiveRows,
} from "@/components/admin/practice-reviews/active-review-rows";
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
import { useRequestPracticeReview } from "@/hooks/use-request-practice-review";
import { useReviewScopeSources } from "@/hooks/use-review-scope-sources";
import { useScopeRecordFilter } from "@/hooks/use-scope-record-filter";
import { useSearchState } from "@/lib/search-params";
import { pagedListState } from "@/runtime/tanstack-query/infinite-list";
import { loadedPages, springPageParams } from "@/runtime/tanstack-query/spring-page";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/runs",
)({
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
	const reviewsResult = useInfiniteQuery({
		...listReviewsInfiniteOptions({
			path: { workspaceSlug },
			query: reviewsQuery(search, REVIEW_PAGE_SIZE),
		}),
		...springPageParams,
	});
	useActiveReviewPoll(workspaceSlug, search, reviewsResult.data);

	// Every row asks for a review of its work. A refusal is said once above the list, and the hook
	// refreshes the list when a review starts.
	const review = useRequestPracticeReview(workspaceSlug, { showsInline: () => true });
	const record = useScopeRecordFilter(workspaceSlug, search);
	const front = parsePracticeReviewLevels(search.detail).at(-1);

	return (
		<ReviewsPage
			search={search}
			onSearchChange={updateSearch}
			reviews={pagedListState(reviewsResult, (row) => row.id)}
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

/**
 * While a loaded review is queued or running, re-read only the active reviews under the same
 * filters, every few seconds: their rows change in place, and the loaded pages are read again once,
 * when a review starts or stops being active. Re-reading every loaded page on the timer cost one
 * request per page per tab.
 */
function useActiveReviewPoll(
	workspaceSlug: string,
	search: ReviewsSearch,
	data: InfiniteData<PageResponseDtoReviewSummary> | undefined,
) {
	const listQuery = reviewsQuery(search, REVIEW_PAGE_SIZE);
	const listKey = listReviewsInfiniteOptions({
		path: { workspaceSlug },
		query: listQuery,
	}).queryKey;
	const loaded = loadedPages(data).flatMap((page) => page.content);
	// The active reviews the list's own filter keeps.
	const statuses = ACTIVE_REVIEW_STATUSES.filter(
		(status) => listQuery.status === undefined || listQuery.status.includes(status),
	);
	useLoadedRowsPoll(listKey, {
		polling: statuses.length > 0 && loaded.some(isActiveReview),
		request: {
			path: { workspaceSlug },
			query: { ...listQuery, status: [...statuses], size: ACTIVE_REVIEW_POLL_SIZE },
		},
		follows: isActiveReview,
	});
}

/**
 * One poll of the rows a list follows, applied to its loaded pages as it lands: the rows it has news
 * of change in place, and the pages are read again only when a followed row left or a new row arrived.
 */
function useLoadedRowsPoll(
	listKey: QueryKey,
	{
		polling,
		request,
		follows,
	}: {
		polling: boolean;
		request: Options<ListReviewsData>;
		follows: (review: ReviewSummary) => boolean;
	},
) {
	const queryClient = useQueryClient();
	useQuery({
		...listReviewsOptions(request),
		queryFn: async ({ signal }) => {
			const { data: polled } = await listReviews({
				...request,
				signal,
				throwOnError: true,
			});
			const rows = polled.content;
			const { updated, refetch } = reconcileActiveRows(
				queryClient
					.getQueryData<InfiniteData<PageResponseDtoReviewSummary>>(listKey)
					?.pages.flatMap((page) => page.content) ?? [],
				rows,
				polled.totalElements <= rows.length,
				follows,
			);
			if (refetch) {
				void queryClient.invalidateQueries({ queryKey: listKey, exact: true });
			} else if (updated.size > 0) {
				queryClient.setQueryData<InfiniteData<PageResponseDtoReviewSummary>>(listKey, (current) =>
					current === undefined
						? current
						: {
								...current,
								pages: current.pages.map((page) => ({
									...page,
									content: page.content.map((review) => updated.get(review.id) ?? review),
								})),
							},
				);
			}
			return polled;
		},
		enabled: polling,
		refetchInterval: polling ? ACTIVE_REVIEW_POLL_MS : false,
	});
}
