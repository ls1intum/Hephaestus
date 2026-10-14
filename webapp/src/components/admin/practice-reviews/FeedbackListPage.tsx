import type { ReviewFeedback, ReviewFeedbackLocationCounts } from "@/api/types.gen";
import type { FacetSource } from "@/components/common/FacetMultiSelect";
import type { PanelState } from "@/components/common/panel-state";
import type { PagedListState } from "@/runtime/tanstack-query/infinite-list";

import {
	clearedFeedbackFacets,
	clearedFeedbackFilters,
	FeedbackFilters,
	hasFeedbackFilter,
} from "./FeedbackFilters";
import { FeedbackRecipientSummary } from "./FeedbackRecipientSummary";
import { FeedbackResults, type FeedbackResultsState } from "./FeedbackResults";
import { clearedScope } from "./review-scope";
import type { FeedbackSearch } from "./review-search";
import { ReviewListEnd } from "./ReviewListEnd";
import { ReviewListRefresh } from "./ReviewListRefresh";
import type { ReviewPeople } from "./ReviewPersonFacet";
import type { ScopeRecordFilter } from "./ReviewScopeFilters";
import { ReviewTabIntro } from "./ReviewTabIntro";

export interface FeedbackListPageProps {
	/** Only the recipient summary needs it: its counts link to the list narrowed to them. */
	workspaceSlug: string;
	search: FeedbackSearch;
	onSearchChange: (patch: Partial<FeedbackSearch>) => void;
	/** The feedback the current `search` selects, as far as it is loaded. */
	feedback: PagedListState<ReviewFeedback>;
	groups: FacetSource;
	practices: FacetSource;
	people: ReviewPeople;
	/**
	 * Everything addressed to the developer `search.recipientUserId` names, by location and delivery
	 * state, whatever else the list is filtered to. Read only while that filter is set.
	 */
	recipientCounts: PanelState<{ counts: ReviewFeedbackLocationCounts[] }>;
	/** The one record the scope is filtered to, by name, for its pill. */
	record?: ScopeRecordFilter;
}

function resultsState(
	rows: ReviewFeedback[],
	onClearFilters: (() => void) | undefined,
): FeedbackResultsState {
	if (rows.length > 0) {
		return { status: "ready", feedback: rows };
	}
	return onClearFilters
		? { status: "empty", filtered: true, onClearFilters }
		: { status: "empty", filtered: false };
}

export function FeedbackListPage({
	workspaceSlug,
	search,
	onSearchChange,
	feedback,
	groups,
	practices,
	people,
	recipientCounts,
	record,
}: FeedbackListPageProps) {
	const rows = feedback.status === "ready" ? feedback.rows : [];
	// Guarded on the filter being set: see `ResultsListPage`. Unfiltered, row zero is whoever
	// sorts first, and their name would be shown against a different person's id.
	const filteredRecipient = search.recipientUserId == null ? undefined : rows[0]?.recipient;
	const recipientName =
		filteredRecipient?.name ??
		filteredRecipient?.login ??
		people.options.find((person) => person.userId === search.recipientUserId)?.label;
	const hasFilter = hasFeedbackFilter(search);
	const reset = () => onSearchChange(clearedFeedbackFilters());

	return (
		<section aria-label="Feedback" className="space-y-4">
			<ReviewTabIntro tab="feedback" />
			<FeedbackFilters
				search={search}
				onPatch={onSearchChange}
				onReset={() => onSearchChange(clearedFeedbackFacets())}
				onClearScope={() => onSearchChange(clearedScope())}
				groups={groups}
				practices={practices}
				people={people}
				total={feedback.status === "ready" ? feedback.total : undefined}
				recipientName={recipientName}
				record={record}
			/>
			{search.recipientUserId !== undefined && (
				<FeedbackRecipientSummary
					workspaceSlug={workspaceSlug}
					recipientUserId={search.recipientUserId}
					recipientName={recipientName}
					state={recipientCounts}
				/>
			)}
			{feedback.status === "error" && (
				<FeedbackResults
					state={{ status: "error", error: feedback.error, onRetry: feedback.onRetry }}
				/>
			)}
			{feedback.status === "loading" && <FeedbackResults state={{ status: "loading" }} />}
			{feedback.status === "ready" && (
				<>
					<ReviewListRefresh failure={feedback.refreshFailure} noun="pieces of feedback" />
					<FeedbackResults state={resultsState(rows, hasFilter ? reset : undefined)} />
					{rows.length > 0 && <ReviewListEnd {...feedback} noun="feedback" />}
				</>
			)}
		</section>
	);
}
