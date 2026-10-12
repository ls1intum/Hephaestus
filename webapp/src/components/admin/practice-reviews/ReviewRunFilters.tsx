import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { ReferenceFilterPill } from "@/components/common/ReferenceFilterPill";
import { statusFacetOptions } from "@/components/common/status-def";
import {
	NO_REVIEW_STARTED,
	RESULT_PROCESSING_DEFS,
	REVIEW_STATUS_FILTER_DEFS,
	type ReviewStatusFilter,
} from "@/components/practice-vocabulary/review-status-defs";
import { nonEmpty } from "@/lib/search-params";

import { listFacet } from "./AppliedFacetPills";
import type { PracticeFacetOption } from "./ResultFilters";
import { clearedScope, hasAppliedScope, readScope, scopeSearch } from "./review-scope";
import { listsWorkWithoutReview, type RunsSearch } from "./review-search";
import { ReviewFilterBar } from "./ReviewFilterBar";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";

const STATUS_OPTIONS = statusFacetOptions(REVIEW_STATUS_FILTER_DEFS);
const RESULT_PROCESSING_OPTIONS = statusFacetOptions(RESULT_PROCESSING_DEFS);

function hasRunFacet(search: RunsSearch): boolean {
	return (
		(search.status?.length ?? 0) > 0 ||
		(search.resultProcessing?.length ?? 0) > 0 ||
		search.finishedAfterMerge === true
	);
}

/** The scope tab the list is on: the reviews, or the work on which no review started. */
function runsTab(search: RunsSearch) {
	return listsWorkWithoutReview(search) ? "unreviewed" : "runs";
}

/** True when the reader has narrowed the list, which decides both the count's wording and whether
 * the empty state offers to clear anything. Derived here so the toolbar and the page it sits above
 * cannot disagree about what "filtered" means. */
export function hasRunFilter(search: RunsSearch): boolean {
	return hasRunFacet(search) || hasAppliedScope(runsTab(search), readScope(search));
}

/** The facets this list owns, cleared: what the toolbar's Reset clears. "Clear scope" owns the rest. */
export function clearedRunFacets(): Partial<RunsSearch> {
	return {
		status: undefined,
		resultProcessing: undefined,
		finishedAfterMerge: undefined,
		withoutReview: undefined,
	};
}

/**
 * Every filter on this list, the shared scope included. Exported so the list's empty state can
 * offer one "clear all" that cannot drift from the toolbar's two buttons.
 */
export function clearedRunFilters(): Partial<RunsSearch> {
	return { ...clearedRunFacets(), ...clearedScope() };
}

/**
 * The Status facet's change as a patch. "No review started" lists work instead of reviews, so it
 * never combines with a status a review has: the value chosen last wins, and choosing it clears the
 * filters only reviews have.
 */
export function statusPatch(
	previous: readonly ReviewStatusFilter[] | undefined,
	values: ReviewStatusFilter[],
): Partial<RunsSearch> {
	const wasWork = previous?.includes(NO_REVIEW_STARTED) === true;
	const isWork = values.includes(NO_REVIEW_STARTED);
	if (isWork && !wasWork) {
		return {
			status: [NO_REVIEW_STARTED],
			resultProcessing: undefined,
			finishedAfterMerge: undefined,
		};
	}
	// An address from before the status value says "no review started" as `withoutReview`.
	if (isWork && values.length > 1) {
		return {
			status: nonEmpty(values.filter((value) => value !== NO_REVIEW_STARTED)),
			withoutReview: undefined,
		};
	}
	return { status: nonEmpty(values), withoutReview: undefined };
}

export interface ReviewRunFiltersProps {
	search: RunsSearch;
	/** Reports one changed facet. The caller sends the reader back to page one. */
	onPatch: (patch: Partial<RunsSearch>) => void;
	/** Clears this list's own facets. The shared scope stays. */
	onReset: () => void;
	/** Clears the shared scope, on this tab and every tab after it. */
	onClearScope: () => void;
	groups: FacetSource;
	practices: FacetSource<PracticeFacetOption>;
	people: ReviewPeople;
	/** How many reviews, or pieces of work, the current filters match. Absent while on its way. */
	total: number | undefined;
	/** The one record the scope is filtered to, by name; absent for none, or for a selection. */
	record?: ScopeRecordFilter;
}

export function ReviewRunFilters({
	search,
	onPatch,
	onReset,
	onClearScope,
	groups,
	practices,
	people,
	total,
	record,
}: ReviewRunFiltersProps) {
	const work = listsWorkWithoutReview(search);
	const tab = runsTab(search);
	const onStatus = (values: ReviewStatusFilter[]) => onPatch(statusPatch(search.status, values));
	const status = listFacet("Status", STATUS_OPTIONS, search.status, onStatus);
	const processing = listFacet(
		"Result processing",
		RESULT_PROCESSING_OPTIONS,
		search.resultProcessing,
		(values) => onPatch({ resultProcessing: nonEmpty(values) }),
	);
	return (
		<ReviewFilterBar
			tab={tab}
			scope={readScope(search)}
			onChange={(patch) => onPatch(scopeSearch(tab, patch))}
			onClearScope={onClearScope}
			// A review matches a developer, practice or group when it recorded a result of them.
			// "Created", not "Started": a review can sit queued before a worker claims it. A provisional
			// word: "Requested" names the review a person asked for. Work no review started counts the
			// moments recorded on it, so the range filters when they were.
			dateTitle={work ? "Recorded" : "Created"}
			groups={groups}
			practices={practices}
			people={people}
			record={record}
			facets={
				<>
					{status.control}
					{/* A review can complete and still fail to process what it produced, which its status
					    alone reports as a success. Work no review started has no result to process. */}
					{!work && processing.control}
				</>
			}
			pills={[...status.pills, ...processing.pills]}
			referencePills={
				search.finishedAfterMerge === true && (
					<ReferenceFilterPill
						label="Finished after merge"
						value="Only"
						onClear={() => onPatch({ finishedAfterMerge: undefined })}
					/>
				)
			}
			hasFacet={hasRunFacet(search)}
			onReset={onReset}
			total={total}
			noun={work ? ["piece of work", "pieces of work"] : ["review", "reviews"]}
		/>
	);
}
