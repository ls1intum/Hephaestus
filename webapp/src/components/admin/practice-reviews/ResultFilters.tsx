import type { ReactNode } from "react";

import type { Practice } from "@/api/types.gen";

import type { FacetOption, FacetSource } from "@/components/common/FacetMultiSelect";
import { ReferenceFilterPill } from "@/components/common/ReferenceFilterPill";
import { statusFacetOptions } from "@/components/common/status-def";
import { OUTCOME_DEFS } from "@/components/practice-vocabulary/outcome-defs";
import {
	DISAGREEMENT_DEFS,
	type Disagreement,
	RESULT_MARK_DEFS,
	type ResultMarkKind,
} from "@/components/practice-vocabulary/result-mark-defs";
import { SEVERITY_DEFS } from "@/components/practice-vocabulary/severity-defs";
import { nonEmpty } from "@/lib/search-params";

import { type ListFacet, listFacet } from "./AppliedFacetPills";
import { clearedScope, hasAppliedScope, readScope } from "./review-scope";
import type { ResultSort, ResultsSearch } from "./review-search";
import { ReviewFilterBar } from "./ReviewFilterBar";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";
import { type ReviewSortItem, ReviewSortSelect } from "./ReviewSortSelect";

/** Every option wears the badge its rows wear; see the note on `FeedbackFilters`' facets. */
const OUTCOME_OPTIONS = statusFacetOptions(OUTCOME_DEFS);
const SEVERITY_OPTIONS = statusFacetOptions(SEVERITY_DEFS);
const DISAGREEMENT_OPTIONS = statusFacetOptions(DISAGREEMENT_DEFS);

/** The filters that the results and the problems lists share: an admin's marks and a developer's disagreement. */
export interface AssessmentSearch {
	markedIncorrect?: boolean;
	accepted?: boolean;
	disagreed?: boolean;
}

/** The one boolean the server filters by, as the facet's selection: both or neither is no filter. */
function disagreementSelection(disagreed: boolean | undefined): Disagreement[] {
	if (disagreed === undefined) {
		return [];
	}
	return [disagreed ? "DISAGREED" : "NOT_DISAGREED"];
}

export function disagreementFacet(
	search: AssessmentSearch,
	onPatch: (patch: AssessmentSearch) => void,
): ListFacet {
	return listFacet(
		"Disagreement",
		DISAGREEMENT_OPTIONS,
		disagreementSelection(search.disagreed),
		(values) => onPatch({ disagreed: values.length === 1 ? values[0] === "DISAGREED" : undefined }),
	);
}

const MARK_FILTERS: readonly { kind: ResultMarkKind; key: "markedIncorrect" | "accepted" }[] = [
	{ kind: "INCORRECT", key: "markedIncorrect" },
	{ kind: "ACCEPTED", key: "accepted" },
];

/**
 * The mark filters. No facet offers them: a link from a count sets them, so they show as pills that
 * clear them.
 */
export function markFilterPills(
	search: AssessmentSearch,
	onPatch: (patch: AssessmentSearch) => void,
): ReactNode {
	return MARK_FILTERS.map(({ kind, key }) => {
		const value = search[key];
		return (
			value !== undefined && (
				<ReferenceFilterPill
					key={key}
					label={RESULT_MARK_DEFS[kind].label}
					value={value ? "Only" : "Excluded"}
					onClear={() => onPatch({ [key]: undefined })}
				/>
			)
		);
	});
}

export function hasAssessmentFilter(search: AssessmentSearch): boolean {
	return (
		search.markedIncorrect !== undefined ||
		search.accepted !== undefined ||
		search.disagreed !== undefined
	);
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
		severity: undefined,
		currentness: undefined,
		markedIncorrect: undefined,
		accepted: undefined,
		disagreed: undefined,
	};
}

/** Every filter on this list, the shared scope included: what the empty list's button clears. */
export function clearedResultFilters(): Partial<ResultsSearch> {
	return { ...clearedResultFacets(), ...clearedScope() };
}

function hasResultFacet(search: ResultsSearch): boolean {
	return (
		(search.outcome?.length ?? 0) > 0 ||
		(search.severity?.length ?? 0) > 0 ||
		search.currentness !== undefined ||
		hasAssessmentFilter(search)
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
 * whether its feedback is turned off today, so the facet can leave it out.
 */
export interface PracticeFacetOption extends FacetOption {
	groupSlug?: string;
	turnedOff?: boolean;
}

export function practiceFacetOptions(
	practices:
		| readonly Pick<Practice, "slug" | "groupSlug" | "currentVersion" | "feedbackMode">[]
		| undefined,
	groups: readonly NamedGroup[] | undefined,
): PracticeFacetOption[] {
	return (practices ?? []).map((practice) => ({
		value: practice.slug,
		label: practice.currentVersion.name ?? practice.slug,
		description: (groups ?? []).find((group) => group.slug === practice.groupSlug)?.name,
		groupSlug: practice.groupSlug ?? undefined,
		turnedOff: practice.feedbackMode.effective === "OFF",
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
	 * The name of the developer `developerId` identifies. It must name *that* person: reading it off
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
	const disagreement = disagreementFacet(search, onPatch);
	return (
		<ReviewFilterBar
			tab="results"
			scope={readScope(search)}
			onChange={onPatch}
			onClearScope={onClearScope}
			// "Recorded", not "Date": this range filters when the result was recorded, while the same
			// control on the feedback list filters when the feedback was composed.
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
					{disagreement.control}
					{/* `Reset` leaves the sort alone: it orders the set and does not narrow it. */}
					<ReviewSortSelect
						items={SORT_ITEMS}
						value={search.order}
						onChange={(order) => onPatch({ order })}
					/>
				</>
			}
			pills={[...outcome.pills, ...severity.pills, ...disagreement.pills]}
			referencePills={markFilterPills(search, onPatch)}
			hasFacet={hasResultFacet(search)}
			onReset={onReset}
			total={total}
			noun={["result", "results"]}
		/>
	);
}
