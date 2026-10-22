import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { statusFacetOptions } from "@/components/common/status-def";
import { REVIEW_STATUS_DEFS } from "@/components/practice-vocabulary/review-status-defs";
import { nonEmpty } from "@/lib/search-params";

import { listFacet } from "./AppliedFacetPills";
import type { PracticeFacetOption } from "./ResultFilters";
import { clearedScopeFilters, hasAppliedScope, readScope } from "./review-scope";
import type { ReviewsSearch } from "./review-search";
import { ReviewFilterBar } from "./ReviewFilterBar";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";

const STATUS_OPTIONS = statusFacetOptions(REVIEW_STATUS_DEFS);

function hasReviewFacet(search: ReviewsSearch): boolean {
	return (search.status?.length ?? 0) > 0;
}

export function hasReviewFilter(search: ReviewsSearch): boolean {
	return hasReviewFacet(search) || hasAppliedScope("reviews", readScope(search));
}

export function clearedReviewFacets(): Partial<ReviewsSearch> {
	return { status: undefined };
}

export function clearedReviewFilters(): Partial<ReviewsSearch> {
	return { ...clearedReviewFacets(), ...clearedScopeFilters() };
}

export interface ReviewFiltersProps {
	search: ReviewsSearch;
	onPatch: (patch: Partial<ReviewsSearch>) => void;
	onReset: () => void;
	onClearScope: () => void;
	groups: FacetSource;
	practices: FacetSource<PracticeFacetOption>;
	people: ReviewPeople;
	total: number | undefined;
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
