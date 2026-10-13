import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";

import {
	getPracticeReviewOverviewOptions,
	listPracticeReviewObservationsInfiniteOptions,
} from "@/api/@tanstack/react-query.gen";
import { ObservationsListPage } from "@/components/admin/practice-reviews/ObservationsListPage";
import { PracticeObservationsSummary } from "@/components/admin/practice-reviews/PracticeObservationsSummary";
import { practiceReviewsHead } from "@/components/admin/practice-reviews/review-levels";
import {
	dateWindowQuery,
	type ObservationsSearch,
	observationsQuery,
	observationsSearchSchema,
	onePracticeQuery,
	REVIEW_PAGE_SIZE,
} from "@/components/admin/practice-reviews/review-search";
import { dateRangeLabel } from "@/components/common/DateRangeFacet";
import { panelState } from "@/components/common/panel-state";
import { useReviewScopeSources } from "@/hooks/use-review-scope-sources";
import { useScopeRecordFilter } from "@/hooks/use-scope-record-filter";
import { toDateRange } from "@/lib/date-range-search";
import { browserTimeZone } from "@/lib/dates";
import { useSearchState } from "@/lib/search-params";
import { pagedListState } from "@/runtime/tanstack-query/infinite-list";
import { keepSameSubject } from "@/runtime/tanstack-query/keep-same-subject";
import { pagedModelParams } from "@/runtime/tanstack-query/spring-page";
import { useQueryReadRefusal } from "@/runtime/tanstack-query/use-query-read-refusal";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/observations",
)({
	validateSearch: observationsSearchSchema,
	head: practiceReviewsHead("Observations"),
	component: ObservationsListRoute,
});

function ObservationsListRoute() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const setSearch = useSearchState();
	const updateSearch = (patch: Partial<ObservationsSearch>) => {
		void setSearch((previous) => ({ ...previous, ...patch }), { replace: true });
	};

	const listOptions = listPracticeReviewObservationsInfiniteOptions({
		path: { workspaceSlug },
		query: observationsQuery(search, REVIEW_PAGE_SIZE),
	});
	const observationsQueryResult = useInfiniteQuery({
		...listOptions,
		...pagedModelParams,
	});
	useQueryReadRefusal(
		listOptions.queryKey,
		observationsQueryResult.isError,
		observationsQueryResult.error,
	);
	const { groups, practices, people } = useReviewScopeSources(workspaceSlug);
	const record = useScopeRecordFilter(workspaceSlug, search);
	const summaryPractice = onePracticeQuery(search);

	return (
		<ObservationsListPage
			search={search}
			onSearchChange={updateSearch}
			observations={pagedListState(observationsQueryResult, (observation) => observation.id)}
			groups={groups}
			practices={practices}
			people={people}
			record={record}
			summary={
				summaryPractice === undefined ? undefined : (
					<PracticeSummaryRead
						workspaceSlug={workspaceSlug}
						practiceSlug={summaryPractice}
						practiceName={
							practices.options.find((option) => option.value === summaryPractice)?.label
						}
						search={search}
					/>
				)
			}
		/>
	);
}

/**
 * The counts of the one practice the list is filtered to, over the list's own days: the overview's
 * per-practice row, read for that window.
 */
function PracticeSummaryRead({
	workspaceSlug,
	practiceSlug,
	practiceName,
	search,
}: {
	workspaceSlug: string;
	practiceSlug: string;
	practiceName: string | undefined;
	search: ObservationsSearch;
}) {
	const window = dateWindowQuery(search);
	const query = useQuery({
		...getPracticeReviewOverviewOptions({
			path: { workspaceSlug },
			// A list with no first day has no date limit, so its counts reach back to the first review.
			query: {
				...(window.from === undefined ? { allTime: true } : { from: window.from }),
				to: window.to,
				zone: browserTimeZone(),
			},
		}),
		placeholderData: keepSameSubject({ workspaceSlug }),
	});
	const range = toDateRange(search);
	return (
		<PracticeObservationsSummary
			workspaceSlug={workspaceSlug}
			practiceSlug={practiceSlug}
			practiceName={practiceName}
			rangeLabel={(range && dateRangeLabel(range)) ?? "All time"}
			counts={panelState(query, (overview) => ({
				status: "ready" as const,
				stale: query.isPlaceholderData,
				counts: overview.practices.find((counts) => counts.practiceSlug === practiceSlug),
			}))}
		/>
	);
}
