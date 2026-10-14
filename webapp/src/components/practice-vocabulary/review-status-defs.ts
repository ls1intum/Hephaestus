import {
	CircleAlertIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	CircleSlashIcon,
	ClockIcon,
	EyeOffIcon,
	HourglassIcon,
	LoaderIcon,
	PackageXIcon,
	TimerOffIcon,
} from "lucide-react";

import type { ReviewJob, ReviewPractice, ReviewRef, ReviewSummary } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";

export type ReviewStatus = ReviewRef["status"];
export type ReviewStopReason = NonNullable<ReviewSummary["stopReason"]>;
export type ReviewPracticeStatus = ReviewPractice["status"];
export type ReviewJobStatus = ReviewJob["status"];
export type ReviewJobProcessingStatus = ReviewJob["processingStatus"];

/**
 * Whether a review *ran*. Nothing here says anything about its results or who heard about them —
 * those are `outcome-defs` and `delivery-state-defs`.
 */
export const REVIEW_STATUS_DEFS: StatusDefs<ReviewStatus> = {
	QUEUED: {
		label: "Queued",
		icon: ClockIcon,
		badgeVariant: "secondary",
		description: "Waiting to be picked up.",
	},
	RUNNING: {
		label: "Running",
		icon: LoaderIcon,
		badgeVariant: "secondary",
		description: "Being run now. Results appear as it finishes.",
	},
	COMPLETE: {
		label: "Complete",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "It ran to the end. Whether it found or sent anything is a separate question.",
	},
	PARTIAL: {
		label: "Partial",
		icon: CircleDashedIcon,
		badgeVariant: "warning",
		description:
			"It closed with some work reviewed and some not. Its results cover only the work it reached.",
	},
	FAILED: {
		label: "Failed",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "It stopped on an error. Results it recorded before then still stand.",
	},
	CANCELLED: {
		label: "Cancelled",
		icon: CircleSlashIcon,
		badgeVariant: "outline",
		description: "It was stopped before it finished.",
	},
};

/**
 * How a review ended, as the developer's own surfaces read it: coarser than the admin console,
 * since a partial review and a cancelled one both mean "it did not finish" to them.
 */
export const OWN_REVIEW_STATUS_DEFS: StatusDefs<ReviewStatus> = {
	QUEUED: {
		label: "Waiting",
		icon: ClockIcon,
		badgeVariant: "secondary",
		description: "The review is waiting to start.",
	},
	RUNNING: {
		label: "Running",
		icon: LoaderIcon,
		badgeVariant: "secondary",
		description: "The review is running. What it records appears as it finishes.",
	},
	COMPLETE: {
		label: "Completed",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "The review ran to the end. Whether it found anything is a separate question.",
	},
	PARTIAL: {
		label: "Did not finish",
		icon: CircleDashedIcon,
		badgeVariant: "warning",
		description: "The review stopped before it finished. What it recorded up to then still stands.",
	},
	FAILED: {
		label: "Failed",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "The review stopped before it finished. What it recorded up to then still stands.",
	},
	CANCELLED: {
		label: "Cancelled",
		icon: CircleSlashIcon,
		badgeVariant: "outline",
		description: "The review was stopped before it finished.",
	},
};

/** Whether a review is still going: the states a page polls and a Cancel action applies to. */
export function isActiveReview(status: ReviewStatus): boolean {
	return status === "QUEUED" || status === "RUNNING";
}

/** Why a review stopped, as the server recorded it. */
export const REVIEW_STOP_REASON_SENTENCES: Record<ReviewStopReason, string> = {
	TIMED_OUT: "It ran past the time it is allowed and was stopped.",
	EXECUTION_FAILED: "Running the review failed.",
	PREPARATION_FAILED: "Preparing the review failed.",
	PROCESSING_FAILED: "Processing the review’s results failed.",
	PERSON_DATA_ERASED: "A person’s data was erased.",
	STALE_ROLLOUT_REVISION: "Review settings changed while it waited.",
	CANCELLED_BY_ADMIN: "A workspace admin cancelled it.",
	REPLACED: "A newer review of the same work replaced it.",
	LEGACY_FAILURE: "It failed before Hephaestus recorded why.",
	BUDGET_EXHAUSTED: "The review budget was used up.",
	DEADLINE_EXCEEDED: "It did not finish before its deadline.",
	CANCELLED_BY_REQUESTER: "The person who asked for it cancelled it.",
	EXECUTION_INCOMPLETE: "It did not review every practice it started.",
	RESULT_WITHHELD: "Its results were withheld.",
	READINESS_REFUSED: "Something the review needs was not ready.",
	SUSPECT_LISTING: "The file listing changed too much to trust, so files were not removed.",
	INVENTORY_INCOMPLETE: "The repository’s file list was incomplete.",
	BUDGET: "The scan’s budget was used up.",
	DEADLINE: "The scan did not finish before its deadline.",
	CANCELLED: "It was cancelled.",
	ENUMERATION_FAILED: "Listing the repository’s files failed.",
	ENUMERATION_LIMIT: "The repository has more files than one scan can list.",
	NO_SCAN_PRACTICES: "No practice is included in scans.",
	MODEL_CHANGED: "The assigned AI model changed while it ran.",
	MODEL_UNAVAILABLE: "The assigned AI model was unavailable.",
	SOURCE_UNAVAILABLE: "Something it reads was unavailable.",
	ERROR: "It stopped on an error.",
	LEGACY_SCOPE_UNKNOWN: "It ran before Hephaestus recorded what it covered.",
	CONTRACT_CHANGED: "Hephaestus was upgraded while it waited, so it stopped.",
};

/** Where one practice stands inside one review. Withheld is a decision with a reason, not a result. */
export const REVIEW_PRACTICE_STATUS_DEFS: StatusDefs<ReviewPracticeStatus> = {
	QUEUED: {
		label: "Queued",
		icon: ClockIcon,
		badgeVariant: "secondary",
		description: "Waiting for its turn in the review.",
	},
	READY: {
		label: "Ready",
		icon: HourglassIcon,
		badgeVariant: "secondary",
		description: "Everything it reads was captured. It runs next.",
	},
	RUNNING: {
		label: "Running",
		icon: LoaderIcon,
		badgeVariant: "secondary",
		description: "Being reviewed now.",
	},
	DONE: {
		label: "Done",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "It recorded its results.",
	},
	WITHHELD: {
		label: "Withheld",
		icon: EyeOffIcon,
		badgeVariant: "warning",
		description: "It was not reviewed, for the recorded reason. This is not a result.",
	},
	FAILED: {
		label: "Failed",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "Reviewing it failed. It recorded no result.",
	},
};

/** One runtime job of a review. A scan runs many jobs; a review is not its job. */
export const REVIEW_JOB_STATUS_DEFS: StatusDefs<ReviewJobStatus> = {
	QUEUED: {
		label: "Queued",
		icon: ClockIcon,
		badgeVariant: "secondary",
		description: "Waiting for a worker.",
	},
	RUNNING: {
		label: "Running",
		icon: LoaderIcon,
		badgeVariant: "secondary",
		description: "A worker is running it.",
	},
	COMPLETED: {
		label: "Completed",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "It ran to the end.",
	},
	FAILED: {
		label: "Failed",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "It stopped on an error.",
	},
	TIMED_OUT: {
		label: "Timed out",
		icon: TimerOffIcon,
		badgeVariant: "destructive",
		description: "It ran past the time it is allowed and was stopped.",
	},
	CANCELLED: {
		label: "Cancelled",
		icon: CircleSlashIcon,
		badgeVariant: "outline",
		description: "It was stopped before it finished.",
	},
};

/** Whether one job's output was processed into results. Processing never proves delivery. */
export const REVIEW_JOB_PROCESSING_DEFS: StatusDefs<ReviewJobProcessingStatus> = {
	NONE: {
		label: "Nothing to process",
		icon: PackageXIcon,
		badgeVariant: "outline",
		description: "The job produced no output to process.",
	},
	PENDING: {
		label: "Awaiting processing",
		icon: ClockIcon,
		badgeVariant: "secondary",
		description: "Its output is waiting to be processed.",
	},
	DONE: {
		label: "Processed",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "Its output was processed. Feedback may still await approval or be withheld.",
	},
	FAILED: {
		label: "Processing failed",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "Processing its output failed.",
	},
};
