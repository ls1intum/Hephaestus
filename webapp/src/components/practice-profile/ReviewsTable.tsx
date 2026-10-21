import { PulseIcon } from "@primer/octicons-react";
import { PlayIcon } from "lucide-react";

import type { ProfileReview, ReviewResultCounts } from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import { useNow } from "@/components/common/use-now";
import type { ReviewSaw } from "@/components/practice-trace/review-saw";
import { LatestReviewTag, RequestedReviewTag } from "@/components/practice-trace/review-tags";
import {
	type ReviewColumn,
	ReviewDayTable,
	ReviewDayTableSkeleton,
	ReviewItemCell,
	ReviewSawCell,
} from "@/components/practice-trace/ReviewDayTable";
import { count } from "@/components/practice-vocabulary/feedback-text";
import { PracticeTableRow } from "@/components/practice-vocabulary/PracticeTable";
import { RecordTimeCell } from "@/components/practice-vocabulary/RecordTable";
import { OWN_REVIEW_STATUS_DEFS } from "@/components/practice-vocabulary/review-status-defs";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { Spinner } from "@/components/ui/spinner";
import { TableCell } from "@/components/ui/table";
import { formatDayTime } from "@/lib/dates";
import { itemKindNoun } from "@/lib/item-kinds";
import { hasText } from "@/lib/text";

import { REVIEWS_OF_YOUR_WORK } from "./practice-profile-search";

export interface ReviewsTableProps {
	reviews: ProfileReview[];
	/**
	 * Whether a filter narrows the list: the newest row is then not the latest review, and an empty
	 * list says nothing about whether any review exists.
	 */
	filtered: boolean;
	onOpenReview: (reviewId: string) => void;
	openReviewId?: string;
	onReviewNow?: (itemId: string) => void;
	requesting?: string;
	/** Each review's position among the reviews of its item, when every review is loaded. */
	positions?: Map<string, number>;
	/** Whether the next page of reviews is on its way: placeholder rows then continue the table. */
	isLoadingMore?: boolean;
}

/** Placeholder rows for the next page: enough to read as the list going on, not a whole page. */
const LOADING_MORE_ROWS = 3;

const COLUMNS: readonly ReviewColumn[] = [
	{ label: "What it saw" },
	{ label: "Actions", srOnly: true, className: "w-48" },
	{ label: "Open", srOnly: true, className: "w-28" },
];

/** The moment a review is listed at: when it finished, or when it was asked for while it runs. */
const reviewedAt = ({ review }: ProfileReview): Date => review.completedAt ?? review.createdAt;

function sawOf({ review, results }: ProfileReview): ReviewSaw {
	const counts: ReviewResultCounts = { met: 0, notMet: 0, doesNotApply: 0, couldNotDetermine: 0 };
	for (const { outcome } of results) {
		if (outcome === "MET") {
			counts.met += 1;
		} else if (outcome === "NOT_MET") {
			counts.notMet += 1;
		} else if (outcome === "DOES_NOT_APPLY") {
			counts.doesNotApply += 1;
		} else {
			counts.couldNotDetermine += 1;
		}
	}
	return {
		status: review.status,
		results: counts,
		notMetPractices: results
			.filter((result) => result.outcome === "NOT_MET")
			.map((result) => result.practiceName),
	};
}

export function ReviewsTable({
	reviews,
	filtered,
	onOpenReview,
	openReviewId,
	onReviewNow,
	requesting,
	positions,
	isLoadingMore = false,
}: ReviewsTableProps) {
	// The newest row of a narrowed list is only the newest of what the filter kept.
	const latestId = filtered ? undefined : reviews[0]?.review.id;

	return (
		<ReviewDayTable
			aria-label={REVIEWS_OF_YOUR_WORK}
			columns={COLUMNS}
			reviews={reviews}
			dateOf={reviewedAt}
			reviewKey={(entry) => entry.review.id}
			loadingMore={isLoadingMore ? LOADING_MORE_ROWS : undefined}
			renderRow={(entry) => (
				<ReviewRow
					entry={entry}
					isLatest={entry.review.id === latestId}
					open={entry.review.id === openReviewId}
					onOpenReview={onOpenReview}
					onReviewNow={onReviewNow}
					isRequesting={requesting !== undefined && requesting === entry.review.item.id}
					position={positions?.get(entry.review.id)}
				/>
			)}
			empty={
				filtered ? (
					<p className="text-sm text-muted-foreground">No reviews match your filters.</p>
				) : (
					<Empty>
						<EmptyHeader>
							<EmptyMedia variant="icon">
								<PulseIcon />
							</EmptyMedia>
							<EmptyTitle>No reviews of your work yet</EmptyTitle>
							<EmptyDescription>
								A review appears here once it records something about your work: a pull or merge
								request, an issue, a conversation or a document.
							</EmptyDescription>
						</EmptyHeader>
					</Empty>
				)
			}
		/>
	);
}

interface ReviewRowProps {
	entry: ProfileReview;
	isLatest: boolean;
	open: boolean;
	onOpenReview: (reviewId: string) => void;
	onReviewNow?: (itemId: string) => void;
	isRequesting: boolean;
	position?: number;
}

function ReviewRow({
	entry,
	isLatest,
	open,
	onOpenReview,
	onReviewNow,
	isRequesting,
	position,
}: ReviewRowProps) {
	const { review } = entry;
	const { item } = review;
	const at = reviewedAt(entry);
	const today = new Date(useNow());

	return (
		<PracticeTableRow
			open={open}
			link={{
				text: "Open review",
				name: `of ${item.label}, ${formatDayTime(at, today)}`,
				onOpen: () => onOpenReview(review.id),
			}}
		>
			<RecordTimeCell status={OWN_REVIEW_STATUS_DEFS[review.status]} at={at} />
			<ReviewItemCell item={item} position={position} />
			<ReviewSawCell saw={sawOf(entry)}>
				<FeedbackOnTheWork entry={entry} />
				{isLatest && <LatestReviewTag />}
				{review.origin === "REQUEST" && <RequestedReviewTag />}
			</ReviewSawCell>
			<TableCell className="align-top whitespace-normal">
				{review.kind === "ITEM" && onReviewNow && (
					<Button
						type="button"
						variant="outline"
						size="xs"
						className={ROW_ACTION_PRESSED}
						disabled={isRequesting}
						onClick={() => onReviewNow(item.id)}
						aria-label={`${isRequesting ? "Requesting review…" : "Request review"}: ${item.label}`}
					>
						{isRequesting ? (
							<Spinner data-icon="inline-start" />
						) : (
							<PlayIcon aria-hidden data-icon="inline-start" />
						)}
						{isRequesting ? "Requesting review…" : "Request review"}
					</Button>
				)}
			</TableCell>
		</PracticeTableRow>
	);
}

const ROW_ACTION_PRESSED =
	"hover:bg-primary hover:text-primary-foreground focus-visible:bg-primary focus-visible:text-primary-foreground dark:hover:bg-primary dark:hover:text-primary-foreground";

// Link only provider-addressable copies; an unaddressable copy remains a count.
function FeedbackOnTheWork({ entry }: { entry: ProfileReview }) {
	const { item } = entry.review;
	const onWork = entry.feedback.filter(
		(feedback) => feedback.destination === "ON_WORK" && feedback.state === "DELIVERED",
	);
	if (onWork.length === 0) {
		return null;
	}
	const url = onWork
		.flatMap((feedback) => feedback.copies)
		.find((copy) => hasText(copy.externalUrl))?.externalUrl;
	if (!hasText(url)) {
		return (
			<span className="text-xs text-muted-foreground">
				{count(onWork.length, "piece", "pieces")} of feedback
			</span>
		);
	}
	const noun = itemKindNoun(item.kind, 1, item.provider);
	return (
		<InlineLink
			href={url}
			external
			className="text-xs"
			aria-label={`Read the feedback on the ${noun} ${item.label}`}
		>
			Read the feedback on the {noun}
		</InlineLink>
	);
}

export function ReviewsTableSkeleton({ rows }: { rows: number }) {
	return <ReviewDayTableSkeleton aria-label={REVIEWS_OF_YOUR_WORK} columns={COLUMNS} rows={rows} />;
}
