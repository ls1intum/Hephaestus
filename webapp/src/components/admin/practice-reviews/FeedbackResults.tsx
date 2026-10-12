import { MessageSquareTextIcon } from "lucide-react";

import { cn } from "cn";
import type { ReviewFeedback } from "@/api/types.gen";
import { statusToneClass } from "@/components/common/status-def";
import { useNow } from "@/components/common/use-now";
import { DetailStackLink } from "@/components/layout/detail-drawer/DetailStackLink";
import { ReviewRunWorkCell } from "@/components/practice-trace/ReviewRunDayTable";
import { deliveryOutcome } from "@/components/practice-vocabulary/delivery-outcome-defs";
import { FEEDBACK_LOCATION_DEFS } from "@/components/practice-vocabulary/feedback-location-defs";
import { PracticeTableRow } from "@/components/practice-vocabulary/PracticeTable";
import {
	RecordDayTable,
	type RecordTableColumn,
	RecordTimeCell,
} from "@/components/practice-vocabulary/RecordTable";
import { StatusLabel } from "@/components/practice-vocabulary/StatusTooltip";
import {
	WITHHOLDING_FAMILY_DEFS,
	withholdingFamily,
	withholdingReasonSentence,
} from "@/components/practice-vocabulary/withholding-defs";
import { TableCell } from "@/components/ui/table";
import { formatDayTime } from "@/lib/dates";

import { RecordResults, type RecordResultsWords } from "./RecordResults";
import { RecordRowActions, ROW_OPEN_COLUMN, rowActionsColumn } from "./RecordRowActions";
import { subjectLabel } from "./review-format";
import { feedbackLevel } from "./review-levels";
import { ReviewPerson } from "./ReviewPerson";
import { useTracked } from "./use-tracked";

/** A filtered empty state has to carry the way out of itself. */
export type FeedbackResultsState =
	| { status: "loading" }
	| { status: "error"; error: unknown; onRetry: () => void }
	| { status: "empty"; filtered: false }
	| { status: "empty"; filtered: true; onClearFilters: () => void }
	| { status: "ready"; feedback: ReviewFeedback[] };

export interface FeedbackResultsProps {
	state: FeedbackResultsState;
}

const LIST_LABEL = "Feedback";

/** The heads of the Feedback table, which every row below fills. */
export const FEEDBACK_COLUMNS: readonly RecordTableColumn[] = [
	{ label: "Composed", className: "w-24 whitespace-nowrap" },
	{ label: "Channel", className: "w-36" },
	{ label: "Work" },
	{ label: "Recipient", className: "w-44" },
	{ label: "Outcome", className: "w-56" },
	rowActionsColumn(),
	ROW_OPEN_COLUMN,
];

const FEEDBACK_WORDS: RecordResultsWords = {
	errorTitle: "We could not load feedback",
	icon: MessageSquareTextIcon,
	emptyTitle: "No feedback yet",
	filteredTitle: "No feedback matches these filters",
	emptyDescription: "Delivered and withheld feedback appears here after reviews run.",
};

/** Feedback by day, drawn as the Reviews table draws reviews. */
export function FeedbackResults({ state }: FeedbackResultsProps) {
	return (
		<RecordResults
			state={state}
			aria-label={LIST_LABEL}
			columns={FEEDBACK_COLUMNS}
			words={FEEDBACK_WORDS}
		>
			{(empty) => (
				<RecordDayTable
					aria-label={LIST_LABEL}
					columns={FEEDBACK_COLUMNS}
					rows={state.status === "ready" ? state.feedback : []}
					dateOf={(item) => item.createdAt}
					rowKey={(item) => item.id}
					renderRow={(item) => <FeedbackRow feedback={item} />}
					empty={empty}
				/>
			)}
		</RecordResults>
	);
}

export interface FeedbackRowProps {
	feedback: ReviewFeedback;
}

/**
 * What tells one row from the next without its text: where it went, who it was for and on which
 * work. "GitHub comment for Ada Lovelace on #1423".
 */
export function feedbackRowName(feedback: ReviewFeedback): string {
	const location = FEEDBACK_LOCATION_DEFS[feedback.location].label;
	const work = feedback.reviewedWork?.label;
	const on = work === undefined ? "" : ` on ${work}`;
	return `${location} for ${subjectLabel(feedback.recipient)}${on}`;
}

/**
 * One piece of feedback, drawn as a review row is: the time, where it went, the work, who it was
 * for, and what became of it. Its text is on its sheet: a row is told apart by its time, channel,
 * work, recipient and outcome, which its accessible names say too. The outcome is its icon and label, and a
 * withheld row adds why, in the words the "Why withheld" filter offers, with the precise reason on
 * hover. The whole row opens the feedback's sheet, and that is its one link; Track and Trace stand
 * in the actions column.
 */
export function FeedbackRow({ feedback }: FeedbackRowProps) {
	const today = new Date(useNow());
	const tracked = useTracked({ kind: "feedback", id: feedback.id });
	const name = feedbackRowName(feedback);
	const when = formatDayTime(feedback.createdAt, today);
	const outcome = deliveryOutcome(feedback);
	const location = FEEDBACK_LOCATION_DEFS[feedback.location];
	const LocationIcon = location.icon;
	return (
		<PracticeTableRow
			link={{
				text: "Open feedback",
				name: `${name}, ${outcome.label}, ${when}`,
				render: <DetailStackLink entry={feedbackLevel(feedback.id)} single />,
			}}
			variant={tracked ? "highlighted" : undefined}
			action={
				<RecordRowActions
					record={{ kind: "feedback", id: feedback.id }}
					agentJobId={feedback.agentJobId}
					name={`feedback ${name}, ${outcome.label}, ${when}`}
				/>
			}
		>
			<RecordTimeCell at={feedback.createdAt} />
			<TableCell className="align-top text-xs whitespace-normal">
				<span className="inline-flex min-w-0 items-center gap-1.5">
					<LocationIcon
						aria-hidden
						className={cn("size-3.5 shrink-0", statusToneClass(location.badgeVariant))}
					/>
					<span className="min-w-0 break-words">{location.label}</span>
				</span>
			</TableCell>
			{feedback.reviewedWork === undefined ? (
				<TableCell className="align-top text-xs text-muted-foreground">No reviewed work</TableCell>
			) : (
				<ReviewRunWorkCell work={feedback.reviewedWork} linked={false} />
			)}
			<TableCell className="align-top whitespace-normal">
				<ReviewPerson person={feedback.recipient} />
			</TableCell>
			<TableCell className="align-top whitespace-normal">
				<span className="flex min-w-0 flex-col items-start gap-1">
					<StatusLabel def={outcome} className="text-xs font-medium" />
					{feedback.suppressionReason !== undefined && (
						<span title={withholdingReasonSentence(feedback.suppressionReason)}>
							<StatusLabel
								def={WITHHOLDING_FAMILY_DEFS[withholdingFamily(feedback.suppressionReason)]}
								className="text-xs text-muted-foreground"
							/>
						</span>
					)}
				</span>
			</TableCell>
		</PracticeTableRow>
	);
}
