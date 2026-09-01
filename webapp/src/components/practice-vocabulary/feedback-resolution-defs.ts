import { CircleCheckIcon, CircleSlashIcon, MessageCircleQuestionMarkIcon } from "lucide-react";

import type { PracticeGroupReviewObservation } from "@/api/types.gen";

import type { StatusDefs } from "./status-def";

export type FeedbackResolution = NonNullable<PracticeGroupReviewObservation["feedbackResolution"]>;

/**
 * What the developer did about a piece of delivered feedback — the half of a response about the
 * work, where `feedback-usefulness-defs` is about the review. The two are independent on the wire
 * and stay independent here.
 *
 * `DISPUTED` is the only value the server demands an explanation for, so its description says so
 * rather than leaving the reader to find out when the form refuses.
 */
export const FEEDBACK_RESOLUTION_DEFS: StatusDefs<FeedbackResolution> = {
	ADDRESSED: {
		label: "Addressed",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "You changed the work in response to this.",
	},
	DISPUTED: {
		label: "Disputed",
		icon: MessageCircleQuestionMarkIcon,
		badgeVariant: "warning",
		description: "You disagree with this observation. Say why, so a human can weigh it.",
	},
	NOT_APPLICABLE: {
		label: "Not applicable",
		icon: CircleSlashIcon,
		badgeVariant: "outline",
		description: "The observation does not apply to this work.",
	},
};
