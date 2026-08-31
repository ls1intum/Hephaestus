import {
	CircleAlertIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	CircleMinusIcon,
	CircleSlashIcon,
} from "lucide-react";

import type { PracticeGroupStanding } from "@/api/types.gen";

import type { StatusDef } from "./status-def";

export type PracticeGroupStandingValue = PracticeGroupStanding["standing"];

/** `shortLabel` travels in the entry, so a value cannot gain one spelling and miss the other. */
export interface PracticeGroupStandingDef extends StatusDef {
	shortLabel: string;
}

/**
 * Where a developer stands in a practice group, worst first. Descriptions follow
 * `PracticeStandingDTO.Standing` on the server: `DEVELOPING` is problems *predominating*, and
 * `NO_OPPORTUNITY` covers evidence that settled nothing as well as work that offered no occasion.
 *
 * `shortLabel` is for the ring legend, where five entries share a row. Both silences render outline,
 * so the icon separates them: dashed for "nothing seen yet", a slash for "nothing to see".
 */
export const PRACTICE_GROUP_STANDING_DEFS: Record<
	PracticeGroupStandingValue,
	PracticeGroupStandingDef
> = {
	DEVELOPING: {
		shortLabel: "Needs attention",
		label: "Needs attention",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "Recent reviews here were mostly problems.",
	},
	MIXED: {
		shortLabel: "Mixed",
		label: "Mixed feedback",
		icon: CircleMinusIcon,
		badgeVariant: "warning",
		description: "Recent reviews found both strengths and problems here.",
	},
	STRENGTH: {
		shortLabel: "Going well",
		label: "Going well",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "Recent reviews here were almost entirely positive.",
	},
	NO_OPPORTUNITY: {
		shortLabel: "Nothing to report",
		label: "Nothing to report yet",
		icon: CircleSlashIcon,
		badgeVariant: "outline",
		description:
			"Your work was reviewed, but nothing here could be judged — either these practices did not apply to it, or the evidence did not settle the question.",
	},
	NOT_OBSERVED: {
		shortLabel: "Not observed",
		label: "Not observed yet",
		icon: CircleDashedIcon,
		badgeVariant: "outline",
		description: "No practice in this group has a current verdict for you.",
	},
};
