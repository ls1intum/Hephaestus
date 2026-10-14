import { MessageSquareIcon, MessageSquareWarningIcon } from "lucide-react";

import type { FeedbackResponse } from "@/api/types.gen";
import type { StatusDefs } from "@/components/common/status-def";

/**
 * What a workspace admin reads of a developer's response to one piece of feedback: that they
 * answered, and whether they disagree, with the reason they wrote for admins. A response is the
 * developer's, never an admin mark; the developer's own words for it are `feedback-usefulness-defs`
 * and `feedback-resolution-defs`, which speak to them as "you".
 */
export type DeveloperAnswer = "ANSWERED" | "DISAGREED";

export const DEVELOPER_ANSWER_DEFS: StatusDefs<DeveloperAnswer> = {
	ANSWERED: {
		label: "Answered",
		icon: MessageSquareIcon,
		badgeVariant: "outline",
		description: "The developer responded to this feedback.",
	},
	DISAGREED: {
		label: "Disagreed",
		icon: MessageSquareWarningIcon,
		badgeVariant: "warning",
		description:
			"The developer disagrees with the feedback and explained why. Read their reason on the feedback.",
	},
};

/** The answer that stands on one piece of feedback, or none for a cleared or absent response. */
export function developerAnswer(response: FeedbackResponse | null): DeveloperAnswer | undefined {
	if (response === null || response.createdAt === null) {
		return undefined;
	}
	if (response.resolution === "DISAGREE") {
		return "DISAGREED";
	}
	return response.resolution === null && response.usefulness === null ? undefined : "ANSWERED";
}
