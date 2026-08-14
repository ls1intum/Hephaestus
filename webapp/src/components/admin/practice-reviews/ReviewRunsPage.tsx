import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ChevronRightIcon, WorkflowIcon } from "lucide-react";
import { useEffect, useId } from "react";
import { listPracticeReviewsOptions } from "@/api/@tanstack/react-query.gen";
import type { ReviewRunSummary } from "@/api/types.gen";
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
import { statusToneClass, statusValues } from "@/components/practice-vocabulary/status-def";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Field, FieldLabel } from "@/components/ui/field";
import {
	Item,
	ItemActions,
	ItemContent,
	ItemDescription,
	ItemGroup,
	ItemTitle,
} from "@/components/ui/item";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/components/ui/select";
import {
	Table,
	TableBody,
	TableCaption,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";
import { ReviewArtifact } from "./ReviewArtifact";
import { FeedbackCountsSummary, FindingCountsSummary } from "./ReviewBadges";
import { ReviewResultsSkeleton } from "./ReviewResultsSkeleton";
import type { RunsSearch } from "./review-search";

const PAGE_SIZE = 20;
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
 * One status, drawn the same way in the closed trigger and in the open list — the two places a
 * reader compares. The dropdown used to be plain text while the table beside it carried coloured
 * badges, so choosing a filter meant matching a word to a tag by memory.
 */
function StatusItemLabel({ value }: { value: string }) {
	if (!isReviewStatus(value)) return <>All statuses</>;
	const def = REVIEW_STATUS_DEFS[value];
	return (
		<>
			<def.icon
				aria-hidden
				className={cn("size-3.5 shrink-0", statusToneClass(def.badgeVariant))}
			/>
			{def.label}
		</>
	);
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
			query: { page: search.page ?? 0, size: PAGE_SIZE, status: search.status },
		}),
		refetchInterval: (result) =>
			result.state.data?.content?.some(
				(review) => review.status === "QUEUED" || review.status === "RUNNING",
			)
				? ACTIVE_REVIEW_POLL_MS
				: false,
	});
	const reviews = query.data?.content ?? [];
	const hasFilter = Boolean(search.status);
	const totalPages = query.data?.page?.totalPages;

	useEffect(() => {
		if (totalPages !== undefined && search.page && search.page >= totalPages) {
			onSearchChange({ page: Math.max(0, totalPages - 1) });
		}
	}, [onSearchChange, search.page, totalPages]);

	return (
		<section aria-label="Practice reviews" className="space-y-4">
			<FilterToolbar
				hasFilter={hasFilter}
				onReset={() => onSearchChange({ status: undefined, page: 0 })}
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
			</FilterToolbar>
			{query.isError ? (
				<QueryErrorAlert
					error={query.error}
					title="Couldn't load reviews"
					onRetry={() => query.refetch()}
				/>
			) : query.isLoading ? (
				<ReviewResultsSkeleton label="Loading reviews" />
			) : reviews.length === 0 ? (
				<Empty className="border">
					<EmptyHeader>
						<EmptyMedia variant="icon">
							<WorkflowIcon />
						</EmptyMedia>
						<EmptyTitle>No reviews found</EmptyTitle>
						<EmptyDescription>
							{search.status
								? "Try another status."
								: "Reviews appear when an enabled practice is triggered or a contributor requests one."}
						</EmptyDescription>
					</EmptyHeader>
				</Empty>
			) : (
				<ReviewList workspaceSlug={workspaceSlug} reviews={reviews} search={search} />
			)}
			<TablePagination
				page={query.data?.page?.number ?? search.page ?? 0}
				totalPages={query.data?.page?.totalPages ?? 0}
				renderPageLink={(page, props) => (
					<Link
						{...props}
						to="/w/$workspaceSlug/admin/practices/reviews"
						params={{ workspaceSlug }}
						search={{ page: page === 0 ? undefined : page, status: search.status }}
					/>
				)}
			/>
		</section>
	);
}

function RunFindingSummary({ review }: { review: ReviewRunSummary }) {
	if (review.status === "COMPLETED" || hasFindingOutput(review)) {
		return <FindingCountsSummary counts={review.observations} />;
	}
	if (review.status === "QUEUED" || review.status === "RUNNING") {
		return <span className="text-muted-foreground">Pending</span>;
	}
	return (
		<span className="text-muted-foreground">
			<span aria-hidden>—</span>
			<span className="sr-only">No observations produced</span>
		</span>
	);
}

function RunFeedbackSummary({ review }: { review: ReviewRunSummary }) {
	if (review.status === "COMPLETED" || hasFeedbackOutput(review)) {
		return <FeedbackCountsSummary counts={review.feedback} />;
	}
	if (review.status === "QUEUED" || review.status === "RUNNING") {
		return <span className="text-muted-foreground">Pending</span>;
	}
	return (
		<span className="text-muted-foreground">
			<span aria-hidden>—</span>
			<span className="sr-only">No feedback composed</span>
		</span>
	);
}

function hasFindingOutput(review: ReviewRunSummary) {
	const { strengths, problems, notApplicable, inconclusive } = review.observations;
	return strengths + problems + notApplicable + inconclusive > 0;
}

function hasFeedbackOutput(review: ReviewRunSummary) {
	const { delivered, failed, prepared, superseded, suppressed } = review.feedback;
	return delivered + failed + prepared + superseded + suppressed > 0;
}

function RunCardOutputSummary({ review }: { review: ReviewRunSummary }) {
	const hasOutput = hasFindingOutput(review) || hasFeedbackOutput(review);
	if (review.status === "COMPLETED" || hasOutput) {
		return (
			<>
				<FindingCountsSummary counts={review.observations} />
				<FeedbackCountsSummary counts={review.feedback} />
			</>
		);
	}
	if (review.status === "QUEUED" || review.status === "RUNNING") {
		return <span className="text-sm text-muted-foreground">Awaiting output</span>;
	}
	return <span className="text-sm text-muted-foreground">No output</span>;
}

function ReviewList({
	workspaceSlug,
	reviews,
	search,
}: {
	workspaceSlug: string;
	reviews: ReviewRunSummary[];
	search: RunsSearch;
}) {
	return (
		<>
			<div className="hidden xl:block">
				<Table containerClassName="rounded-lg border">
					<TableCaption className="sr-only">Practice reviews, newest first</TableCaption>
					<TableHeader>
						<TableRow>
							<TableHead scope="col">Reviewed work</TableHead>
							<TableHead scope="col">Status</TableHead>
							<TableHead scope="col">Observations</TableHead>
							<TableHead scope="col">Feedback</TableHead>
							<TableHead scope="col" className="w-32">
								Created
							</TableHead>
						</TableRow>
					</TableHeader>
					<TableBody>
						{reviews.map((review) => (
							<TableRow key={review.id}>
								<TableCell className="max-w-md whitespace-normal align-top">
									<Link
										to="/w/$workspaceSlug/admin/practices/reviews/$jobId"
										params={{ workspaceSlug, jobId: review.id }}
										search={{ page: search.page, status: search.status }}
										className="block rounded-sm hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
									>
										<ReviewArtifact artifact={review.target} />
									</Link>
								</TableCell>
								<TableCell className="whitespace-normal align-top">
									<StatusBadge def={REVIEW_STATUS_DEFS[review.status]} />
								</TableCell>
								<TableCell className="whitespace-normal align-top">
									<RunFindingSummary review={review} />
								</TableCell>
								<TableCell className="whitespace-normal align-top">
									<RunFeedbackSummary review={review} />
								</TableCell>
								<TableCell className="align-top text-muted-foreground">
									<RelativeTime value={review.createdAt} />
								</TableCell>
							</TableRow>
						))}
					</TableBody>
				</Table>
			</div>
			<ItemGroup className="xl:hidden">
				{reviews.map((review) => (
					<div key={review.id} role="listitem">
						<Item
							variant="outline"
							render={
								<Link
									to="/w/$workspaceSlug/admin/practices/reviews/$jobId"
									params={{ workspaceSlug, jobId: review.id }}
									search={{ page: search.page, status: search.status }}
								/>
							}
						>
							<ItemContent className="min-w-0">
								<ItemTitle className="w-full min-w-0 line-clamp-none">
									<ReviewArtifact artifact={review.target} variant="label" display="full" />
								</ItemTitle>
								<ItemDescription>{review.target.title}</ItemDescription>
								<div className="mt-1">
									<StatusBadge def={REVIEW_STATUS_DEFS[review.status]} />
								</div>
								<RunCardOutputSummary review={review} />
								<span className="text-xs text-muted-foreground">
									Created <RelativeTime value={review.createdAt} />
								</span>
							</ItemContent>
							<ItemActions>
								<ChevronRightIcon className="size-4 text-muted-foreground" aria-hidden />
							</ItemActions>
						</Item>
					</div>
				))}
			</ItemGroup>
		</>
	);
}
