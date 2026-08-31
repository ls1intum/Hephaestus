import { ThumbsDownIcon, ThumbsUpIcon } from "lucide-react";

import type { PracticeGroupReviewObservation } from "@/api/types.gen";

import type { StatusDefs } from "./status-def";

export type FeedbackUsefulness = NonNullable<PracticeGroupReviewObservation["feedbackUsefulness"]>;

/**
 * Whether a piece of delivered feedback was worth receiving — the half of a response that is about
 * the review, where resolution is about the work.
 *
 * Kept beside `feedback-resolution-defs` so the two halves of one answer are typed and worded in one
 * place. They used to be split: resolution had a registry while usefulness was written inline at its
 * call site, and the tinted background that inline pair chose put its label at 4.46:1 — just under
 * WCAG 2.2 SC 1.4.3. Rendering both through the registry settles that by construction.
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
