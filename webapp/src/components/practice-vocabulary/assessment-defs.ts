import { CircleCheckIcon, WrenchIcon } from "lucide-react";

import type { ReviewObservation } from "@/api/types.gen";

import type { StatusDefs } from "./status-def";

export type Assessment = NonNullable<ReviewObservation["assessment"]>;

/**
 * How the practice was followed, once `presence-defs` has established that it was in play.
 *
 * `BAD` wears a wrench rather than an alert icon because the severity badge, which does wear one,
 * sits directly beside it on every row that has one.
 */
export const ASSESSMENT_DEFS: StatusDefs<Assessment> = {
	GOOD: {
		label: "Strength",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "The work does this well and the author is told so.",
	},
	BAD: {
		label: "Needs improvement",
		icon: WrenchIcon,
		badgeVariant: "destructive",
		description: "The work falls short of the practice; the severity says by how much.",
	},
};
