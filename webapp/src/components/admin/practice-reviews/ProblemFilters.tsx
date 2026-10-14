import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { statusFacetOptions } from "@/components/common/status-def";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { nonEmpty } from "@/lib/search-params";

import { listFacet } from "./AppliedFacetPills";
import {
	disagreementFacet,
	hasAssessmentFilter,
	markFilterPills,
	type PracticeFacetOption,
} from "./ResultFilters";
import { clearedScope, hasAppliedScope, readScope } from "./review-scope";
import type { ProblemsSearch } from "./review-search";
import { ReviewFilterBar } from "./ReviewFilterBar";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";

const SEVERITY_OPTIONS = statusFacetOptions(SEVERITY_DEFS);

/** The facets this list owns, cleared: what the toolbar's Reset clears. */
export function clearedProblemFacets(): Partial<ProblemsSearch> {
	return {
		severity: undefined,
		currentness: undefined,
		markedIncorrect: undefined,
		accepted: undefined,
		disagreed: undefined,
	};
}

/** Every filter on this list, the shared scope included: what the empty list's button clears. */
export function clearedProblemFilters(): Partial<ProblemsSearch> {
	return { ...clearedProblemFacets(), ...clearedScope() };
}

function hasProblemFacet(search: ProblemsSearch): boolean {
	return (
		(search.severity?.length ?? 0) > 0 ||
		search.currentness !== undefined ||
		hasAssessmentFilter(search)
	);
}

export function hasProblemFilter(search: ProblemsSearch): boolean {
	return hasProblemFacet(search) || hasAppliedScope("problems", readScope(search));
}

export interface ProblemFiltersProps {
	search: ProblemsSearch;
	/** Reports one changed facet. The caller sends the reader back to page one. */
	onPatch: (patch: Partial<ProblemsSearch>) => void;
	/** Clears this list's own facets. The shared scope stays. */
	onReset: () => void;
	/** Clears the shared scope, on this tab and every tab after it. */
	onClearScope: () => void;
	groups: FacetSource;
	practices: FacetSource<PracticeFacetOption>;
	people: ReviewPeople;
	total: number | undefined;
	record?: ScopeRecordFilter;
	/** The name of the developer `developerId` identifies, while that filter is on. */
	subjectName?: string;
}

export function ProblemFilters({
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
}: ProblemFiltersProps) {
	const severity = listFacet("Severity", SEVERITY_OPTIONS, search.severity, (values) =>
		onPatch({ severity: nonEmpty(values) }),
	);
	const disagreement = disagreementFacet(search, onPatch);
	return (
		<ReviewFilterBar
			tab="problems"
			scope={readScope(search)}
			onChange={onPatch}
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
					{disagreement.control}
				</>
			}
			pills={[...severity.pills, ...disagreement.pills]}
			referencePills={markFilterPills(search, onPatch)}
			hasFacet={hasProblemFacet(search)}
			onReset={onReset}
			total={total}
			noun={["problem", "problems"]}
		/>
	);
}
