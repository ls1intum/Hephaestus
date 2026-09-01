import { ThumbsDownIcon, ThumbsUpIcon } from "lucide-react";

import type { PracticeGroupReviewObservation } from "@/api/types.gen";

import type { StatusDefs } from "./status-def";

export type FeedbackUsefulness = NonNullable<PracticeGroupReviewObservation["feedbackUsefulness"]>;

/**
 * Whether delivered feedback was worth receiving — the half of a response about the review, where
 * `feedback-resolution-defs` is about the work. Neither derives the other: feedback can be useful
 * and still not apply.
 */
export const FEEDBACK_USEFULNESS_DEFS: StatusDefs<FeedbackUsefulness> = {
	HELPFUL: {
		label: "Helpful",
		icon: ThumbsUpIcon,
		badgeVariant: "success",
		description: "This told you something you could act on.",
	},
	UNHELPFUL: {
		label: "Not helpful",
		icon: ThumbsDownIcon,
		badgeVariant: "destructive",
		description: "This was not worth the read.",
	},
};
