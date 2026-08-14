import {
	CircleAlertIcon,
	CircleCheckIcon,
	CircleSlashIcon,
	ClockIcon,
	LoaderIcon,
	TimerOffIcon,
} from "lucide-react";
import type { AgentJob } from "@/api/types.gen";
import type { StatusDefs } from "./status-def";

export type ReviewStatus = AgentJob["status"];
export type SummaryPostStatus = NonNullable<AgentJob["deliveryStatus"]>;

/**
 * Whether a review *ran*. Nothing here says anything about what it found or who heard about it —
 * those are `assessment-defs` and `delivery-outcome-defs`.
 *
 * <p>Moved here from the AI console's `job-utils`. The Reviews screen was importing its status
 * vocabulary from a module about agent jobs, so the same status wore a solid primary badge on one
 * screen and plain text in the filter beside it. It is a review status wherever it is shown, so it
 * lives with the other review vocabulary and the agent console can import it back if it grows a use.
 *
 * <p>`COMPLETED` is `success` rather than the primary fill it used to have: primary is the app's
 * action colour, and a finished review is not an action.
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
		description: "Being run now; results appear as it finishes.",
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
		label: "Cancelled",
		icon: CircleSlashIcon,
		badgeVariant: "outline",
		description: "Somebody stopped it before it finished.",
	},
};

/**
 * Whether the review's own summary comment made it onto the work — a different thing from a piece of
 * feedback being delivered, and named differently on purpose.
 *
 * The wire calls all three of these "delivery" too, which is how a review detail screen ended up
 * showing "Delivered" twice about two unrelated things. Every label here says "summary", so the
 * badge is unambiguous standing on its own next to a run status.
 */
export const SUMMARY_POST_DEFS: StatusDefs<SummaryPostStatus> = {
	DELIVERED: {
		label: "Summary posted",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "The review's own summary comment is on the work.",
	},
	PENDING: {
		label: "Summary not posted yet",
		icon: ClockIcon,
		badgeVariant: "secondary",
		description: "The summary comment is still waiting to be posted.",
	},
	FAILED: {
		label: "Summary failed to post",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "Posting the summary comment was attempted and did not succeed.",
	},
};
