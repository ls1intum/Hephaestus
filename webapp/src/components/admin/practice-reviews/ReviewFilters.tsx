import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { statusFacetOptions } from "@/components/common/status-def";
import { REVIEW_STATUS_DEFS } from "@/components/practice-vocabulary/review-status-defs";
import { nonEmpty } from "@/lib/search-params";

import { listFacet } from "./AppliedFacetPills";
import type { PracticeFacetOption } from "./ResultFilters";
import { clearedScope, hasAppliedScope, readScope } from "./review-scope";
import type { ReviewsSearch } from "./review-search";
import { ReviewFilterBar } from "./ReviewFilterBar";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";

const STATUS_OPTIONS = statusFacetOptions(REVIEW_STATUS_DEFS);

function hasReviewFacet(search: ReviewsSearch): boolean {
	return (search.status?.length ?? 0) > 0;
}

/** True when the reader has narrowed the list, which decides both the count's wording and whether
 * the empty state offers to clear anything. Derived here so the toolbar and the page it sits above
 * cannot disagree about what "filtered" means. */
export function hasReviewFilter(search: ReviewsSearch): boolean {
	return hasReviewFacet(search) || hasAppliedScope("reviews", readScope(search));
}

/** The facets this list owns, cleared: what the toolbar's Reset clears. "Clear scope" owns the rest. */
export function clearedReviewFacets(): Partial<ReviewsSearch> {
	return { status: undefined };
}

/**
 * Every filter on this list, the shared scope included. Exported so the list's empty state can
 * offer one "clear all" that cannot drift from the toolbar's two buttons.
 */
export function clearedReviewFilters(): Partial<ReviewsSearch> {
	return { ...clearedReviewFacets(), ...clearedScope() };
}

export interface ReviewFiltersProps {
	search: ReviewsSearch;
	/** Reports one changed facet. The caller sends the reader back to page one. */
	onPatch: (patch: Partial<ReviewsSearch>) => void;
	/** Clears this list's own facets. The shared scope stays. */
	onReset: () => void;
	/** Clears the shared scope, on this tab and every tab after it. */
	onClearScope: () => void;
	groups: FacetSource;
	practices: FacetSource<PracticeFacetOption>;
	people: ReviewPeople;
	/** How many reviews the current filters match. Absent while on its way. */
	total: number | undefined;
	/** The one record the scope is filtered to, by name; absent for none, or for a selection. */
	record?: ScopeRecordFilter;
}

export function ReviewFilters({
	search,
	onPatch,
	onReset,
	onClearScope,
	groups,
	practices,
	people,
	total,
	record,
}: ReviewFiltersProps) {
	const status = listFacet("Status", STATUS_OPTIONS, search.status, (values) =>
		onPatch({ status: nonEmpty(values) }),
	);
	return (
		<ReviewFilterBar
			tab="reviews"
			scope={readScope(search)}
			onChange={onPatch}
			onClearScope={onClearScope}
			// A review matches a developer, practice or group when it recorded a result of them.
			// "Created", not "Started": a review can sit queued before a worker claims it.
			dateTitle="Created"
			groups={groups}
			practices={practices}
			people={people}
			record={record}
			facets={status.control}
			pills={status.pills}
			hasFacet={hasReviewFacet(search)}
			onReset={onReset}
			total={total}
			noun={["review", "reviews"]}
		/>
	);
}
