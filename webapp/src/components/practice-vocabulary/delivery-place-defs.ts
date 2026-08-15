import { BotMessageSquareIcon, MessageSquareQuoteIcon, UserRoundIcon } from "lucide-react";
import type { ReviewFeedback } from "@/api/types.gen";
import type { StatusDefs } from "./status-def";

export type DeliveryPlace = ReviewFeedback["channel"];

/**
 * **Where** a piece of feedback goes. One of the two delivery axes, and never the same cell as the
 * other one: place answers "where", `deliveryOutcome` answers "what happened".
 *
 * <p>The labels are parallel prepositional phrases so each reads as English inside a sentence —
 * "delivered on the work", "raised in conversation".
 */
export const DELIVERY_PLACE_DEFS: StatusDefs<DeliveryPlace> = {
	IN_CONTEXT: {
		label: "On the work",
		icon: MessageSquareQuoteIcon,
		badgeVariant: "outline",
		description: "Posted on the pull request, issue or document the feedback is about.",
	},
	CONVERSATION: {
		label: "In conversation",
		icon: BotMessageSquareIcon,
		badgeVariant: "outline",
		description: "Held back until the developer's next chat with the mentor, then raised there.",
	},
	PROFILE: {
		label: "On the profile",
		icon: UserRoundIcon,
		badgeVariant: "outline",
		description: "Shown only on the developer's own profile.",
	},
};

/**
 * The places worth offering as a filter. Nothing writes a `PROFILE` unit —
 * `ProfileChannelUnwrittenArchTest` fails the build if any production class so much as reads the
 * constant — so a filter for it could only ever return an empty page, which an operator would read
 * as "none today" rather than "never any". The registry above still keeps its words, against the
 * day the server does write one.
 */
export const FILTERABLE_PLACES: readonly DeliveryPlace[] = ["IN_CONTEXT", "CONVERSATION"];
