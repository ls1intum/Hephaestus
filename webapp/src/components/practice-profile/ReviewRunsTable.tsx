import { PulseIcon } from "@primer/octicons-react";
import { PlayIcon } from "lucide-react";

import type { ProfileReviewRun, ReviewedWorkRef } from "@/api/types.gen";
import { InlineLink } from "@/components/common/InlineLink";
import { useNow } from "@/components/common/use-now";
import type { ReviewRunSaw } from "@/components/practice-trace/review-run-saw";
import { LatestReviewTag, RequestedReviewTag } from "@/components/practice-trace/review-run-tags";
import {
	type ReviewRunColumn,
	ReviewRunDayTable,
	ReviewRunDayTableSkeleton,
	ReviewRunSawCell,
	ReviewRunWorkCell,
} from "@/components/practice-trace/ReviewRunDayTable";
import { count } from "@/components/practice-vocabulary/feedback-text";
import { PracticeTableRow } from "@/components/practice-vocabulary/PracticeTable";
import { RecordTimeCell } from "@/components/practice-vocabulary/RecordTable";
import { REVIEW_RUN_STATE_DEFS } from "@/components/practice-vocabulary/review-run-state-defs";
import { Button } from "@/components/ui/button";
import {
	Empty,
	EmptyDescription,
	EmptyHeader,
	EmptyMedia,
	EmptyTitle,
} from "@/components/ui/empty";
import { TableCell } from "@/components/ui/table";
import { artifactKindNoun, sameReviewedWork } from "@/lib/artifact-kinds";
import { formatDayTime } from "@/lib/dates";
import { hasText } from "@/lib/text";

import { REVIEWS_OF_YOUR_WORK } from "./practice-profile-search";

export interface ReviewRunsTableProps {
	/** Newest first, as the wire orders them. */
	runs: ProfileReviewRun[];
	/**
	 * Whether a filter narrows the list: the newest row is then not the latest review, and an empty
	 * list says nothing about whether any review exists.
	 */
	filtered: boolean;
	onOpenReview: (reviewId: string) => void;
	openReviewId?: string;
	/** Offered only on the rows whose `mayRequest` allows it. */
	onReviewNow?: (work: ReviewedWorkRef) => void;
	/** The work an ask is in flight about; its rows say so rather than invite a second. */
	requesting?: Pick<ReviewedWorkRef, "kind" | "id">;
	/** Each review's position among the reviews of its work, when every review is loaded. */
	positions?: Map<string, number>;
}

const COLUMNS: readonly ReviewRunColumn[] = [
	{ label: "What it saw" },
	{ label: "Actions", srOnly: true, className: "w-48" },
	{ label: "Open", srOnly: true, className: "w-28" },
];

/** The developer's own review, as the list phrases it. */
function sawOf(run: ProfileReviewRun): ReviewRunSaw {
	let progress: ReviewRunSaw["progress"] = "finished";
	if (run.status === "FAILED") {
		progress = "stopped";
	} else if (run.status === "IN_PROGRESS") {
		progress = "running";
	}
	return { ...run, progress };
}

/**
 * The reviews that recorded something about the reader's own work, one body per day under its
 * heading, so a row carries the time alone.
 */
export function ReviewRunsTable({
	runs,
	filtered,
	onOpenReview,
	openReviewId,
	onReviewNow,
	requesting,
	positions,
}: ReviewRunsTableProps) {
	// The newest row of a narrowed list is only the newest of what the filter kept.
	const latestId = filtered ? undefined : runs[0]?.reviewId;

	return (
		<ReviewRunDayTable
			aria-label={REVIEWS_OF_YOUR_WORK}
			columns={COLUMNS}
			runs={runs}
			dateOf={(run) => run.reviewedAt}
			runKey={(run) => run.reviewId}
			renderRow={(run) => (
				<ReviewRunRow
					run={run}
					isLatest={run.reviewId === latestId}
					open={run.reviewId === openReviewId}
					onOpenReview={onOpenReview}
					onReviewNow={onReviewNow}
					isRequesting={requesting !== undefined && sameReviewedWork(requesting, run.reviewedWork)}
					position={positions?.get(run.reviewId)}
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

interface ReviewRunRowProps {
	run: ProfileReviewRun;
	isLatest: boolean;
	open: boolean;
	onOpenReview: (reviewId: string) => void;
	onReviewNow?: (work: ReviewedWorkRef) => void;
	isRequesting: boolean;
	position?: number;
}

function ReviewRunRow({
	run,
	isLatest,
	open,
	onOpenReview,
	onReviewNow,
	isRequesting,
	position,
}: ReviewRunRowProps) {
	const { reviewedWork: work, reviewedAt: at } = run;
	const today = new Date(useNow());

	return (
		<PracticeTableRow
			open={open}
			link={{
				text: "Open review",
				name: `of ${work.label}, ${formatDayTime(at, today)}`,
				onOpen: () => onOpenReview(run.reviewId),
			}}
		>
			<RecordTimeCell
				status={run.status === undefined ? undefined : REVIEW_RUN_STATE_DEFS[run.status]}
				at={at}
			/>
			<ReviewRunWorkCell work={work} position={position} />
			<ReviewRunSawCell saw={sawOf(run)}>
				<FeedbackOnTheWork run={run} />
				{isLatest && <LatestReviewTag />}
				{run.triggerMode === "MANUAL" && <RequestedReviewTag />}
			</ReviewRunSawCell>
			<TableCell className="align-top whitespace-normal">
				{run.mayRequest && onReviewNow && (
					<Button
						type="button"
						variant="outline"
						size="xs"
						className={ROW_ACTION_PRESSED}
						disabled={isRequesting}
						onClick={() => onReviewNow(work)}
						aria-label={`${isRequesting ? "Requesting review…" : "Request review"}: ${work.label}`}
					>
						<PlayIcon aria-hidden data-icon="inline-start" />
						{isRequesting ? "Requesting review…" : "Request review"}
					</Button>
				)}
			</TableCell>
		</PracticeTableRow>
	);
}

/**
 * The row's own control under the pointer or focus, in the primary ground: a press anywhere else on
 * the row opens the review, and the accent is spent on one control per surface, never on a row.
 */
const ROW_ACTION_PRESSED =
	"hover:bg-primary hover:text-primary-foreground focus-visible:bg-primary focus-visible:text-primary-foreground dark:hover:bg-primary dark:hover:text-primary-foreground";

/**
 * The feedback this review left on the work, linked where it was left. A comment the provider cannot
 * address stays a count: a link that scrolls nowhere is a promise the row did not keep.
 */
function FeedbackOnTheWork({ run }: { run: ProfileReviewRun }) {
	const { feedbackDelivered, feedbackUrl, reviewedWork } = run;
	if (feedbackDelivered === 0) {
		return null;
	}
	if (!hasText(feedbackUrl)) {
		return (
			<span className="text-xs text-muted-foreground">
				{count(feedbackDelivered, "piece", "pieces")} of feedback
			</span>
		);
	}
	const noun = artifactKindNoun(reviewedWork.kind, 1, reviewedWork.provider);
	return (
		<InlineLink
			href={feedbackUrl}
			external
			className="text-xs"
			aria-label={`Read the feedback on the ${noun} ${reviewedWork.label}`}
		>
			Read the feedback on the {noun}
		</InlineLink>
	);
}

export function ReviewRunsTableSkeleton({ rows }: { rows: number }) {
	return (
		<ReviewRunDayTableSkeleton aria-label={REVIEWS_OF_YOUR_WORK} columns={COLUMNS} rows={rows} />
	);
}
