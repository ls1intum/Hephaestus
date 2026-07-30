import { useQuery } from "@tanstack/react-query";
import { listPracticeReviewFeedbackOptions } from "@/api/@tanstack/react-query.gen";
import { DateRangeFacet } from "@/components/common/DateRangeFacet";
import {
	FacetMultiSelect,
	type FacetOption,
	toFacetOptions,
} from "@/components/common/FacetMultiSelect";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { ReferenceFilterPill } from "@/components/common/ReferenceFilterPill";
import { ResultCount } from "@/components/common/ResultCount";
import { TablePagination } from "@/components/common/TablePagination";
import { fromDateRange, toDateRange } from "@/lib/date-range-search";
import { nonEmpty } from "@/lib/search-params";
import { FeedbackResults } from "./FeedbackResults";
import { reviewArtifactScopeLabel } from "./ReviewArtifact";
import { ReviewMoreFilters } from "./ReviewMoreFilters";
import {
	AVAILABLE_FEEDBACK_CHANNELS,
	CHANNEL_LABELS,
	DELIVERY_STATE_LABELS,
	type FeedbackChannel,
	type FeedbackDeliveryState,
	type FeedbackSuppressionReason,
	SUPPRESSION_REASON_LABELS,
} from "./review-format";
import { type FeedbackSearch, feedbackQuery } from "./review-search";

const PAGE_SIZE = 25;
const DELIVERY_OPTIONS: FacetOption<FeedbackDeliveryState>[] =
	toFacetOptions(DELIVERY_STATE_LABELS);
const REASON_OPTIONS: FacetOption<FeedbackSuppressionReason>[] =
	toFacetOptions(SUPPRESSION_REASON_LABELS);
const CHANNEL_OPTIONS: FacetOption<FeedbackChannel>[] = AVAILABLE_FEEDBACK_CHANNELS.map(
	(value) => ({
		value,
		label: CHANNEL_LABELS[value],
	}),
);

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
	const recipient = feedback[0]?.recipient;
	const hasFilter = Boolean(
		search.deliveryState?.length ||
			search.suppressionReason?.length ||
			search.channel?.length ||
			search.agentJobId ||
			search.artifactType ||
			search.recipientUserId ||
			search.from ||
			search.to,
	);
	const advancedCount = (search.suppressionReason?.length ?? 0) + (search.channel?.length ?? 0);
	const reset = () =>
		onSearchChange({
			page: 0,
			deliveryState: undefined,
			suppressionReason: undefined,
			channel: undefined,
			agentJobId: undefined,
			artifactType: undefined,
			artifactId: undefined,
			recipientUserId: undefined,
			from: undefined,
			to: undefined,
		});
	const patchFilter = (patch: Partial<FeedbackSearch>) => onSearchChange({ ...patch, page: 0 });

	return (
		<section aria-label="Feedback delivery" className="space-y-4">
			<FilterToolbar
				hasFilter={hasFilter}
				onReset={reset}
				actions={
					<ResultCount
						total={query.data?.page?.totalElements}
						noun={["message", "messages"]}
						hasFilter={hasFilter}
					/>
				}
			>
				<div className="flex flex-wrap gap-2">
					<FacetMultiSelect
						title="Outcome"
						options={DELIVERY_OPTIONS}
						selected={search.deliveryState ?? []}
						onChange={(values) => patchFilter({ deliveryState: nonEmpty(values) })}
					/>
					<DateRangeFacet
						value={toDateRange(search)}
						onChange={(range) => patchFilter(fromDateRange(range))}
					/>
					<ReviewMoreFilters activeCount={advancedCount}>
						<FacetMultiSelect
							title="Withholding reason"
							variant="field"
							options={REASON_OPTIONS}
							selected={search.suppressionReason ?? []}
							onChange={(values) => patchFilter({ suppressionReason: nonEmpty(values) })}
						/>
						<FacetMultiSelect
							title="Destination"
							variant="field"
							options={CHANNEL_OPTIONS}
							selected={search.channel ?? []}
							onChange={(values) => patchFilter({ channel: nonEmpty(values) })}
						/>
					</ReviewMoreFilters>
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
				{search.artifactType && (
					<ReferenceFilterPill
						label="Reviewed work"
						value={reviewArtifactScopeLabel(
							search.artifactType,
							search.artifactId,
							feedback[0]?.artifact,
						)}
						onClear={() => patchFilter({ artifactType: undefined, artifactId: undefined })}
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
				onPageChange={(page) => onSearchChange({ page })}
			/>
		</section>
	);
}
