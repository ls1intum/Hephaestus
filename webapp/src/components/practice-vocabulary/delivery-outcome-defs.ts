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

/**
 * The facts a surface needs to say what became of a piece of feedback. Every read model that carries
 * a delivery state carries these three together, so nothing has to be passed as loose scalars.
 */
export type DeliveryFacts = Pick<ReviewFeedback, "channel" | "deliveryState" | "suppressionReason">;

/**
 * **What happened** to a piece of feedback. The second delivery axis; `delivery-place-defs` is the
 * first, and a cell shows one or the other, never a value from each.
 *
 * <p>These entries are keyed by the stored state because that is what the list endpoint filters on.
 * `deliveryOutcome` is what a *row* renders, and it says more than the state alone: the same
 * `DELIVERED` means two different things depending on where it went.
 *
 * <p>"Prepared" does not appear, here or anywhere an operator can read it. It is not a stage every
 * piece of feedback passes through — it is the conversation queue and nothing else. Only one server
 * class writes it and that class hard-codes the conversation lane; feedback going onto the work is
 * posted first and recorded afterwards, already in its final state, so it is never prepared even for
 * an instant. Calling it "prepared" invited the reading the owner objected to, that something was
 * made ready and is now sitting somewhere for every lane. Saying "queued for conversation" says
 * which queue, and implies the one thing that empties it: the developer turning up to talk.
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
 * The two rows where the stored state under-describes what happened, both on the conversation lane.
 *
 * A conversation unit is delivered by the developer showing up, so "delivered" alone loses the one
 * fact worth knowing — that a conversation took place. And a queue entry that times out is stored as
 * an ordinary withholding, when what a reader needs is that the chat never came.
 *
 * <p>Each label **begins with the label of the state it refines**, and that is load-bearing rather
 * than a style. The Outcome facet can only offer the five stored states, so a badge whose words
 * share no stem with any option — "Raised in conversation" against a list starting "Delivered" —
 * leaves a reader unable to find the filter for the row in front of them. Sharing the stem makes the
 * refinement legible as a refinement: pick Outcome "Delivered" and Place "In conversation" to get
 * exactly these rows, and Outcome "Withheld" with reason family "Housekeeping" for the other.
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

/**
 * What a row should say became of this feedback.
 *
 * Takes the record rather than a state, because the answer depends on all three fields — and a
 * caller holding three scalars is a caller that can pair a conversation state with an on-the-work
 * channel and get a sentence that never happens.
 */
export function deliveryOutcome(feedback: DeliveryFacts): StatusDef {
	const { channel, deliveryState, suppressionReason } = feedback;
	if (channel === "CONVERSATION") {
		if (deliveryState === "DELIVERED") return CONVERSATION_OVERRIDES.RAISED;
		if (suppressionReason === "CONVERSATION_EXPIRED") return CONVERSATION_OVERRIDES.EXPIRED;
	}
	return DELIVERY_STATE_DEFS[deliveryState];
}

/** Whether a gate stopped this feedback, which is what earns a trace its middle step. */
export function isWithheld(feedback: DeliveryFacts): boolean {
	return feedback.deliveryState === "SUPPRESSED";
}
