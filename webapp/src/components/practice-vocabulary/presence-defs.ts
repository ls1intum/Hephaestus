import { CircleHelpIcon, CircleMinusIcon, CircleSlashIcon, EyeIcon } from "lucide-react";
import type { ReviewObservation } from "@/api/types.gen";
import type { StatusDefs } from "./status-def";

export type Presence = ReviewObservation["presence"];

/**
 * Whether the practice was found in the work at all — the question *before* "was it done well",
 * which is `assessment-defs`.
 *
 * None of the four is good or bad on its own, so none of them is coloured: an absent practice is not
 * a failure, it is a practice the work gave no occasion to show. The two negatives stay apart on
 * purpose. `NOT_APPLICABLE` says the practice did not apply here; `INCONCLUSIVE` says it did apply,
 * the evidence was read, and it did not settle the question. Collapsing them would claim nothing
 * here was worth looking at.
 */
export const PRESENCE_DEFS: StatusDefs<Presence> = {
	PRESENT: {
		label: "Observed",
		icon: EyeIcon,
		badgeVariant: "secondary",
		description: "The work shows this practice being followed.",
	},
	ABSENT: {
		label: "Expected but not observed",
		icon: CircleSlashIcon,
		badgeVariant: "outline",
		description: "The work gave an occasion for this practice and did not take it.",
	},
	NOT_APPLICABLE: {
		label: "Not applicable",
		icon: CircleMinusIcon,
		badgeVariant: "outline",
		description: "Nothing in this work called for the practice, so there was nothing to judge.",
	},
	INCONCLUSIVE: {
		label: "Could not be determined",
		icon: CircleHelpIcon,
		badgeVariant: "outline",
		description: "The practice applied, but the evidence available did not settle the question.",
	},
};
