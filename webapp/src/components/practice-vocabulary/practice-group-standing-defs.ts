import {
	CircleAlertIcon,
	CircleCheckIcon,
	CircleDashedIcon,
	CircleMinusIcon,
	CircleSlashIcon,
} from "lucide-react";

import type { PracticeGroupStanding } from "@/api/types.gen";

import type { StatusDefs } from "./status-def";

export type PracticeGroupStandingValue = PracticeGroupStanding["standing"];

/**
 * Where a developer stands in a practice group, ordered worst first so a sorted list and a filter
 * read the same way.
 *
 * `shortLabel` exists for the ring legend, where five entries share one row and the badge's full
 * phrase does not fit. It says the same thing in fewer words and is never a different word — the
 * legend used to sit directly above a badge that disagreed with it.
 *
 * `NOT_OBSERVED` and `NO_OPPORTUNITY` are both silences and both render outline, so the icon is what
 * separates them: a dashed circle for "nothing seen yet", a slash for "nothing to see".
 */
export const PRACTICE_GROUP_STANDING_DEFS: StatusDefs<PracticeGroupStandingValue> = {
	DEVELOPING: {
		label: "Needs attention",
		icon: CircleAlertIcon,
		badgeVariant: "destructive",
		description: "Recent reviews raised problems in this group.",
	},
	MIXED: {
		label: "Mixed feedback",
		icon: CircleMinusIcon,
		badgeVariant: "warning",
		description: "Recent reviews found both strengths and problems here.",
	},
	STRENGTH: {
		label: "Going well",
		icon: CircleCheckIcon,
		badgeVariant: "success",
		description: "Nothing needs your attention here right now.",
	},
	NO_OPPORTUNITY: {
		label: "Nothing to report yet",
		icon: CircleSlashIcon,
		badgeVariant: "outline",
		description:
			"Your recent work was reviewed, but it either offered no opportunity for these practices or raised nothing worth mentioning.",
	},
	NOT_OBSERVED: {
		label: "Not observed yet",
		icon: CircleDashedIcon,
		badgeVariant: "outline",
		description: "These practices have not been observed in your reviewed work yet.",
	},
};

/** The legend's shorter wording, for the one row that cannot hold the full labels. */
export const PRACTICE_GROUP_STANDING_SHORT_LABELS: Record<PracticeGroupStandingValue, string> = {
	DEVELOPING: "Needs attention",
	MIXED: "Mixed",
	STRENGTH: "Going well",
	NO_OPPORTUNITY: "Nothing to report",
	NOT_OBSERVED: "Not observed",
};
