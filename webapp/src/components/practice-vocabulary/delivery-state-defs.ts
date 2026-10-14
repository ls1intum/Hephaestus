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

/**
 * **What happened** to a piece of feedback: its delivery. The second delivery axis;
 * `feedback-destination-defs` is the first, and a cell shows one or the other, never a value from
 * each. A partial package is no state of its own: its copy counts say which copies landed.
 *
 * <p>Keyed by the stored state, because that is what the list endpoint filters on. What a *row*
 * renders is {@link deliveryOutcome}, which refines `READY` and `DELIVERED` per destination.
 */
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

/**
 * The rows where the stored state under-describes what happened, per destination.
 *
 * <p>Each label **must begin with the label of the state it refines**. The state facet can only
 * offer the stored states, so a badge whose words share no stem with any option leaves a reader
 * unable to find the filter for the row in front of them.
 */
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

/**
 * A practice page delivers nothing by itself: the developer opening their own practice pages is
 * the delivery. Saying "waiting for their next chat" about one of these rows would describe an
 * event that will never happen to it.
 */
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

/** What a row should say became of this feedback. */
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

/** Still on its way out: cleared to send at a destination that sends by itself. */
export function isDeliveryInProgress(feedback: DeliveryFacts): boolean {
	return feedback.state === "READY" && feedback.destination === "ON_WORK";
}
