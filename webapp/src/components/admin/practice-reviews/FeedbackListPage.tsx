import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { useEffect } from "react";
import { listPracticeReviewFeedbackOptions } from "@/api/@tanstack/react-query.gen";
import { DateRangeFacet } from "@/components/common/DateRangeFacet";
import { FacetMultiSelect } from "@/components/common/FacetMultiSelect";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { ReferenceFilterPill } from "@/components/common/ReferenceFilterPill";
import { ResultCount } from "@/components/common/ResultCount";
import { TablePagination } from "@/components/common/TablePagination";
import { DELIVERY_STATE_DEFS } from "@/components/practice-vocabulary/delivery-outcome-defs";
import {
	DELIVERY_PLACE_DEFS,
	FILTERABLE_PLACES,
} from "@/components/practice-vocabulary/delivery-place-defs";
import { statusFacetOptions } from "@/components/practice-vocabulary/status-def";
import { WITHHOLDING_FAMILY_DEFS } from "@/components/practice-vocabulary/withholding-defs";
import { fromDateRange, toDateRange } from "@/lib/date-range-search";
import { nonEmpty } from "@/lib/search-params";
import { FeedbackResults } from "./FeedbackResults";
import { reviewArtifactScopeLabel } from "./ReviewArtifact";
import { type FeedbackSearch, feedbackQuery } from "./review-search";

const PAGE_SIZE = 25;
/**
 * Three facets, none of them nested behind a "More filters" popover.
 *
 * Outcome, place and reason are the three questions this screen exists to answer, and a filter an
 * operator has to go looking for is one they do not know they have. The popover earned its keep when
 * the reason facet was fourteen sentences long; grouping those into four families is what made all
 * three fit on the toolbar, so the popover went with it.
 *
 * Each option carries the icon and tone of the badge it filters for, so the dropdown and the table
 * are recognisably about the same thing — they used to be plain grey text next to coloured tags.
 */
const OUTCOME_OPTIONS = statusFacetOptions(DELIVERY_STATE_DEFS);
const PLACE_OPTIONS = statusFacetOptions(DELIVERY_PLACE_DEFS, FILTERABLE_PLACES);
const WITHHELD_FAMILY_OPTIONS = statusFacetOptions(WITHHOLDING_FAMILY_DEFS);

export interface FeedbackListPageProps {
	workspaceSlug: string;
	search: FeedbackSearch;
	onSearchChange: (patch: Partial<FeedbackSearch>) => void;
}

export function FeedbackListPage({ workspaceSlug, search, onSearchChange }: FeedbackListPageProps) {
	const query = useQuery({
		...listPracticeReviewFeedbackOptions({
			path: { workspaceSlug },
			query: feedbackQuery(search, PAGE_SIZE),
		}),
	});
	const feedback = query.data?.content ?? [];
	const totalPages = query.data?.page?.totalPages;
	const recipient = feedback[0]?.recipient;
	const hasFilter = Boolean(
		search.deliveryState?.length ||
			search.withheldFamily?.length ||
			search.channel?.length ||
			search.agentJobId ||
			search.artifactKind ||
			search.recipientUserId ||
			search.from ||
			search.to,
	);
	const reset = () =>
		onSearchChange({
			page: 0,
			deliveryState: undefined,
			withheldFamily: undefined,
			channel: undefined,
			agentJobId: undefined,
			artifactKind: undefined,
			artifactId: undefined,
			recipientUserId: undefined,
			from: undefined,
			to: undefined,
		});
	const patchFilter = (patch: Partial<FeedbackSearch>) => onSearchChange({ ...patch, page: 0 });

	useEffect(() => {
		if (totalPages !== undefined && search.page && search.page >= totalPages) {
			onSearchChange({ page: Math.max(0, totalPages - 1) });
		}
	}, [onSearchChange, search.page, totalPages]);

	return (
		<section aria-label="Feedback delivery" className="space-y-4">
			<FilterToolbar
				hasFilter={hasFilter}
				onReset={reset}
				actions={
					<ResultCount
						total={query.data?.page?.totalElements}
						noun={["piece of feedback", "pieces of feedback"]}
						hasFilter={hasFilter}
					/>
				}
			>
				<div className="flex flex-wrap gap-2">
					<FacetMultiSelect
						title="Outcome"
						options={OUTCOME_OPTIONS}
						selected={search.deliveryState ?? []}
						onChange={(values) => patchFilter({ deliveryState: nonEmpty(values) })}
					/>
					<FacetMultiSelect
						title="Place"
						options={PLACE_OPTIONS}
						selected={search.channel ?? []}
						onChange={(values) => patchFilter({ channel: nonEmpty(values) })}
					/>
					<FacetMultiSelect
						title="Why withheld"
						options={WITHHELD_FAMILY_OPTIONS}
						selected={search.withheldFamily ?? []}
						onChange={(values) => patchFilter({ withheldFamily: nonEmpty(values) })}
					/>
					<DateRangeFacet
						value={toDateRange(search)}
						onChange={(range) => patchFilter(fromDateRange(range))}
					/>
				</div>
				{search.agentJobId && (
					<ReferenceFilterPill
						label="Review"
						value={search.agentJobId}
						onClear={() => patchFilter({ agentJobId: undefined })}
					/>
				)}
				{search.recipientUserId && (
					<ReferenceFilterPill
						label="Recipient"
						id={search.recipientUserId}
						name={recipient?.name ?? recipient?.login}
						onClear={() => patchFilter({ recipientUserId: undefined })}
					/>
				)}
				{search.artifactKind && (
					<ReferenceFilterPill
						label="Reviewed work"
						value={reviewArtifactScopeLabel(
							search.artifactKind,
							search.artifactId,
							feedback[0]?.artifact,
						)}
						onClear={() => patchFilter({ artifactKind: undefined, artifactId: undefined })}
					/>
				)}
			</FilterToolbar>
			{query.isError ? (
				<QueryErrorAlert
					error={query.error}
					title="Couldn't load feedback"
					onRetry={() => query.refetch()}
				/>
			) : (
				<FeedbackResults
					workspaceSlug={workspaceSlug}
					state={
						query.isLoading
							? { status: "loading" }
							: feedback.length === 0
								? { status: "empty", filtered: hasFilter }
								: { status: "ready", feedback }
					}
				/>
			)}
			<TablePagination
				page={query.data?.page?.number ?? search.page ?? 0}
				totalPages={query.data?.page?.totalPages ?? 0}
				renderPageLink={(page, props) => (
					<Link
						{...props}
						to="/w/$workspaceSlug/admin/practices/reviews/delivery"
						params={{ workspaceSlug }}
						search={(previous) => ({ ...previous, page: page === 0 ? undefined : page })}
					/>
				)}
			/>
		</section>
	);
}
