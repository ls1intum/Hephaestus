import {
	BotMessageSquareIcon,
	CircleAlertIcon,
	CircleCheckIcon,
	EyeOffIcon,
	HistoryIcon,
	HourglassIcon,
	MessageSquareDashedIcon,
} from "lucide-react";
import type { ReviewFeedback } from "@/api/types.gen";
import type { StatusDef, StatusDefs } from "./status-def";

export type DeliveryState = ReviewFeedback["deliveryState"];

export type DeliveryFacts = Pick<ReviewFeedback, "channel" | "deliveryState" | "suppressionReason">;

/**
 * **What happened** to a piece of feedback. The second delivery axis; `delivery-place-defs` is the
 * first, and a cell shows one or the other, never a value from each.
 *
 * <p>Keyed by the stored state, because that is what the list endpoint filters on. What a *row*
 * renders is {@link deliveryOutcome}, which says more: `DELIVERED` means two different things
 * depending on the lane it went down. `PREPARED` only ever occurs on the conversation lane.
 */
export const DELIVERY_STATE_DEFS: StatusDefs<DeliveryState> = {
	DELIVERED: {
		label: "Delivered",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "It reached the developer where it was placed.",
	},
	PREPARED: {
		label: "Queued for conversation",
		icon: MessageSquareDashedIcon,
		badgeVariant: "secondary",
		description: "Waiting for the developer's next chat with the mentor, which is what sends it.",
	},
	SUPERSEDED: {
		label: "Replaced by newer",
		icon: HistoryIcon,
		badgeVariant: "outline",
		description: "A later piece of feedback about the same work took its place.",
	},
	SUPPRESSED: {
		label: "Withheld",
		icon: EyeOffIcon,
		badgeVariant: "warning",
		description: "Composed, then deliberately not sent. The reason says who decided and why.",
	},
	FAILED: {
		label: "Failed to deliver",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "Sending was attempted and did not succeed.",
	},
};

/**
 * The rows where the stored state under-describes what happened, both on the conversation lane.
 *
 * <p>Each label **must begin with the label of the state it refines**. The Outcome facet can only
 * offer the stored states, so a badge whose words share no stem with any option leaves a reader
 * unable to find the filter for the row in front of them.
 */
const CONVERSATION_OVERRIDES = {
	RAISED: {
		label: "Delivered in conversation",
		icon: BotMessageSquareIcon,
		badgeVariant: "success",
		description: "The mentor raised it the next time the developer was talking to it.",
	},
	EXPIRED: {
		label: "Withheld, never raised",
		icon: HourglassIcon,
		badgeVariant: "warning",
		description: "It sat in the conversation queue until it aged out, and was never said.",
	},
} as const satisfies Record<string, StatusDef>;

/** What a row should say became of this feedback. */
export function deliveryOutcome(feedback: DeliveryFacts): StatusDef {
	const { channel, deliveryState, suppressionReason } = feedback;
	if (channel === "CONVERSATION") {
		if (deliveryState === "DELIVERED") return CONVERSATION_OVERRIDES.RAISED;
		if (suppressionReason === "CONVERSATION_EXPIRED") return CONVERSATION_OVERRIDES.EXPIRED;
	}
	return DELIVERY_STATE_DEFS[deliveryState];
}

export function isWithheld(feedback: DeliveryFacts): boolean {
	return feedback.deliveryState === "SUPPRESSED";
}
