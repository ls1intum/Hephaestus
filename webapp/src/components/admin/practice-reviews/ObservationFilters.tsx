import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { statusFacetOptions } from "@/components/common/status-def";
import { RESULT_ORIGIN_DEFS } from "@/components/practice-vocabulary/result-origin-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { nonEmpty } from "@/lib/search-params";

import { listFacet } from "./AppliedFacetPills";
import type { PracticeFacetOption } from "./ResultFilters";
import { clearedScope, hasAppliedScope, readScope, scopeSearch } from "./review-scope";
import type { ObservationsSearch } from "./review-search";
import { ReviewFilterBar } from "./ReviewFilterBar";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";

const SEVERITY_OPTIONS = statusFacetOptions(SEVERITY_DEFS);
const ORIGIN_OPTIONS = statusFacetOptions(RESULT_ORIGIN_DEFS);

/** The facets this list owns, cleared: what the toolbar's Reset clears. */
export function clearedObservationFacets(): Partial<ObservationsSearch> {
	return { severity: undefined, origin: undefined };
}

/** Every filter on this list, the shared scope included: what the empty list's button clears. */
export function clearedObservationFilters(): Partial<ObservationsSearch> {
	return { ...clearedObservationFacets(), ...clearedScope() };
}

function hasObservationFacet(search: ObservationsSearch): boolean {
	return (search.severity?.length ?? 0) > 0 || (search.origin?.length ?? 0) > 0;
}

export function hasObservationFilter(search: ObservationsSearch): boolean {
	return hasObservationFacet(search) || hasAppliedScope("observations", readScope(search));
}

export interface ObservationFiltersProps {
	search: ObservationsSearch;
	/** Reports one changed facet. The caller sends the reader back to page one. */
	onPatch: (patch: Partial<ObservationsSearch>) => void;
	/** Clears this list's own facets. The shared scope stays. */
	onReset: () => void;
	/** Clears the shared scope, on this tab and every tab after it. */
	onClearScope: () => void;
	groups: FacetSource;
	practices: FacetSource<PracticeFacetOption>;
	people: ReviewPeople;
	total: number | undefined;
	record?: ScopeRecordFilter;
	/** The name of the person `subjectUserId` identifies, while that filter is on. */
	subjectName?: string;
}

export function ObservationFilters({
	search,
	onPatch,
	onReset,
	onClearScope,
	groups,
	practices,
	people,
	total,
	record,
	subjectName,
}: ObservationFiltersProps) {
	const severity = listFacet("Severity", SEVERITY_OPTIONS, search.severity, (values) =>
		onPatch({ severity: nonEmpty(values) }),
	);
	const origin = listFacet("Origin", ORIGIN_OPTIONS, search.origin, (values) =>
		onPatch({ origin: nonEmpty(values) }),
	);
	return (
		<ReviewFilterBar
			tab="observations"
			scope={readScope(search)}
			onChange={(patch) => onPatch(scopeSearch("observations", patch))}
			onClearScope={onClearScope}
			dateTitle="Recorded"
			groups={groups}
			practices={practices}
			people={people}
			personName={subjectName}
			record={record}
			facets={
				<>
					{severity.control}
					{origin.control}
				</>
			}
			pills={[...severity.pills, ...origin.pills]}
			hasFacet={hasObservationFacet(search)}
			onReset={onReset}
			total={total}
			noun={["problem", "problems"]}
		/>
	);
}
