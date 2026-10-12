import {
	type InfiniteData,
	useInfiniteQuery,
	useQuery,
	useQueryClient,
} from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";
import { useEffect, useRef } from "react";

import {
	listPracticeReviewsInfiniteOptions,
	listPracticeReviewsOptions,
	listTracedArtifactsInfiniteOptions,
} from "@/api/@tanstack/react-query.gen";
import { listPracticeReviews } from "@/api/sdk.gen";
import type { PagedModelReviewRunSummary } from "@/api/types.gen";
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
	listsWorkWithoutReview,
	type RunsSearch,
	runsQuery,
	runsSearchSchema,
	workWithoutReviewQuery,
} from "@/components/admin/practice-reviews/review-search";
import {
	type RequestableWork,
	ReviewRunsPage,
} from "@/components/admin/practice-reviews/ReviewRunsPage";
import { workRowKey } from "@/components/admin/practice-reviews/UnreviewedWorkRow";
import { TraceRefusalAlert } from "@/components/practice-trace/TraceRefusalAlert";
import { useRequestPracticeReview } from "@/hooks/use-request-practice-review";
import { useReviewScopeSources } from "@/hooks/use-review-scope-sources";
import { useScopeRecordFilter } from "@/hooks/use-scope-record-filter";
import { useSearchState } from "@/lib/search-params";
import { pagedListState } from "@/runtime/tanstack-query/infinite-list";
import { loadedPages, pagedModelParams } from "@/runtime/tanstack-query/spring-page";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/runs",
)({
	validateSearch: runsSearchSchema,
	head: practiceReviewsHead("Reviews"),
	component: ReviewRunsRoute,
});

const sameWork = (a: RequestableWork | undefined, b: RequestableWork) =>
	a !== undefined && a.artifactKind === b.artifactKind && a.artifactId === b.artifactId;

function ReviewRunsRoute() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const setSearch = useSearchState();
	const updateSearch = (patch: Partial<RunsSearch>) => {
		void setSearch((previous) => ({ ...previous, ...patch }), { replace: true });
	};
	const { groups, practices, people } = useReviewScopeSources(workspaceSlug);
	const withoutReview = listsWorkWithoutReview(search);
	const reviewsQuery = useInfiniteQuery({
		...listPracticeReviewsInfiniteOptions({
			path: { workspaceSlug },
			query: runsQuery(search, REVIEW_PAGE_SIZE),
		}),
		...pagedModelParams,
		enabled: !withoutReview,
	});
	useActiveReviewPoll(workspaceSlug, search, reviewsQuery.data, !withoutReview);
	const unreviewedOptions = listTracedArtifactsInfiniteOptions({
		path: { workspaceSlug },
		query: workWithoutReviewQuery(search, REVIEW_PAGE_SIZE),
	});
	const unreviewedQuery = useInfiniteQuery({
		...unreviewedOptions,
		...pagedModelParams,
		enabled: withoutReview,
	});

	// Every row asks for a review of its work. On the work without a review the row says a refusal
	// inline, and a started review moves the work off that list, so it is read again once the ask
	// settles; on the reviews a refusal is a toast and the hook refreshes the list.
	const review = useRequestPracticeReview(workspaceSlug, { showsInline: () => withoutReview });
	const queryClient = useQueryClient();
	const asking = review.asking !== undefined;
	const wasAsking = useRef(asking);
	useEffect(() => {
		const before = wasAsking.current;
		wasAsking.current = asking;
		if (before && !asking) {
			void queryClient.invalidateQueries({ queryKey: unreviewedOptions.queryKey });
		}
	});
	const record = useScopeRecordFilter(workspaceSlug, search);
	const front = parsePracticeReviewLevels(search.detail).at(-1);

	return (
		<ReviewRunsPage
			search={search}
			onSearchChange={updateSearch}
			reviews={pagedListState(reviewsQuery, (row) => row.id)}
			unreviewed={pagedListState(unreviewedQuery, workRowKey)}
			workspaceSlug={workspaceSlug}
			requestReview={{
				canRequest: (work) => REVIEWABLE_ON_DEMAND.has(work.artifactKind),
				onRequest: (work) =>
					review.ask({ artifactKind: work.artifactKind, artifactId: work.artifactId }),
				isRequesting: (work) => sameWork(review.asking, work),
				refusalFor: (work) =>
					review.refusal !== undefined && sameWork(review.refusal.work, work) ? (
						<TraceRefusalAlert
							refusal={review.refusal.outcome}
							workspaceSlug={workspaceSlug}
							canAdminister
						/>
					) : undefined,
			}}
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
	search: RunsSearch,
	data: InfiniteData<PagedModelReviewRunSummary> | undefined,
	enabled: boolean,
) {
	const queryClient = useQueryClient();
	const listQuery = runsQuery(search, REVIEW_PAGE_SIZE);
	const listKey = listPracticeReviewsInfiniteOptions({
		path: { workspaceSlug },
		query: listQuery,
	}).queryKey;
	const loaded = loadedPages(data).flatMap((page) => page.content ?? []);
	// The active reviews the list's own filter keeps. A held review is queued, so "On hold" alone polls
	// the held reviews, and beside listed statuses it adds them to the active ones among those.
	const heldListed = listQuery.onHold === true;
	const statuses = ACTIVE_REVIEW_STATUSES.filter((status) =>
		listQuery.status === undefined ? !heldListed : listQuery.status.includes(status),
	);
	const polling = enabled && (statuses.length > 0 || heldListed) && loaded.some(isActiveReview);
	const activeRequest = {
		path: { workspaceSlug },
		query: {
			...listQuery,
			status: statuses.length > 0 ? [...statuses] : undefined,
			size: ACTIVE_REVIEW_POLL_SIZE,
		},
	};
	useQuery({
		...listPracticeReviewsOptions(activeRequest),
		// Each poll is applied to the loaded pages as it lands: the rows it has news of change in
		// place, and the pages are read again only when a review started or stopped being active.
		queryFn: async ({ signal }) => {
			const { data: polled } = await listPracticeReviews({
				...activeRequest,
				signal,
				throwOnError: true,
			});
			const rows = polled.content ?? [];
			const { updated, refetch } = reconcileActiveRows(
				queryClient
					.getQueryData<InfiniteData<PagedModelReviewRunSummary>>(listKey)
					?.pages.flatMap((page) => page.content ?? []) ?? [],
				rows,
				(polled.page?.totalElements ?? rows.length) <= rows.length,
			);
			if (refetch) {
				void queryClient.invalidateQueries({ queryKey: listKey, exact: true });
			} else if (updated.size > 0) {
				queryClient.setQueryData<InfiniteData<PagedModelReviewRunSummary>>(listKey, (current) =>
					current === undefined
						? current
						: {
								...current,
								pages: current.pages.map((page) => ({
									...page,
									content: page.content?.map((review) => updated.get(review.id) ?? review),
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
