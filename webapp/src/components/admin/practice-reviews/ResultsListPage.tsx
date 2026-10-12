import type { ReactNode } from "react";

import type { ReviewResult } from "@/api/types.gen";
import type { FacetSource } from "@/components/common/FacetMultiSelect";
import type { PagedListState } from "@/runtime/tanstack-query/infinite-list";

import {
	clearedResultFacets,
	clearedResultFilters,
	hasResultFilter,
	ResultFilters,
} from "./ResultFilters";
import { ResultRows, type ResultRowsState } from "./ResultRows";
import { clearedScope } from "./review-scope";
import type { ResultsSearch } from "./review-search";
import { ReviewListEnd } from "./ReviewListEnd";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";
import { ReviewTabIntro } from "./ReviewTabIntro";

export interface ResultsListPageProps {
	search: ResultsSearch;
	onSearchChange: (patch: Partial<ResultsSearch>) => void;
	/** The results the current `search` selects, as far as they are loaded. */
	results: PagedListState<ReviewResult>;
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
	rows: ReviewResult[],
	onClearFilters: (() => void) | undefined,
): ResultRowsState {
	if (rows.length > 0) {
		return { status: "ready", results: rows };
	}
	return onClearFilters
		? { status: "empty", filtered: true, onClearFilters }
		: { status: "empty", filtered: false };
}

export function ResultsListPage({
	search,
	onSearchChange,
	results,
	groups,
	practices,
	people,
	record,
	summary,
}: ResultsListPageProps) {
	const rows = results.status === "ready" ? results.rows : [];
	// Guarded on the filter being set, because that is the only condition under which the first row
	// names the filtered person — unfiltered, row zero is whoever happens to sort first, and the facet
	// would put a stranger's name on somebody else's id.
	const filteredSubject = search.subjectUserId == null ? undefined : rows[0]?.subject;
	const hasFilter = hasResultFilter(search);
	const reset = () => onSearchChange(clearedResultFilters());
	// The server sorts "Most actionable first" by outcome and severity, not by time.
	const byDay = search.order !== "ACTIONABILITY";

	return (
		<section aria-label="Practice review results" className="space-y-4">
			<ReviewTabIntro tab="results" />
			<ResultFilters
				search={search}
				onPatch={onSearchChange}
				onReset={() => onSearchChange(clearedResultFacets())}
				onClearScope={() => onSearchChange(clearedScope())}
				groups={groups}
				practices={practices}
				people={people}
				total={results.status === "ready" ? results.total : undefined}
				subjectName={filteredSubject?.name ?? filteredSubject?.login}
				record={record}
			/>
			{summary}
			{results.status === "error" && (
				<ResultRows
					state={{ status: "error", error: results.error, onRetry: results.onRetry }}
				/>
			)}
			{results.status === "loading" && <ResultRows state={{ status: "loading" }} />}
			{results.status === "ready" && (
				<>
					<ResultRows
						state={resultsState(rows, hasFilter ? reset : undefined)}
						byDay={byDay}
					/>
					{rows.length > 0 && <ReviewListEnd {...results} noun="results" />}
				</>
			)}
		</section>
	);
}
