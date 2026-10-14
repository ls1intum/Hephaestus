import { CircleStopIcon, SendIcon, UserRoundCheckIcon } from "lucide-react";

import {
	FEEDBACK_MODE_DESCRIPTIONS,
	FEEDBACK_MODE_LABELS,
	type FeedbackMode,
} from "@/lib/practice-feedback-mode";

import type { StatusDefs } from "@/components/common/status-def";

// Feedback-mode words are owned by lib/practice-feedback-mode; this registry supplies presentation.
export const FEEDBACK_MODE_DEFS: StatusDefs<FeedbackMode> = {
	OFF: {
		label: FEEDBACK_MODE_LABELS.OFF,
		icon: CircleStopIcon,
		badgeVariant: "secondary",
		description: FEEDBACK_MODE_DESCRIPTIONS.OFF,
	},
	APPROVE_FIRST: {
		label: FEEDBACK_MODE_LABELS.APPROVE_FIRST,
		icon: UserRoundCheckIcon,
		badgeVariant: "warning",
		description: FEEDBACK_MODE_DESCRIPTIONS.APPROVE_FIRST,
	},
	AUTOMATIC: {
		label: FEEDBACK_MODE_LABELS.AUTOMATIC,
		icon: SendIcon,
		badgeVariant: "success",
		description: FEEDBACK_MODE_DESCRIPTIONS.AUTOMATIC,
	},
};
