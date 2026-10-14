import type { ReviewSummary } from "@/api/types.gen";
import type { StatusDef } from "@/components/common/status-def";
import { StatusBadge } from "@/components/common/StatusBadge";
import { useNow } from "@/components/common/use-now";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { type ReviewColumn, ReviewItemCell } from "@/components/practice-trace/ReviewDayTable";
import { PracticeTableRow } from "@/components/practice-vocabulary/PracticeTable";
import { RecordTimeCell } from "@/components/practice-vocabulary/RecordTable";
import { REVIEW_ORIGIN_DEFS } from "@/components/practice-vocabulary/review-origin-defs";
import {
	REVIEW_STATUS_DEFS,
	REVIEW_STOP_REASON_SENTENCES,
} from "@/components/practice-vocabulary/review-status-defs";
import { TableCell } from "@/components/ui/table";
import { formatDayTime } from "@/lib/dates";
import { hasText } from "@/lib/text";

import { RecordRowActions, ROW_OPEN_COLUMN, rowActionsColumn } from "./RecordRowActions";
import type { RowReviewRequest } from "./RequestReviewAction";
import { reviewLevel } from "./review-levels";
import { useTracked } from "./use-tracked";

/** The heads after the time and Work, which every row below fills. */
export const REVIEW_SUMMARY_COLUMNS: readonly ReviewColumn[] = [
	{ label: "Started by", className: "w-44" },
	rowActionsColumn({ request: true }),
	ROW_OPEN_COLUMN,
];

export interface ReviewSummaryRowProps {
	review: ReviewSummary;
	/** True while this review's level is in front of the page; the row keeps a bar on its edge. */
	open?: boolean;
	/** Request review, the row's third action; left out where the screen offers no way to ask. */
	request?: RowReviewRequest;
}

/**
 * One review on the admin list: when it was created and its status, the work with the states worth
 * a second look beside it, and what started it. What it found, practice by practice, is on its
 * sheet: a maintainer scans this list for reviews that need attention, not for counts. The whole row
 * opens the review's sheet over the list, and that is its one link: the work is plain text here.
 * Track narrows every table to the review; Trace opens its trace; Request review asks for a new
 * review of its work.
 */
export function ReviewSummaryRow({ review, open = false, request }: ReviewSummaryRowProps) {
	const today = new Date(useNow());
	const name = review.item.label;
	const when = formatDayTime(review.createdAt, today);
	const tracked = useTracked({ kind: "review", id: review.id });
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
				status={REVIEW_STATUS_DEFS[review.status]}
				at={review.createdAt}
				statusTooltip={false}
			/>
			<ReviewItemCell item={review.item} linked={false}>
				<ReviewMarkers review={review} />
			</ReviewItemCell>
			<TableCell className="align-top whitespace-normal">
				<StartedBy review={review} />
			</TableCell>
		</PracticeTableRow>
	);
}

/**
 * The states of a review worth a second look: failed, or closed with part of it unreviewed. A review
 * with neither has no marker: running, queued, complete and cancelled are the status icon's to say.
 */
export function reviewMarkers(review: Pick<ReviewSummary, "status">): StatusDef[] {
	return review.status === "FAILED" || review.status === "PARTIAL"
		? [REVIEW_STATUS_DEFS[review.status]]
		: [];
}

/** The markers as small chips; each names the recorded stop reason on hover. */
function ReviewMarkers({ review }: { review: ReviewSummary }) {
	const reason =
		review.stopReason === null ? undefined : REVIEW_STOP_REASON_SENTENCES[review.stopReason];
	return reviewMarkers(review).map((def) => (
		<StatusBadge key={def.label} def={def} title={reason} />
	));
}

/** How the review came to run: who asked for it, or the origin's own word. */
function StartedBy({ review }: { review: ReviewSummary }) {
	const requester = review.requestedBy?.displayName;
	if (review.origin === "REQUEST" && hasText(requester)) {
		return <span className="break-words">{requester}</span>;
	}
	return <StatusBadge def={REVIEW_ORIGIN_DEFS[review.origin]} />;
}
