import type { PracticeReviewObservationRow } from "@/api/types.gen";
import type { FacetSource } from "@/components/common/FacetMultiSelect";
import type { PagedListState } from "@/runtime/tanstack-query/infinite-list";

import {
	clearedObservationFacets,
	clearedObservationFilters,
	hasObservationFilter,
	ObservationFilters,
} from "./ObservationFilters";
import { ObservationRows, type ObservationRowsState } from "./ObservationRows";
import type { PracticeFacetOption } from "./ResultFilters";
import { clearedScope } from "./review-scope";
import type { ObservationsSearch } from "./review-search";
import { ReviewListEnd } from "./ReviewListEnd";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";
import { ReviewTabIntro } from "./ReviewTabIntro";

export interface ObservationsListPageProps {
	search: ObservationsSearch;
	onSearchChange: (patch: Partial<ObservationsSearch>) => void;
	/** The observations the current `search` selects, as far as they are loaded. */
	observations: PagedListState<PracticeReviewObservationRow>;
	groups: FacetSource;
	practices: FacetSource<PracticeFacetOption>;
	people: ReviewPeople;
	record?: ScopeRecordFilter;
}

function observationsState(
	rows: PracticeReviewObservationRow[],
	onClearFilters: (() => void) | undefined,
): ObservationRowsState {
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
}: ObservationsListPageProps) {
	const rows = observations.status === "ready" ? observations.rows : [];
	// Only while the filter is on does the first row name the filtered person.
	const filteredSubject = search.subjectUserId == null ? undefined : rows[0]?.subject;
	const reset = () => onSearchChange(clearedObservationFilters());

	return (
		<section aria-label="Practice review problems" className="space-y-4">
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
			{observations.status === "error" && (
				<ObservationRows
					state={{ status: "error", error: observations.error, onRetry: observations.onRetry }}
				/>
			)}
			{observations.status === "loading" && <ObservationRows state={{ status: "loading" }} />}
			{observations.status === "ready" && (
				<>
					<ObservationRows
						state={observationsState(rows, hasObservationFilter(search) ? reset : undefined)}
					/>
					{rows.length > 0 && <ReviewListEnd {...observations} noun="problems" />}
				</>
			)}
		</section>
	);
}
