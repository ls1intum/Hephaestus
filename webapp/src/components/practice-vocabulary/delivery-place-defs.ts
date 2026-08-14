import { BotMessageSquareIcon, MessageSquareQuoteIcon, UserRoundIcon } from "lucide-react";
import type { ReviewFeedback } from "@/api/types.gen";
import type { StatusDefs } from "./status-def";

export type DeliveryPlace = ReviewFeedback["channel"];

/**
 * **Where** a piece of feedback goes. One of the two delivery axes, and never the same cell as the
 * other one.
 *
 * The surfaces used to show place and outcome interchangeably — a table cell printed the withholding
 * reason *or* the place, whichever happened to be set — so a reader could not tell whether a row was
 * telling them what happened or where it would have happened. Place answers "where", `deliveryOutcome`
 * answers "what happened", and a row shows both or neither.
 *
 * <p>On the words: the product owner proposed "in-context / private view / conversation". These are
 * near-misses of that and the difference is deliberate. "In-context" is the wire constant
 * (`IN_CONTEXT`) with a hyphen in it, and echoing a constant at an operator is the same failure that
 * put "findings" on these screens. "Private view" names a screen this product does not have. The
 * three below are parallel prepositional phrases instead, so a column of them scans as one question
 * being answered three ways, and each one reads as English inside a sentence: "delivered on the
 * work", "raised in conversation".
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
 * The places worth offering as a filter — everything except `PROFILE`.
 *
 * Nothing writes a `PROFILE` unit. `FeedbackReach.reaches` returns `false` for it, no builder in the
 * server sets it, and `ProfileChannelUnwrittenArchTest` fails the build if any production class so
 * much as reads the constant. A filter for it can therefore only ever return an empty page, which
 * reads to an operator as "there is none of this today" rather than "there can never be any".
 *
 * <p>The registry above stays total on purpose. Hiding the option is a product decision about what to
 * offer; deleting the words would mean a row rendering blank on the day the server starts writing one.
 */
export const FILTERABLE_PLACES: readonly DeliveryPlace[] = ["IN_CONTEXT", "CONVERSATION"];
