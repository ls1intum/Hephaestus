import {
	BotMessageSquareIcon,
	CircleAlertIcon,
	CircleCheckIcon,
	CircleHelpIcon,
	ClipboardCheckIcon,
	ClockIcon,
	EyeOffIcon,
	HistoryIcon,
	HourglassIcon,
	MessageSquareDashedIcon,
	UserRoundIcon,
	XCircleIcon,
} from "lucide-react";

import type { ReviewFeedback } from "@/api/types.gen";

import type { StatusDef, StatusDefs } from "@/components/common/status-def";

export type DeliveryState = ReviewFeedback["state"];

export type DeliveryFacts = Pick<ReviewFeedback, "destination" | "state" | "stopReason">;

export const DELIVERY_STATE_DEFS: StatusDefs<DeliveryState> = {
	AWAITING_APPROVAL: {
		label: "Awaiting approval",
		icon: ClipboardCheckIcon,
		badgeVariant: "secondary",
		description: "A workspace admin must approve or reject it before anything is sent.",
	},
	READY: {
		label: "Ready",
		icon: ClockIcon,
		badgeVariant: "secondary",
		description:
			"Composed, and waiting for the moment that delivers it. That moment differs by destination.",
	},
	DELIVERED: {
		label: "Delivered",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "It reached the developer at its destination.",
	},
	UNCONFIRMED: {
		label: "Delivery unconfirmed",
		icon: CircleHelpIcon,
		badgeVariant: "outline",
		description: "The conversation linked this feedback, but its appearance was not recorded.",
	},
	WITHHELD: {
		label: "Withheld",
		icon: EyeOffIcon,
		badgeVariant: "warning",
		description: "Composed, then deliberately not sent. The reason says who decided and why.",
	},
	REJECTED: {
		label: "Rejected",
		icon: XCircleIcon,
		badgeVariant: "outline",
		description: "A workspace admin decided this feedback should not be sent.",
	},
	REPLACED: {
		label: "Replaced by newer",
		icon: HistoryIcon,
		badgeVariant: "outline",
		description: "A later piece of feedback about the same work took its place.",
	},
	FAILED: {
		label: "Failed to deliver",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "Sending was attempted and did not succeed.",
	},
};

// Refined delivery labels must begin with the base state label so filters still match what rows say.
const CONVERSATION_OVERRIDES = {
	READY: {
		label: "Ready for conversation",
		icon: MessageSquareDashedIcon,
		badgeVariant: "secondary",
		description: "Waiting for the developer’s next chat with Heph, which is what sends it.",
	},
	DELIVERED: {
		label: "Delivered in conversation",
		icon: BotMessageSquareIcon,
		badgeVariant: "success",
		description: "Heph showed it to the developer in a reply to their next chat.",
	},
	EXPIRED: {
		label: "Withheld, never raised",
		icon: HourglassIcon,
		badgeVariant: "warning",
		description: "It stayed ready for conversation until it expired. Heph never raised it.",
	},
} as const satisfies Record<string, StatusDef>;

// Practice-page feedback is delivered when its developer opens the page; conversation feedback waits for a turn.
const PRACTICE_PAGE_READY: StatusDef = {
	label: "Ready for their practice pages",
	icon: UserRoundIcon,
	badgeVariant: "secondary",
	description:
		"Waiting on the developer’s own practice pages. The feedback is delivered when the developer opens it.",
};

const ON_WORK_READY: StatusDef = {
	label: "Ready to send",
	icon: ClockIcon,
	badgeVariant: "secondary",
	description: "It is cleared to send. Provider delivery is in progress and may be retried safely.",
};

export function deliveryOutcome(feedback: DeliveryFacts): StatusDef {
	const { destination, state, stopReason } = feedback;
	if (destination === "CONVERSATION") {
		if (state === "READY") {
			return CONVERSATION_OVERRIDES.READY;
		}
		if (state === "DELIVERED") {
			return CONVERSATION_OVERRIDES.DELIVERED;
		}
		if (stopReason === "CONVERSATION_EXPIRED") {
			return CONVERSATION_OVERRIDES.EXPIRED;
		}
	}
	if (state === "READY") {
		return destination === "PRACTICE_PAGE" ? PRACTICE_PAGE_READY : ON_WORK_READY;
	}
	return DELIVERY_STATE_DEFS[state];
}

export function isDeliveryInProgress(feedback: DeliveryFacts): boolean {
	return feedback.state === "READY" && feedback.destination === "ON_WORK";
}
