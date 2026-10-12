import {
	BanknoteIcon,
	CircleOffIcon,
	EraserIcon,
	HourglassIcon,
	PowerIcon,
	UserXIcon,
} from "lucide-react";

import type { AgentJob } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";

export type ReviewCancellationReason = NonNullable<AgentJob["cancellationReason"]>;

/** Why a review was canceled before it finished, as the server recorded it. */
export const REVIEW_CANCELLATION_DEFS: StatusDefs<ReviewCancellationReason> = {
	DRAIN_GRACEFUL: {
		label: "The server shut down",
		icon: HourglassIcon,
		badgeVariant: "outline",
		description:
			"The server waited for the review to finish while it shut down, and the wait ran out.",
	},
	DRAIN_IMMEDIATE: {
		label: "The server shut down at once",
		icon: PowerIcon,
		badgeVariant: "outline",
		description: "The server shut down without waiting for the review.",
	},
	BUDGET_EXHAUSTED: {
		label: "The model budget was used up",
		icon: BanknoteIcon,
		badgeVariant: "outline",
		description:
			"When the review was to start, the budget paying for it was used up or could not be checked.",
	},
	MODEL_UNAVAILABLE: {
		label: "The model was no longer available",
		icon: CircleOffIcon,
		badgeVariant: "outline",
		description: "The model the review was asked for on was removed or changed before it started.",
	},
	MEMBER_AI_DECLINED: {
		label: "A developer declined AI review",
		icon: UserXIcon,
		badgeVariant: "outline",
		description:
			"A developer on the work declined AI review, or has not made the choice the workspace requires.",
	},
	PERSON_DATA_ERASED: {
		label: "A person’s data was erased",
		icon: EraserIcon,
		badgeVariant: "outline",
		description: "The work or its author is blocked by a request to erase a person’s data.",
	},
};
