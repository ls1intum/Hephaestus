import { ThumbsDownIcon, ThumbsUpIcon } from "lucide-react";

import type { PracticeGroupReviewObservation } from "@/api/types.gen";

import type { StatusDefs } from "./status-def";

export type FeedbackUsefulness = NonNullable<PracticeGroupReviewObservation["feedbackUsefulness"]>;

/**
 * Whether delivered feedback was worth receiving — the half of a response about the review, where
 * resolution is about the work. Written inline once, where its tinted background left the label at
 * 4.46:1, under WCAG 2.2 SC 1.4.3; rendering both halves from a registry settles that.
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
