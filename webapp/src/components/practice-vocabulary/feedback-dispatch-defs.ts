import {
	CheckCheckIcon,
	CircleAlertIcon,
	CircleHelpIcon,
	ClockIcon,
	LoaderIcon,
	ShieldOffIcon,
} from "lucide-react";

import type { ReviewFeedbackDispatch } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";

export type FeedbackDispatchState = ReviewFeedbackDispatch["state"];
export type FeedbackDispatchDestination = ReviewFeedbackDispatch["destination"];

/**
 * Where one delivery to the provider stands: the summary and line comments it carries are sent
 * together and tried again until they are sent, held back or given up. A label names the event on
 * the feedback's sheet, under the feedback's outcome, so it says what happened to the delivery
 * rather than repeating that outcome.
 */
export const FEEDBACK_DISPATCH_STATE_DEFS: StatusDefs<FeedbackDispatchState> = {
	PENDING: {
		label: "Waiting to be sent",
		icon: ClockIcon,
		badgeVariant: "outline",
		description: "It is sent at its next attempt.",
	},
	CLAIMED: {
		label: "Being sent",
		icon: LoaderIcon,
		badgeVariant: "secondary",
		description: "A server is sending it now.",
	},
	SENT: {
		label: "Sent",
		icon: CheckCheckIcon,
		badgeVariant: "success",
		description: "The provider confirmed what was posted.",
	},
	SUPPRESSED: {
		label: "Held back",
		icon: ShieldOffIcon,
		badgeVariant: "secondary",
		description: "A delivery check stopped it before anything was posted.",
	},
	UNCERTAIN: {
		label: "Not confirmed",
		icon: CircleHelpIcon,
		badgeVariant: "warning",
		description:
			"The provider may have posted it without confirming. It is checked before anything is posted again.",
	},
	FAILED: {
		// Not "Failed": the feedback's own outcome already says "Failed to deliver" above the event.
		label: "Stopped trying",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "Its attempts failed and it is no longer tried.",
	},
};

/** What one delivery carried: the review's own comments, or one approved proposal. */
export const FEEDBACK_DISPATCH_DESTINATION_LABELS: Record<FeedbackDispatchDestination, string> = {
	AUTOMATIC_REVIEW_PACKAGE: "The review’s comments",
	APPROVED_REVIEW_PACKAGE: "The approved comments",
};
