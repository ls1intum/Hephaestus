import {
	CircleAlertIcon,
	CircleCheckIcon,
	CircleSlashIcon,
	ClockIcon,
	LoaderIcon,
	PauseCircleIcon,
	RadarIcon,
	Repeat2Icon,
	TimerOffIcon,
} from "lucide-react";

import type { AgentJob } from "@/api/types.gen";

import type { StatusDef, StatusDefs } from "@/components/common/status-def";

export type ReviewStatus = AgentJob["status"];
export type ResultProcessingStatus = NonNullable<AgentJob["deliveryStatus"]>;

/**
 * Whether a review *ran*. Nothing here says anything about what it observed or who heard about it —
 * those are `outcome-defs` and `delivery-outcome-defs`.
 */
export const REVIEW_STATUS_DEFS: StatusDefs<ReviewStatus> = {
	QUEUED: {
		label: "Queued",
		icon: ClockIcon,
		badgeVariant: "secondary",
		description: "Waiting to be picked up. A queued review may also be parked on a hold.",
	},
	RUNNING: {
		label: "Running",
		icon: LoaderIcon,
		badgeVariant: "secondary",
		description: "Being run now. Results appear as it finishes.",
	},
	COMPLETED: {
		label: "Completed",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "It ran to the end. Whether it found or sent anything is a separate question.",
	},
	FAILED: {
		label: "Failed",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "It stopped on an error and produced no results.",
	},
	TIMED_OUT: {
		label: "Timed out",
		icon: TimerOffIcon,
		badgeVariant: "destructive",
		description: "It ran past the time it is allowed and was stopped.",
	},
	CANCELLED: {
		label: "Canceled",
		icon: CircleSlashIcon,
		badgeVariant: "outline",
		description: "Somebody stopped it before it finished.",
	},
};

/** The Reviews tab's Status filter value for work on which no review started, which no review carries. */
export const NO_REVIEW_STARTED = "NOT_STARTED";

/**
 * The Reviews tab's Status filter value for a queued review that waits on a hold, such as the AI
 * budget: no status of its own, so the server takes it as `onHold` beside the listed statuses.
 */
export const ON_HOLD = "ON_HOLD";

/**
 * The Reviews tab's Status filter value for a queued review that a worker could pick up now: not on
 * a budget hold and not waiting before a retry. These are the reviews the queue counts as Waiting,
 * so its figure opens exactly them; the server takes it as `waiting` beside the listed statuses.
 */
export const WAITING = "WAITING";

export type ReviewStatusFilter =
	| ReviewStatus
	| typeof NO_REVIEW_STARTED
	| typeof ON_HOLD
	| typeof WAITING;

/**
 * What the Reviews tab's Status filter offers: the work on which no review started, then every
 * status a review can have, with the queued reviews that wait now and those on hold after Queued. The first lists work instead of reviews, so it is never combined with
 * the others.
 */
export const REVIEW_STATUS_FILTER_DEFS: StatusDefs<ReviewStatusFilter> = {
	[NO_REVIEW_STARTED]: {
		label: "No review started",
		icon: RadarIcon,
		badgeVariant: "outline",
		description: "Work with recorded moments, none of which started a review, and why.",
	},
	QUEUED: REVIEW_STATUS_DEFS.QUEUED,
	[WAITING]: {
		label: "Waiting",
		icon: ClockIcon,
		badgeVariant: "secondary",
		description:
			"Queued and ready for a worker now: not on hold and not waiting before a retry. The queue counts these as Waiting.",
	},
	[ON_HOLD]: {
		label: "On hold",
		icon: PauseCircleIcon,
		badgeVariant: "warning",
		description:
			"Queued and waiting on a hold, for example because the AI budget is used up. It continues on its own when the hold ends.",
	},
	RUNNING: REVIEW_STATUS_DEFS.RUNNING,
	COMPLETED: REVIEW_STATUS_DEFS.COMPLETED,
	FAILED: REVIEW_STATUS_DEFS.FAILED,
	TIMED_OUT: REVIEW_STATUS_DEFS.TIMED_OUT,
	CANCELLED: REVIEW_STATUS_DEFS.CANCELLED,
};

/** A review a newer one replaced: its status says it ran, this says it does not carry its practices. */
export const REPLACED_REVIEW_STATUS: StatusDef = {
	label: "Replaced",
	icon: Repeat2Icon,
	badgeVariant: "secondary",
	description:
		"A newer admitted review carries this review’s practices and may still be waiting or running.",
};

export function reviewStatusDef(review: Pick<AgentJob, "status" | "reviewOutcome">): StatusDef {
	return review.reviewOutcome === "SUPERSEDED"
		? REPLACED_REVIEW_STATUS
		: REVIEW_STATUS_DEFS[review.status];
}

/**
 * Processing a review's results includes delivering its feedback automatically, so a failed
 * delivery fails processing too; processing that finished still does not establish publication. The
 * job's wire field is `deliveryStatus`; individual feedback records establish whether anything
 * reached a developer.
 */
export const RESULT_PROCESSING_DEFS: StatusDefs<ResultProcessingStatus> = {
	DELIVERED: {
		label: "Results processed",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "Result processing finished. Feedback may still await approval or be withheld.",
	},
	PENDING: {
		label: "Results awaiting processing",
		icon: ClockIcon,
		badgeVariant: "secondary",
		description: "The review results are waiting to be processed.",
	},
	FAILED: {
		label: "Result processing failed",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description:
			"Processing the review results, or delivering the feedback they produced, did not succeed.",
	},
};
