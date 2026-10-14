import { useInfiniteQuery } from "@tanstack/react-query";
import { createFileRoute } from "@tanstack/react-router";

import { listPracticeReviewResultObservationsInfiniteOptions } from "@/api/@tanstack/react-query.gen";
import { ObservationsListPage } from "@/components/admin/practice-reviews/ObservationsListPage";
import { practiceReviewsHead } from "@/components/admin/practice-reviews/review-levels";
import {
	type ObservationsSearch,
	observationsQuery,
	observationsSearchSchema,
	REVIEW_PAGE_SIZE,
} from "@/components/admin/practice-reviews/review-search";
import { useReviewScopeSources } from "@/hooks/use-review-scope-sources";
import { useScopeRecordFilter } from "@/hooks/use-scope-record-filter";
import { useSearchState } from "@/lib/search-params";
import { pagedListState } from "@/runtime/tanstack-query/infinite-list";
import { pagedModelParams } from "@/runtime/tanstack-query/spring-page";
import { useQueryReadRefusal } from "@/runtime/tanstack-query/use-query-read-refusal";

export const Route = createFileRoute(
	"/_authenticated/w/$workspaceSlug/admin/practices/reviews/observations",
)({
	validateSearch: observationsSearchSchema,
	head: practiceReviewsHead("Problems"),
	component: ObservationsListRoute,
});

function ObservationsListRoute() {
	const { workspaceSlug } = Route.useParams();
	const search = Route.useSearch();
	const setSearch = useSearchState();
	const updateSearch = (patch: Partial<ObservationsSearch>) => {
		void setSearch((previous) => ({ ...previous, ...patch }), { replace: true });
	};

	const listOptions = listPracticeReviewResultObservationsInfiniteOptions({
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

	return (
		<ObservationsListPage
			search={search}
			onSearchChange={updateSearch}
			observations={pagedListState(observationsQueryResult, (observation) => observation.id)}
			groups={groups}
			practices={practices}
			people={people}
			record={record}
		/>
	);
}
