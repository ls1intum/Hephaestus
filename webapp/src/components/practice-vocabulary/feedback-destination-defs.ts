import { BotMessageSquareIcon, MessageSquareQuoteIcon, UserRoundIcon } from "lucide-react";

import type { ReviewFeedback } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";

export type FeedbackDestination = ReviewFeedback["destination"];

/**
 * **Where** a piece of feedback goes. One of the two delivery axes, and never the same cell as the
 * other one: the destination answers "where", the delivery state answers "what happened".
 *
 * <p>The labels are parallel prepositional phrases so each reads as English inside a sentence —
 * "delivered on the work", "raised in conversation".
 */
export const FEEDBACK_DESTINATION_DEFS: StatusDefs<FeedbackDestination> = {
	ON_WORK: {
		label: "On the work",
		icon: MessageSquareQuoteIcon,
		badgeVariant: "outline",
		description: "Posted on the pull request, issue or document the feedback is about.",
	},
	CONVERSATION: {
		label: "In conversation",
		icon: BotMessageSquareIcon,
		badgeVariant: "outline",
		description:
			"Held back until the developer’s next chat with Heph, then raised there. What is stored is the move to make, not the words, so it is withheld from every admin surface including this one.",
	},
	PRACTICE_PAGE: {
		label: "On their practice pages",
		icon: UserRoundIcon,
		badgeVariant: "outline",
		description:
			"Shown only on the developer’s own practice pages, which is also what delivers it. The text is withheld from every admin surface, including this one.",
	},
};

/**
 * What an admin is told in place of the text for the destinations whose text the server withholds
 * from every admin surface. Absent text there is private, not missing, so it must never read as
 * "nothing was composed"; the conversation may also hold no notes at all, which an admin cannot
 * tell apart, so its words allow for that.
 */
export const ADMIN_WITHHELD_TEXT: Partial<
	Record<FeedbackDestination, { title: string; detail: string }>
> = {
	CONVERSATION: {
		title: "Notes for the conversation are private",
		detail:
			"Any notes prepared for the developer’s next conversation with Heph are private to them and withheld from admins.",
	},
	PRACTICE_PAGE: {
		title: "Text is private to the developer",
		detail:
			"The text was written for the developer’s own practice pages and is withheld from admins.",
	},
};
