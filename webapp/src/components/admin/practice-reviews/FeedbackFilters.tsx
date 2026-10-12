import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { statusFacetOptions } from "@/components/common/status-def";
import { DELIVERY_STATE_DEFS } from "@/components/practice-vocabulary/delivery-outcome-defs";
import { FEEDBACK_LOCATION_DEFS } from "@/components/practice-vocabulary/feedback-location-defs";
import { WITHHOLDING_FAMILY_DEFS } from "@/components/practice-vocabulary/withholding-defs";
import { nonEmpty } from "@/lib/search-params";

import { listFacet } from "./AppliedFacetPills";
import type { PracticeFacetOption } from "./ObservationFilters";
import { clearedScope, hasAppliedScope, readScope, scopeSearch } from "./review-scope";
import type { FeedbackSearch, FeedbackSort } from "./review-search";
import { ReviewFilterBar } from "./ReviewFilterBar";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";
import { type ReviewSortItem, ReviewSortSelect } from "./ReviewSortSelect";

const OUTCOME_OPTIONS = statusFacetOptions(DELIVERY_STATE_DEFS);
// The channel facet offers locations: each channel split by the provider that shows it, so an admin
// can ask for GitHub or Slack alone. The channel-wide entries cover feedback whose provider is unknown.
const LOCATION_OPTIONS = statusFacetOptions(FEEDBACK_LOCATION_DEFS);
const WITHHELD_FAMILY_OPTIONS = statusFacetOptions(WITHHOLDING_FAMILY_DEFS);

const SORT_ITEMS: [ReviewSortItem<FeedbackSort>, ...ReviewSortItem<FeedbackSort>[]] = [
	{ value: "NEWEST", label: "Newest first" },
	{ value: "OLDEST", label: "Oldest first" },
];

/**
 * The facets this list owns, cleared: what the toolbar's Reset clears. `order` is excluded, as on the
 * observations list, and so is the shared scope, which "Clear scope" owns.
 */
export function clearedFeedbackFacets(): Partial<FeedbackSearch> {
	return {
		deliveryState: undefined,
		withheldFamily: undefined,
		location: undefined,
	};
}

/** Every filter on this list, the shared scope included: what the empty list's button clears. */
export function clearedFeedbackFilters(): Partial<FeedbackSearch> {
	return { ...clearedFeedbackFacets(), ...clearedScope() };
}

function hasFeedbackFacet(search: FeedbackSearch): boolean {
	return (
		(search.deliveryState?.length ?? 0) > 0 ||
		(search.withheldFamily?.length ?? 0) > 0 ||
		(search.location?.length ?? 0) > 0
	);
}

export function hasFeedbackFilter(search: FeedbackSearch): boolean {
	return hasFeedbackFacet(search) || hasAppliedScope("feedback", readScope(search));
}

export interface FeedbackFiltersProps {
	search: FeedbackSearch;
	/** Reports one changed facet. The caller sends the reader back to page one. */
	onPatch: (patch: Partial<FeedbackSearch>) => void;
	/** Clears this list's own facets. The shared scope stays. */
	onReset: () => void;
	/** Clears the shared scope, on this tab and every tab after it. */
	onClearScope: () => void;
	/** The groups whose practices' observations the feedback was written from. */
	groups: FacetSource;
	/** The practices whose observations the feedback was written from. */
	practices: FacetSource<PracticeFacetOption>;
	people: ReviewPeople;
	/** How many rows the filters currently select, or `undefined` while that is unknown. */
	total: number | undefined;
	/** The one record the scope is filtered to, by name; absent for none, or for a selection. */
	record?: ScopeRecordFilter;
	/**
	 * The name of the person `recipientUserId` identifies. It must name *that* person: reading it off
	 * the first row is right only while the filter is on.
	 */
	recipientName?: string;
}

export function FeedbackFilters({
	search,
	onPatch,
	onReset,
	onClearScope,
	groups,
	practices,
	people,
	total,
	record,
	recipientName,
}: FeedbackFiltersProps) {
	const scope = readScope(search);
	const outcome = listFacet("Outcome", OUTCOME_OPTIONS, search.deliveryState, (values) =>
		onPatch({ deliveryState: nonEmpty(values) }),
	);
	const channel = listFacet("Channel", LOCATION_OPTIONS, search.location, (values) =>
		onPatch({ location: nonEmpty(values) }),
	);
	const withheld = listFacet(
		"Why withheld",
		WITHHELD_FAMILY_OPTIONS,
		search.withheldFamily,
		(values) => onPatch({ withheldFamily: nonEmpty(values) }),
	);

	return (
		<ReviewFilterBar
			tab="feedback"
			scope={scope}
			onChange={(patch) => onPatch(scopeSearch("feedback", patch))}
			onClearScope={onClearScope}
			// "Composed" rather than "Date": this range filters when the feedback was written, which is
			// not when it was delivered and not when the observation behind it was made.
			dateTitle="Composed"
			groups={groups}
			practices={practices}
			people={people}
			personName={recipientName}
			record={record}
			facets={
				<>
					{outcome.control}
					{channel.control}
					{withheld.control}
					<ReviewSortSelect
						items={SORT_ITEMS}
						value={search.order}
						onChange={(order) => onPatch({ order })}
					/>
				</>
			}
			pills={[...outcome.pills, ...channel.pills, ...withheld.pills]}
			hasFacet={hasFeedbackFacet(search)}
			onReset={onReset}
			total={total}
			noun={["piece of feedback", "pieces of feedback"]}
		/>
	);
}
