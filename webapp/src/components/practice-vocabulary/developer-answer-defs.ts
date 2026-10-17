import { MessageSquareIcon, MessageSquareWarningIcon } from "lucide-react";

import type { FeedbackResponse } from "@/api/types.gen";
import type { StatusDefs } from "@/components/common/status-def";

// Admins may read a disagreement reason; usefulness and private notes remain the developer's.
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

export function developerAnswer(response: FeedbackResponse | null): DeveloperAnswer | undefined {
	if (response === null || response.createdAt === null) {
		return undefined;
	}
	if (response.resolution === "DISAGREE") {
		return "DISAGREED";
	}
	return response.resolution === null && response.usefulness === null ? undefined : "ANSWERED";
}
