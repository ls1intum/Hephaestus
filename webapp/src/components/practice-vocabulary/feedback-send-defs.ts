import { CheckCheckIcon, CircleHelpIcon, ClockIcon, LoaderIcon } from "lucide-react";

import type { FeedbackSend } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";

export type FeedbackSendState = FeedbackSend["state"];
export type FeedbackSendAction = FeedbackSend["action"];

/**
 * Where one send to the provider stands. A label names the event on the feedback's sheet, under the
 * delivery state, so it says what happened to the send rather than repeating that state.
 */
export const FEEDBACK_SEND_STATE_DEFS: StatusDefs<FeedbackSendState> = {
	PENDING: {
		label: "Waiting to be sent",
		icon: ClockIcon,
		badgeVariant: "outline",
		description: "It is sent at its next attempt.",
	},
	SENDING: {
		label: "Being sent",
		icon: LoaderIcon,
		badgeVariant: "secondary",
		description: "A server is sending it now.",
	},
	UNCERTAIN: {
		label: "Not confirmed",
		icon: CircleHelpIcon,
		badgeVariant: "warning",
		description:
			"The provider may have posted it without confirming. It is checked before anything is posted again.",
	},
	DONE: {
		label: "Done",
		icon: CheckCheckIcon,
		badgeVariant: "success",
		description: "Every copy in this send reached its final state.",
	},
};

/** What one send did to the provider. */
export const FEEDBACK_SEND_ACTION_LABELS: Record<FeedbackSendAction, string> = {
	POST: "Post the copies",
	RETRACT: "Retract the posted copies",
};
