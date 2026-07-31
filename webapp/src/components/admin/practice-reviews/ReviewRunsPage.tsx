import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";
import { ChevronRightIcon, WorkflowIcon } from "lucide-react";
import { useEffect, useId } from "react";
import { listPracticeReviewsOptions } from "@/api/@tanstack/react-query.gen";
import type { ReviewRunSummary } from "@/api/types.gen";
import { STATUS_LABELS, statusBadgeVariant } from "@/components/admin/ai/job-utils";
import { FilterToolbar } from "@/components/common/FilterToolbar";
import { QueryErrorAlert } from "@/components/common/QueryErrorAlert";
import { RelativeTime } from "@/components/common/RelativeTime";
import { ResultCount } from "@/components/common/ResultCount";
import { TablePagination } from "@/components/common/TablePagination";
import { Badge } from "@/components/ui/badge";
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
import { ReviewArtifact } from "./ReviewArtifact";
import { FeedbackCountsSummary, FindingCountsSummary } from "./ReviewBadges";
import { ReviewResultsSkeleton } from "./ReviewResultsSkeleton";
import type { RunsSearch } from "./review-search";

const PAGE_SIZE = 20;
const ACTIVE_REVIEW_POLL_MS = 5_000;
const STATUSES = ["QUEUED", "RUNNING", "COMPLETED", "FAILED", "TIMED_OUT", "CANCELLED"] as const;
const STATUS_ITEMS = [
	{ value: "ALL", label: "All statuses" },
	...STATUSES.map((status) => ({ value: status, label: STATUS_LABELS[status] })),
] as const;

function isReviewStatus(value: string | null): value is NonNullable<RunsSearch["status"]> {
	return STATUSES.some((status) => status === value);
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
						value={search.status ?? "ALL"}
						onValueChange={(value) =>
							onSearchChange({
								status: isReviewStatus(value) ? value : undefined,
								page: 0,
							})
						}
					>
						<SelectTrigger id={statusId} size="sm" className="w-44 max-w-full">
							<SelectValue />
						</SelectTrigger>
						<SelectContent>
							{STATUS_ITEMS.map((item) => (
								<SelectItem key={item.value} value={item.value}>
									{item.label}
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
		return <FindingCountsSummary counts={review.findings} />;
	}
	if (review.status === "QUEUED" || review.status === "RUNNING") {
		return <span className="text-muted-foreground">Pending</span>;
	}
	return (
		<span className="text-muted-foreground">
			<span aria-hidden>—</span>
			<span className="sr-only">No findings produced</span>
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
	const { strengths, problems, notApplicable } = review.findings;
	return strengths + problems + notApplicable > 0;
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
				<FindingCountsSummary counts={review.findings} />
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
							<TableHead scope="col">Findings</TableHead>
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
									<Badge variant={statusBadgeVariant(review.status)}>
										{STATUS_LABELS[review.status]}
									</Badge>
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
									<Badge variant={statusBadgeVariant(review.status)}>
										{STATUS_LABELS[review.status]}
									</Badge>
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
