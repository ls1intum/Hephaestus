import type { ReactNode } from "react";

import type { ReviewObservation } from "@/api/types.gen";
import type { FacetSource } from "@/components/common/FacetMultiSelect";
import type { PagedListState } from "@/runtime/tanstack-query/infinite-list";

import {
	clearedObservationFacets,
	clearedObservationFilters,
	hasObservationFilter,
	ObservationFilters,
} from "./ObservationFilters";
import { ObservationResults, type ObservationResultsState } from "./ObservationResults";
import { clearedScope } from "./review-scope";
import type { ObservationsSearch } from "./review-search";
import { ReviewListEnd } from "./ReviewListEnd";
import { ReviewListRefresh } from "./ReviewListRefresh";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";
import { ReviewTabIntro } from "./ReviewTabIntro";

export interface ObservationsListPageProps {
	search: ObservationsSearch;
	onSearchChange: (patch: Partial<ObservationsSearch>) => void;
	/** The observations the current `search` selects, as far as they are loaded. */
	observations: PagedListState<ReviewObservation>;
	groups: FacetSource;
	practices: FacetSource;
	people: ReviewPeople;
	/** The one record the scope is filtered to, by name, for its pill. */
	record?: ScopeRecordFilter;
	/**
	 * Counts above the list for the one practice it is filtered to (`PracticeObservationsSummary`),
	 * drawn by the route.
	 */
	summary?: ReactNode;
}

function resultsState(
	rows: ReviewObservation[],
	onClearFilters: (() => void) | undefined,
): ObservationResultsState {
	if (rows.length > 0) {
		return { status: "ready", observations: rows };
	}
	return onClearFilters
		? { status: "empty", filtered: true, onClearFilters }
		: { status: "empty", filtered: false };
}

export function ObservationsListPage({
	search,
	onSearchChange,
	observations,
	groups,
	practices,
	people,
	record,
	summary,
}: ObservationsListPageProps) {
	const rows = observations.status === "ready" ? observations.rows : [];
	// Guarded on the filter being set, because that is the only condition under which the first row
	// names the filtered person — unfiltered, row zero is whoever happens to sort first, and the facet
	// would put a stranger's name on somebody else's id.
	const filteredSubject = search.subjectUserId == null ? undefined : rows[0]?.subject;
	const hasFilter = hasObservationFilter(search);
	const reset = () => onSearchChange(clearedObservationFilters());
	// The server sorts "Most actionable first" by outcome and severity, not by time.
	const byDay = search.order !== "ACTIONABILITY";

	return (
		<section aria-label="Practice review observations" className="space-y-4">
			<ReviewTabIntro tab="observations" />
			<ObservationFilters
				search={search}
				onPatch={onSearchChange}
				onReset={() => onSearchChange(clearedObservationFacets())}
				onClearScope={() => onSearchChange(clearedScope())}
				groups={groups}
				practices={practices}
				people={people}
				total={observations.status === "ready" ? observations.total : undefined}
				subjectName={filteredSubject?.name ?? filteredSubject?.login}
				record={record}
			/>
			{summary}
			{observations.status === "error" && (
				<ObservationResults
					state={{ status: "error", error: observations.error, onRetry: observations.onRetry }}
				/>
			)}
			{observations.status === "loading" && <ObservationResults state={{ status: "loading" }} />}
			{observations.status === "ready" && (
				<>
					<ReviewListRefresh failure={observations.refreshFailure} noun="observations" />
					<ObservationResults
						state={resultsState(rows, hasFilter ? reset : undefined)}
						byDay={byDay}
					/>
					{rows.length > 0 && <ReviewListEnd {...observations} noun="observations" />}
				</>
			)}
		</section>
	);
}
