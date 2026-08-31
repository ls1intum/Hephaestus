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

/**
 * `shortLabel` travels inside the entry rather than beside it, so a value cannot gain one spelling
 * and miss the other — the same reason `EvidenceSourceDef` carries its `locator` here.
 */
export interface PracticeGroupStandingDef extends StatusDef {
	shortLabel: string;
}

/**
 * Where a developer stands in a practice group, ordered worst first so a sorted list and a filter
 * read the same way.
 *
 * The descriptions follow `PracticeStandingDTO.Standing` on the server rather than paraphrasing it.
 * Two of them used to overreach: `DEVELOPING` said "raised problems", which is equally true of
 * `MIXED` — what separates them is that the problems *predominate*. And `NO_OPPORTUNITY` named only
 * the missing occasion, dropping the case where evidence existed but settled nothing. That is
 * `INCONCLUSIVE`, and reporting it as "no opportunity" is precisely the collapse `observation-outcome`
 * refuses to make one screen further in.
 *
 * `shortLabel` exists for the ring legend, where five entries share one row and the badge's full
 * phrase does not fit. It says the same thing in fewer words and is never a different word — the
 * legend used to sit directly above a badge that disagreed with it.
 *
 * `NOT_OBSERVED` and `NO_OPPORTUNITY` are both silences and both render outline, so the icon is what
 * separates them: a dashed circle for "nothing seen yet", a slash for "nothing to see".
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
