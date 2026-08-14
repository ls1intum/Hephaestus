import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { WorkflowIcon } from "lucide-react";
import { useEffect, useId } from "react";
import { listPracticeReviewsOptions } from "@/api/@tanstack/react-query.gen";
import type { ReviewRunSummary } from "@/api/types.gen";
import { DateRangeFacet } from "@/components/common/DateRangeFacet";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { RelativeTime } from "@/components/common/RelativeTime";
import { ResultCount } from "@/components/common/ResultCount";
import { TablePagination } from "@/components/common/TablePagination";
import {
	REVIEW_STATUS_DEFS,
	type ReviewStatus,
} from "@/components/practice-vocabulary/review-status-defs";
import { StatusBadge } from "@/components/practice-vocabulary/StatusBadge";
import { statusValues } from "@/components/practice-vocabulary/status-def";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyContent,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Field, FieldLabel } from "@/components/ui/field";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import { fromDateRange, toDateRange } from "@/lib/date-range-search";
import { ReviewArtifactLabel } from "./ReviewArtifact";
import { feedbackCountSlots, observationCountSlots, ReviewCountStrip } from "./ReviewBadges";
import { ReviewResultsSkeleton } from "./ReviewResultsSkeleton";
import { ReviewRow, ReviewRowList, ReviewRowMeta } from "./ReviewRow";
import { REVIEW_PAGE_SIZE, type RunsSearch, runsQuery } from "./review-search";

const ACTIVE_REVIEW_POLL_MS = 5_000;
const STATUSES = statusValues(REVIEW_STATUS_DEFS);
/** The "no filter" sentinel. A `Select` has to hold some value, and `undefined` is not one. */
const ALL_STATUSES = "ALL";
const STATUS_ITEMS: { value: string; label: string }[] = [
	{ value: ALL_STATUSES, label: "All statuses" },
	...STATUSES.map((status) => ({ value: status, label: REVIEW_STATUS_DEFS[status].label })),
];

function isReviewStatus(value: string | null): value is ReviewStatus {
	return STATUSES.some((status) => status === value);
}

/**
 * A status in the filter is drawn as the same tag the rows wear, in the closed trigger and in the
 * open list alike.
 *
 * The dropdown was plain grey text beside a table of coloured tags, so choosing a filter meant
 * matching a word to a tag from memory; a first pass gave it the icon and the tone but left the
 * shape different. It is the badge itself now — one component, so the two cannot come apart again.
 */
function StatusItemLabel({ value }: { value: string }) {
	if (!isReviewStatus(value)) return <span className="text-muted-foreground">All statuses</span>;
	return <StatusBadge def={REVIEW_STATUS_DEFS[value]} />;
}

export interface ReviewRunsPageProps {
	workspaceSlug: string;
	search: RunsSearch;
	onSearchChange: (patch: Partial<RunsSearch>) => void;
}

export function ReviewRunsPage({ workspaceSlug, search, onSearchChange }: ReviewRunsPageProps) {
	const statusId = useId();
	const query = useQuery({
		...listPracticeReviewsOptions({
			path: { workspaceSlug },
			query: runsQuery(search, REVIEW_PAGE_SIZE),
		}),
		refetchInterval: (result) =>
			result.state.data?.content?.some(
				(review) => review.status === "QUEUED" || review.status === "RUNNING",
			)
				? ACTIVE_REVIEW_POLL_MS
				: false,
	});
	const reviews = query.data?.content ?? [];
	const hasFilter = Boolean(search.status || search.from || search.to);
	const totalPages = query.data?.page?.totalPages;
	/** Every filter this toolbar can set, cleared at once — the toolbar's Reset and the empty state's
	    button are the same action and were two drifting copies of it. */
	const reset = () =>
		onSearchChange({ status: undefined, from: undefined, to: undefined, page: 0 });

	useEffect(() => {
		if (totalPages !== undefined && search.page && search.page >= totalPages) {
			onSearchChange({ page: Math.max(0, totalPages - 1) });
		}
	}, [onSearchChange, search.page, totalPages]);

	return (
		<section aria-label="Practice reviews" className="space-y-4">
			<FilterToolbar
				hasFilter={hasFilter}
				onReset={reset}
				actions={
					<ResultCount
						total={query.data?.page?.totalElements}
						noun={["review", "reviews"]}
						hasFilter={hasFilter}
					/>
				}
			>
				<Field orientation="horizontal" className="w-auto max-w-full flex-wrap text-sm">
					<FieldLabel htmlFor={statusId} className="text-muted-foreground">
						Status
					</FieldLabel>
					<Select
						items={STATUS_ITEMS}
						value={search.status ?? ALL_STATUSES}
						onValueChange={(value) =>
							onSearchChange({
								status: isReviewStatus(value) ? value : undefined,
								page: 0,
							})
						}
					>
						<SelectTrigger id={statusId} size="sm" className="w-48 max-w-full">
							<SelectValue>{(value: string) => <StatusItemLabel value={value} />}</SelectValue>
						</SelectTrigger>
						<SelectContent>
							{STATUS_ITEMS.map((item) => (
								<SelectItem key={item.value} value={item.value}>
									<StatusItemLabel value={item.value} />
								</SelectItem>
							))}
						</SelectContent>
					</Select>
				</Field>
				{/* "Requested" rather than "Date", and not "Started": the timestamp the rows show is the
				    review's `createdAt`, which is when it was enqueued. A review can sit queued before a
				    worker claims it, so "Started" would name a moment neither the row nor this filter
				    uses. See `DateRangeFacet`, which requires the title for exactly this reason. */}
				<DateRangeFacet
					title="Requested"
					value={toDateRange(search)}
					onChange={(range) => onSearchChange({ ...fromDateRange(range), page: 0 })}
				/>
			</FilterToolbar>
			{query.isError ? (
				<QueryErrorAlert
					error={query.error}
					title="Couldn't load reviews"
					onRetry={() => query.refetch()}
				/>
			) : query.isLoading ? (
				<ReviewResultsSkeleton label="Loading reviews" rows={REVIEW_PAGE_SIZE} />
			) : reviews.length === 0 ? (
				<Empty className="border">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<WorkflowIcon />
						</EmptyMedia>
						<EmptyTitle>No reviews found</EmptyTitle>
						<EmptyDescription>
							{/* A date range narrows this list too now, so "never triggered" is no longer the
							    only reason it can be empty — saying so to someone who has just picked a
							    window would be false. */}
							{!hasFilter
								? "Reviews appear when an enabled practice is triggered or a contributor requests one."
								: search.status && !search.from && !search.to
									? `No review is ${REVIEW_STATUS_DEFS[search.status].label.toLowerCase()}. Other reviews may exist under another status.`
									: "No review matches these filters. Other reviews may exist outside them."}
						</EmptyDescription>
					</EmptyHeader>
					{hasFilter && (
						<EmptyContent>
							{/* The advice used to be "Try another status" with nothing to press. See
							    `ObservationResultsState`. */}
							<Button variant="outline" size="sm" onClick={reset}>
								Clear all filters
							</Button>
						</EmptyContent>
					)}
				</Empty>
			) : (
				<ReviewRowList label="Practice reviews, newest first">
					{reviews.map((review) => (
						<ReviewRunRow
							key={review.id}
							workspaceSlug={workspaceSlug}
							review={review}
							search={search}
						/>
					))}
				</ReviewRowList>
			)}
			<TablePagination
				page={query.data?.page?.number ?? search.page ?? 0}
				totalPages={query.data?.page?.totalPages ?? 0}
				renderPageLink={(page, props) => (
					<Link
						{...props}
						to="/w/$workspaceSlug/admin/practices/reviews"
						params={{ workspaceSlug }}
						// Spread rather than list the filters: page 2 of a filtered list has to stay
						// filtered, and naming them one by one is what silently dropped the next one.
						search={{ ...search, page: page === 0 ? undefined : page }}
					/>
				)}
			/>
		</section>
	);
}

export interface ReviewRunRowProps {
	workspaceSlug: string;
	review: ReviewRunSummary;
	search: RunsSearch;
}

/**
 * One review: the work it read, and what it produced.
 *
 * <p>The row is named after the work rather than after the review, because a review has no name an
 * operator knows — it has a UUID. The two tallies sit under it as a numeric strip, and only when
 * there is something true to count: a review that has not finished says so, and one that ended
 * without output says that instead of drawing nine zeroes.
 */
export function ReviewRunRow({ workspaceSlug, review, search }: ReviewRunRowProps) {
	return (
		<ReviewRow
			status={REVIEW_STATUS_DEFS[review.status]}
			title={
				<Link
					to="/w/$workspaceSlug/admin/practices/reviews/$jobId"
					params={{ workspaceSlug, jobId: review.id }}
					// The detail route validates with this same schema, so the whole search carries and
					// the reader comes back to the list they left, filters intact.
					search={search}
				>
					{review.target.title}
				</Link>
			}
			meta={
				<>
					<ReviewRowMeta
						items={[
							<ReviewArtifactLabel key="work" artifact={review.target} />,
							// See `ObservationRow`: no hover target under a stretched row link.
							<RelativeTime key="created" value={review.createdAt} tooltip={false} />,
						]}
					/>
					<RunOutputSummary review={review} />
				</>
			}
			chips={[
				{
					key: "status",
					width: "lg:w-40",
					node: <StatusBadge def={REVIEW_STATUS_DEFS[review.status]} />,
				},
			]}
		/>
	);
}

function hasObservationOutput(review: ReviewRunSummary) {
	const { strengths, problems, notApplicable, inconclusive } = review.observations;
	return strengths + problems + notApplicable + inconclusive > 0;
}

function hasFeedbackOutput(review: ReviewRunSummary) {
	const { delivered, failed, prepared, superseded, suppressed } = review.feedback;
	return delivered + failed + prepared + superseded + suppressed > 0;
}

/**
 * What the review produced, or why there is nothing to show.
 *
 * <p>A run that is still going has an empty tally that means "not yet", and one that failed has an
 * empty tally that means "never". Printing nine zeroes for both would make the first look like a
 * finished review that found nothing.
 *
 * <p>Once there *is* a tally it is drawn as two strips rather than two sentences, because these nine
 * numbers are the row's dominant content and they are read down the list, not along the row. See
 * `ReviewCountStrip` for why the zeroes are drawn rather than dropped.
 */
function RunOutputSummary({ review }: { review: ReviewRunSummary }) {
	if (review.status === "COMPLETED" || hasObservationOutput(review) || hasFeedbackOutput(review)) {
		return (
			<>
				<ReviewCountStrip label="Observations" slots={observationCountSlots(review.observations)} />
				<ReviewCountStrip label="Feedback" slots={feedbackCountSlots(review.feedback)} />
			</>
		);
	}
	if (review.status === "QUEUED" || review.status === "RUNNING") {
		return <p>Results appear as it finishes.</p>;
	}
	return <p>It produced nothing before it stopped.</p>;
}
