import { CheckCheckIcon, MessageSquareIcon, MessageSquareWarningIcon } from "lucide-react";

import type { ReviewLineageResponse } from "@/api/types.gen";
import type { StatusDefs } from "@/components/common/status-def";

/**
 * What a workspace admin may know about a developer's answer to one piece of feedback: that they
 * answered, whether they dispute it (with the reason they wrote to the admins), and whether their
 * later work resolved it. How they rated it, what else they decided and any note beside it stay
 * private to them; the developer's own words for those are `feedback-usefulness-defs` and
 * `feedback-resolution-defs`, which speak to them as "you".
 */
export type DeveloperAnswer = "ANSWERED" | "DISPUTED" | "RESOLVED_BY_WORK";

export const DEVELOPER_ANSWER_DEFS: StatusDefs<DeveloperAnswer> = {
	ANSWERED: {
		label: "Answered privately",
		icon: MessageSquareIcon,
		badgeVariant: "outline",
		description:
			"The developer answered this feedback. How they rated it and what they decided stay private to them.",
	},
	DISPUTED: {
		label: "Disputed",
		icon: MessageSquareWarningIcon,
		badgeVariant: "warning",
		description:
			"The developer says the feedback is wrong and explained why. Read their reason on the feedback.",
	},
	RESOLVED_BY_WORK: {
		label: "Resolved by the work",
		icon: CheckCheckIcon,
		badgeVariant: "success",
		description:
			"Three pieces of the developer’s work in a row, reviewed after this feedback, came back clean on the practice.",
	},
};

/** What an admin may know of the answers that stand on one piece of feedback. */
export function developerAnswers(response: ReviewLineageResponse | undefined): DeveloperAnswer[] {
	if (response === undefined) {
		return [];
	}
	const answers: DeveloperAnswer[] = [];
	if (response.disputed) {
		answers.push("DISPUTED");
	} else if (response.respondedAt !== undefined) {
		answers.push("ANSWERED");
	}
	if (response.resolvedByWorkAt !== undefined) {
		answers.push("RESOLVED_BY_WORK");
	}
	return answers;
}
