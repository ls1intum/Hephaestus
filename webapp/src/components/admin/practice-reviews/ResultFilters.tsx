import type { FacetOption, FacetSource } from "@/components/common/FacetMultiSelect";
import { ReferenceFilterPill } from "@/components/common/ReferenceFilterPill";
import { statusFacetOptions } from "@/components/common/status-def";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import {
	DEVELOPER_RESPONSE_DEFS,
	type DeveloperResponse,
} from "@/components/practice-vocabulary/result-dispute-defs";
import { MARKED_INCORRECT_DEF } from "@/components/practice-vocabulary/result-invalidation-defs";
import { RESULT_ORIGIN_DEFS } from "@/components/practice-vocabulary/result-origin-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { nonEmpty } from "@/lib/search-params";

import { listFacet } from "./AppliedFacetPills";
import { clearedScope, hasAppliedScope, readScope, scopeSearch } from "./review-scope";
import type { ResultSort, ResultsSearch } from "./review-search";
import { ReviewFilterBar } from "./ReviewFilterBar";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";
import { type ReviewSortItem, ReviewSortSelect } from "./ReviewSortSelect";

/** Every option wears the badge its rows wear; see the note on `FeedbackFilters`' facets. */
const OUTCOME_OPTIONS = statusFacetOptions(OUTCOME_DEFS);
const SEVERITY_OPTIONS = statusFacetOptions(SEVERITY_DEFS);
const ORIGIN_OPTIONS = statusFacetOptions(RESULT_ORIGIN_DEFS);
const RESPONSE_OPTIONS = statusFacetOptions(DEVELOPER_RESPONSE_DEFS);

/** The one boolean the server filters by, as the facet's selection: both or neither is no filter. */
function responseSelection(disputed: boolean | undefined): DeveloperResponse[] {
	if (disputed === undefined) {
		return [];
	}
	return [disputed ? "DISPUTED" : "UNDISPUTED"];
}

function disputedFrom(values: DeveloperResponse[]): boolean | undefined {
	return values.length === 1 ? values[0] === "DISPUTED" : undefined;
}

const SORT_ITEMS: [ReviewSortItem<ResultSort>, ...ReviewSortItem<ResultSort>[]] = [
	{ value: "NEWEST", label: "Newest first" },
	{ value: "ACTIONABILITY", label: "Most actionable first" },
];

/**
 * The facets this list owns, cleared: what the toolbar's Reset clears. `order` is excluded, because
 * sorting does not narrow anything, and the shared scope is excluded, because "Clear scope" owns it.
 */
export function clearedResultFacets(): Partial<ResultsSearch> {
	return {
		outcome: undefined,
		invalidated: undefined,
		disputed: undefined,
		severity: undefined,
		origin: undefined,
	};
}

/** Every filter on this list, the shared scope included: what the empty list's button clears. */
export function clearedResultFilters(): Partial<ResultsSearch> {
	return { ...clearedResultFacets(), ...clearedScope() };
}

function hasResultFacet(search: ResultsSearch): boolean {
	return (
		(search.outcome?.length ?? 0) > 0 ||
		search.invalidated !== undefined ||
		search.disputed !== undefined ||
		(search.severity?.length ?? 0) > 0 ||
		(search.origin?.length ?? 0) > 0
	);
}

export function hasResultFilter(search: ResultsSearch): boolean {
	return hasResultFacet(search) || hasAppliedScope("results", readScope(search));
}

interface NamedGroup {
	slug: string;
	name: string;
}

export function groupFacetOptions(groups: readonly NamedGroup[] | undefined): FacetOption[] {
	return (groups ?? []).map((group) => ({ value: group.slug, label: group.name }));
}

/**
 * A practice as a facet offers it, with its group, so a chosen group can narrow the offer, and
 * whether it is turned off today, so the facet can leave it out.
 */
export interface PracticeFacetOption extends FacetOption {
	groupSlug?: string;
	turnedOff?: boolean;
}

export function practiceFacetOptions(
	practices:
		| readonly {
				slug: string;
				name: string;
				groupSlug?: string;
				autonomy?: { effective: string };
		  }[]
		| undefined,
	groups: readonly NamedGroup[] | undefined,
): PracticeFacetOption[] {
	return (practices ?? []).map((practice) => ({
		value: practice.slug,
		label: practice.name,
		description: (groups ?? []).find((group) => group.slug === practice.groupSlug)?.name,
		groupSlug: practice.groupSlug,
		turnedOff: practice.autonomy?.effective === "OFF",
	}));
}

export interface ResultFiltersProps {
	search: ResultsSearch;
	/** Reports one changed facet. The caller sends the reader back to page one. */
	onPatch: (patch: Partial<ResultsSearch>) => void;
	/** Clears this list's own facets. The shared scope stays. */
	onReset: () => void;
	/** Clears the shared scope, on this tab and every tab after it. */
	onClearScope: () => void;
	groups: FacetSource;
	practices: FacetSource<PracticeFacetOption>;
	people: ReviewPeople;
	total: number | undefined;
	/** The one record the scope is filtered to, by name; absent for none, or for a selection. */
	record?: ScopeRecordFilter;
	/**
	 * The name of the person `subjectUserId` identifies. It must name *that* person: reading it off
	 * the first row is right only while the filter is on.
	 */
	subjectName?: string;
}

export function ResultFilters({
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
}: ResultFiltersProps) {
	const outcome = listFacet("Outcome", OUTCOME_OPTIONS, search.outcome, (values) =>
		onPatch({ outcome: nonEmpty(values) }),
	);
	const severity = listFacet("Severity", SEVERITY_OPTIONS, search.severity, (values) =>
		onPatch({ severity: nonEmpty(values) }),
	);
	const response = listFacet(
		"Developer response",
		RESPONSE_OPTIONS,
		responseSelection(search.disputed),
		(values) => onPatch({ disputed: disputedFrom(values) }),
	);
	// Every origin is offered, live included, though a row badges only the other two: a reader
	// comparing live reviews with requested ones has to be able to pick either.
	const origin = listFacet("Origin", ORIGIN_OPTIONS, search.origin, (values) =>
		onPatch({ origin: nonEmpty(values) }),
	);
	return (
		<ReviewFilterBar
			tab="results"
			scope={readScope(search)}
			onChange={(patch) => onPatch(scopeSearch("results", patch))}
			onClearScope={onClearScope}
			// "Recorded", not "Date": this range filters `observedAt`, while the same control on the
			// feedback list filters when the feedback was composed.
			dateTitle="Recorded"
			groups={groups}
			practices={practices}
			people={people}
			personName={subjectName}
			record={record}
			facets={
				<>
					{outcome.control}
					{severity.control}
					{response.control}
					{origin.control}
					{/* `Reset` leaves the sort alone: it orders the set and does not narrow it. */}
					<ReviewSortSelect
						items={SORT_ITEMS}
						value={search.order}
						onChange={(order) => onPatch({ order })}
					/>
				</>
			}
			pills={[...outcome.pills, ...severity.pills, ...response.pills, ...origin.pills]}
			referencePills={
				search.invalidated !== undefined && (
					<ReferenceFilterPill
						label={MARKED_INCORRECT_DEF.label}
						value={search.invalidated ? "Only" : "Excluded"}
						onClear={() => onPatch({ invalidated: undefined })}
					/>
				)
			}
			hasFacet={hasResultFacet(search)}
			onReset={onReset}
			total={total}
			noun={["result", "results"]}
		/>
	);
}
