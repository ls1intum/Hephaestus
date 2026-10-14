import { WorkflowIcon } from "lucide-react";
import type { ReactNode } from "react";
import { ReviewListRefresh } from "./ReviewListRefresh";

import type { ItemRef, ReviewSummary } from "@/api/types.gen";
import type { FacetSource } from "@/components/common/FacetMultiSelect";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { ReviewDayTable, ReviewDayTableSkeleton } from "@/components/practice-trace/ReviewDayTable";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { itemName } from "@/lib/item-kinds";
import type { PagedListState } from "@/runtime/tanstack-query/infinite-list";

import { REQUEST_BLOCKED, type RowReviewRequest } from "./RequestReviewAction";
import type { PracticeFacetOption } from "./ResultFilters";
import { clearedScope } from "./review-scope";
import { REVIEW_PAGE_SIZE, type ReviewsSearch } from "./review-search";
import {
	clearedReviewFacets,
	clearedReviewFilters,
	hasReviewFilter,
	ReviewFilters,
} from "./ReviewFilters";
import { ReviewListEnd } from "./ReviewListEnd";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";
import { REVIEW_SUMMARY_COLUMNS, ReviewSummaryRow } from "./ReviewSummaryRow";
import { ReviewTabIntro } from "./ReviewTabIntro";

/** What a row can ask for, and what its ask answered: a requested review of its item. */
export interface ReviewRequestActions {
	/** Whether a person can ask for a review of this kind of item at all. */
	canRequest: (item: ItemRef) => boolean;
	onRequest: (itemId: string) => void;
	isRequesting: (itemId: string) => boolean;
}

/**
 * A row's Request review: blocked, with the reason, while a review of the same item is queued or
 * running among the loaded rows, or when nobody can ask about its kind of item.
 */
export function rowReviewRequest(
	actions: ReviewRequestActions,
	item: ItemRef,
	activeItems: ReadonlySet<string>,
): RowReviewRequest {
	const work = itemName(item);
	if (!actions.canRequest(item)) {
		return { work, blocked: REQUEST_BLOCKED.kind };
	}
	return {
		work,
		onRequest: () => actions.onRequest(item.id),
		requesting: actions.isRequesting(item.id),
		blocked: activeItems.has(item.id) ? REQUEST_BLOCKED.active : undefined,
	};
}

/** The item of every loaded review still queued or running: asking again would only join it. */
function activeItemsOf(reviews: readonly ReviewSummary[]): ReadonlySet<string> {
	return new Set(
		reviews.flatMap((review) =>
			review.status === "QUEUED" || review.status === "RUNNING" ? [review.item.id] : [],
		),
	);
}

export interface ReviewsPageProps {
	search: ReviewsSearch;
	onSearchChange: (patch: Partial<ReviewsSearch>) => void;
	/** The reviews the current search selects, as far as they are loaded. */
	reviews: PagedListState<ReviewSummary>;
	/** Request review on every row; left out where the screen offers no way to ask. */
	requestReview?: ReviewRequestActions;
	/** Why the latest ask started nothing, drawn by the route above the list. */
	refusal?: ReactNode;
	/** The one record the scope is filtered to, by name, for its pill. */
	record?: ScopeRecordFilter;
	/** The options of the shared scope's group, practice and developer facets. */
	groups: FacetSource;
	practices: FacetSource<PracticeFacetOption>;
	people: ReviewPeople;
	/** The review whose level is in front of the page, whose row keeps a bar on its edge. */
	openReviewId?: string;
}

const LIST_LABEL = "Practice reviews, newest first";

/**
 * The date filter's word for the time a row shows, when the review was created. "Requested" is the
 * origin of a review a person asked for, so the time cannot share it.
 */
const TIME_LABEL = "Created";

/**
 * The list of reviews, given its loaded pages. It neither fetches nor polls: the route asks for the
 * next page when the end of the list asks for it and keeps asking while a review is still running,
 * and this screen only ever sees the answer — which is why a still-running review looks the same
 * here as anywhere else.
 */
export function ReviewsPage({
	search,
	onSearchChange,
	reviews,
	requestReview,
	refusal,
	record,
	groups,
	practices,
	people,
	openReviewId,
}: ReviewsPageProps) {
	const rows = reviews.status === "ready" ? reviews.rows : [];
	const activeItems = activeItemsOf(rows);
	const hasFilter = hasReviewFilter(search);
	// The toolbar's Reset and the empty state's button are one action, not two copies of it.
	const reset = () => onSearchChange(clearedReviewFilters());
	let results: ReactNode;
	if (reviews.status === "error") {
		results = (
			<QueryErrorAlert
				error={reviews.error}
				title="We could not load reviews"
				onRetry={reviews.onRetry}
			/>
		);
	} else if (reviews.status === "loading") {
		results = (
			<ReviewDayTableSkeleton
				aria-label={LIST_LABEL}
				timeLabel={TIME_LABEL}
				columns={REVIEW_SUMMARY_COLUMNS}
				rows={REVIEW_PAGE_SIZE}
			/>
		);
	} else {
		results = (
			<>
				<ReviewListRefresh failure={reviews.refreshFailure} noun="reviews" />
				<ReviewDayTable
					aria-label={LIST_LABEL}
					timeLabel={TIME_LABEL}
					columns={REVIEW_SUMMARY_COLUMNS}
					reviews={rows}
					dateOf={(review) => review.createdAt}
					reviewKey={(review) => review.id}
					renderRow={(review) => (
						<ReviewSummaryRow
							review={review}
							open={review.id === openReviewId}
							request={requestReview && rowReviewRequest(requestReview, review.item, activeItems)}
						/>
					)}
					empty={
						<Empty>
							<EmptyHeader>
								<EmptyMedia variant="icon">
									<WorkflowIcon />
								</EmptyMedia>
								<EmptyTitle>
									{hasFilter ? "No reviews match these filters" : "No reviews yet"}
								</EmptyTitle>
								<EmptyDescription>
									{hasFilter
										? "Change or clear the filters to see more."
										: "Reviews appear when an event on the work starts one or someone requests one."}
								</EmptyDescription>
							</EmptyHeader>
							{hasFilter && (
								<EmptyContent>
									<Button variant="outline" size="sm" onClick={reset}>
										Clear all filters
									</Button>
								</EmptyContent>
							)}
						</Empty>
					}
				/>
				{rows.length > 0 && <ReviewListEnd {...reviews} noun="reviews" />}
			</>
		);
	}

	return (
		<section aria-label="Practice reviews" className="space-y-4">
			<ReviewTabIntro tab="reviews" />
			<ReviewFilters
				record={record}
				search={search}
				onPatch={onSearchChange}
				onReset={() => onSearchChange(clearedReviewFacets())}
				onClearScope={() => onSearchChange(clearedScope())}
				groups={groups}
				practices={practices}
				people={people}
				total={reviews.status === "ready" ? reviews.total : undefined}
			/>
			{refusal}
			{results}
		</section>
	);
}
