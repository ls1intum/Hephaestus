import type { ReviewRunSummary } from "@/api/types.gen";
import type { StatusDef } from "@/components/common/status-def";
import { StatusBadge } from "@/components/common/StatusBadge";
import { useNow } from "@/components/common/use-now";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import {
	type ReviewRunColumn,
	ReviewRunWorkCell,
} from "@/components/practice-trace/ReviewRunDayTable";
import { PracticeTableRow } from "@/components/practice-vocabulary/PracticeTable";
import { RecordTimeCell } from "@/components/practice-vocabulary/RecordTable";
import { REVIEW_RUN_MARKER_DEFS } from "@/components/practice-vocabulary/review-run-marker-defs";
import {
	REPLACED_REVIEW_STATUS,
	RESULT_PROCESSING_DEFS,
	REVIEW_STATUS_DEFS,
	reviewStatusDef,
} from "@/components/practice-vocabulary/review-status-defs";
import { TableCell } from "@/components/ui/table";
import { formatDayTime } from "@/lib/dates";
import { hasText } from "@/lib/text";

import { holdReasonCopy } from "./job-utils";
import { RecordRowActions, ROW_OPEN_COLUMN, rowActionsColumn } from "./RecordRowActions";
import type { RowReviewRequest } from "./RequestReviewAction";
import { reviewLevel } from "./review-levels";
import { ReviewPerson } from "./ReviewPerson";
import { useTracked } from "./use-tracked";

/** The heads after the time and Work, which every row below fills. */
export const REVIEW_RUN_COLUMNS: readonly ReviewRunColumn[] = [
	{ label: "Developer", className: "w-44" },
	rowActionsColumn({ request: true }),
	ROW_OPEN_COLUMN,
];

export interface ReviewRunRowProps {
	review: ReviewRunSummary;
	/** True while this review's level is in front of the page; the row keeps a bar on its edge. */
	open?: boolean;
	/** Request review, the row's third action; left out where the screen offers no way to ask. */
	request?: RowReviewRequest;
}

/**
 * One review on the admin list: when it was created and its status, the work with the states worth
 * a second look beside it, and who it was about. What it found, practice by practice, is on its
 * sheet: a maintainer scans this list for reviews that need attention, not for counts. The whole row
 * opens the review's sheet over the list, and that is its one link: the work is plain text here.
 * Track narrows every table to the review; Trace opens its trace; Request review asks for a new
 * review of its work.
 */
export function ReviewRunRow({ review, open = false, request }: ReviewRunRowProps) {
	const today = new Date(useNow());
	const work = review.target.reviewedWork;
	const name = work?.label ?? review.target.title;
	const when = formatDayTime(review.createdAt, today);
	const tracked = useTracked({ kind: "review", id: review.id });
	const markers = <ReviewRunMarkers review={review} />;
	return (
		<PracticeTableRow
			open={open}
			link={{
				text: "Open review",
				name: `of ${name}, ${when}`,
				render: <DetailStackLink entry={reviewLevel(review.id)} single />,
			}}
			variant={tracked ? "highlighted" : undefined}
			action={
				<RecordRowActions
					record={{ kind: "review", id: review.id }}
					name={`review of ${name}, ${when}`}
					request={request}
				/>
			}
		>
			<RecordTimeCell
				status={reviewStatusDef(review)}
				at={review.createdAt}
				statusTooltip={false}
			/>
			{work === undefined ? (
				<TableCell className="align-top font-medium whitespace-normal">
					<span className="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-1">
						{review.target.title}
						{markers}
					</span>
				</TableCell>
			) : (
				<ReviewRunWorkCell
					work={work}
					position={review.positionOnWork}
					total={review.reviewsOnWork}
					linked={false}
				>
					{markers}
				</ReviewRunWorkCell>
			)}
			<TableCell className="align-top whitespace-normal">
				<ReviewDevelopers developers={review.developers} />
			</TableCell>
		</PracticeTableRow>
	);
}

/** A review a newer one replaced, in the words the list marks it with. */
const REPLACED_BY_NEWER: StatusDef = { ...REPLACED_REVIEW_STATUS, label: "Replaced by newer" };

/**
 * The states of a review worth a second look, in the order the list marks them: on hold, failed or
 * timed out, replaced by a newer review, result processing failed, and finished after the merge
 * unless the merge itself started it. A review with none of them has no marker: running, queued,
 * completed and canceled are the status icon's to say.
 */
export function reviewRunMarkers(review: ReviewRunSummary): StatusDef[] {
	const markers: StatusDef[] = [];
	if (hasText(review.holdReason)) {
		markers.push(REVIEW_RUN_MARKER_DEFS.ON_HOLD);
	}
	if (review.status === "FAILED" || review.status === "TIMED_OUT") {
		markers.push(REVIEW_STATUS_DEFS[review.status]);
	}
	if (review.reviewOutcome === "SUPERSEDED") {
		markers.push(REPLACED_BY_NEWER);
	}
	if (review.resultProcessing === "FAILED") {
		markers.push(RESULT_PROCESSING_DEFS.FAILED);
	}
	if (review.finishedAfterMerge && !review.finishedAfterMergeStartedByMerge) {
		markers.push(REVIEW_RUN_MARKER_DEFS.FINISHED_AFTER_MERGE);
	}
	return markers;
}

/** The markers as small chips; an on-hold chip names the hold's reason on hover. */
function ReviewRunMarkers({ review }: { review: ReviewRunSummary }) {
	return reviewRunMarkers(review).map((def) => (
		<StatusBadge
			key={def.label}
			def={def}
			title={
				def === REVIEW_RUN_MARKER_DEFS.ON_HOLD && hasText(review.holdReason)
					? holdReasonCopy(review.holdReason).label
					: undefined
			}
		/>
	));
}

/** The first developer by name; a review about several counts the rest. */
function ReviewDevelopers({ developers }: { developers: ReviewRunSummary["developers"] }) {
	const [first, ...rest] = developers;
	if (first === undefined) {
		return null;
	}
	return (
		<span className="flex min-w-0 flex-col items-start gap-1">
			<ReviewPerson person={first} />
			{rest.length > 0 && (
				<span className="text-xs text-muted-foreground">+{rest.length} more</span>
			)}
		</span>
	);
}
