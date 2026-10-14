import { CircleCheckIcon, EyeIcon, EyeOffIcon, XCircleIcon } from "lucide-react";

import type { FeedbackDecision } from "@/api/types.gen";

import type { StatusDefs } from "@/components/common/status-def";

export type FeedbackDecisionAction = FeedbackDecision["action"];
export type FeedbackDecisionReason = NonNullable<FeedbackDecision["reasonCode"]>;

/**
 * A workspace admin's decision about one piece of feedback. Decisions append: a withdrawal or a
 * restore never erases a copy that was already posted, and approval cannot change after the first
 * write starts.
 */
export const FEEDBACK_DECISION_DEFS: StatusDefs<FeedbackDecisionAction> = {
	APPROVE: {
		label: "Approved",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "A workspace admin approved the exact copies shown for sending.",
	},
	REJECT: {
		label: "Rejected",
		icon: XCircleIcon,
		badgeVariant: "outline",
		description: "A workspace admin decided this feedback should not be sent.",
	},
	WITHDRAW: {
		label: "Withdrawn",
		icon: EyeOffIcon,
		badgeVariant: "warning",
		description:
			"A workspace admin withdrew this feedback. Copies already posted keep their receipts.",
	},
	RESTORE: {
		label: "Restored",
		icon: EyeIcon,
		badgeVariant: "secondary",
		description:
			"A workspace admin restored withdrawn feedback. It is sent again only after every check passes.",
	},
};

/**
 * Why an admin rejected or withdrew feedback. Keyed on the wire union, so a reason the server adds
 * fails the build here rather than reaching an admin as a constant name.
 */
const FEEDBACK_DECISION_REASON_LABELS: Record<FeedbackDecisionReason, string> = {
	INCORRECT: "Incorrect",
	MISSING_CONTEXT: "Missing important context",
	UNHELPFUL: "Not useful to the developer",
	DUPLICATE: "Already covered elsewhere",
	INAPPROPRIATE_PLACEMENT: "Wrong place for this feedback",
	OTHER: "Something else",
};

/** The reasons in the order an admin is offered them. */
export const FEEDBACK_DECISION_REASONS = (
	[
		"INCORRECT",
		"MISSING_CONTEXT",
		"UNHELPFUL",
		"DUPLICATE",
		"INAPPROPRIATE_PLACEMENT",
		"OTHER",
	] as const satisfies readonly FeedbackDecisionReason[]
).map((value) => ({ value, label: FEEDBACK_DECISION_REASON_LABELS[value] }));

export function feedbackDecisionReasonLabel(reason: FeedbackDecisionReason): string {
	return FEEDBACK_DECISION_REASON_LABELS[reason];
}
