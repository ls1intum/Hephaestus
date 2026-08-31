import type { PracticeGroupStanding } from "@/api/types.gen";

export type PracticeGroupStandingKey = PracticeGroupStanding["standing"];

/**
 * The one place a standing gets words and a colour. The badge, the ring legend and the practice
 * nodes on the detail page all read from here — they used to carry three spellings, and the legend
 * sat directly above a badge that disagreed with it.
 *
 * `shortLabel` is for the ring legend, where five entries share one row and the badge's full phrase
 * does not fit; it says the same thing in fewer words and is never a different word.
 */
export const PRACTICE_GROUP_STANDING_BADGE: Record<
	PracticeGroupStandingKey,
	{
		label: string;
		shortLabel: string;
		variant: "destructive" | "warning" | "success" | "outline";
		explanation: string;
	}
> = {
	DEVELOPING: {
		label: "Needs attention",
		shortLabel: "Needs attention",
		variant: "destructive",
		explanation: "Recent reviews raised problems in this group.",
	},
	MIXED: {
		label: "Mixed feedback",
		shortLabel: "Mixed",
		variant: "warning",
		explanation: "Recent reviews found both strengths and problems here.",
	},
	STRENGTH: {
		label: "Going well",
		shortLabel: "Going well",
		variant: "success",
		explanation: "Nothing needs your attention here right now.",
	},
	NOT_OBSERVED: {
		label: "Not observed yet",
		shortLabel: "Not observed",
		variant: "outline",
		explanation: "These practices have not been observed in your reviewed work yet.",
	},
	NO_OPPORTUNITY: {
		label: "Nothing to report yet",
		shortLabel: "Nothing to report",
		variant: "outline",
		explanation:
			"Your recent work was reviewed, but it either offered no opportunity for these practices or raised nothing worth mentioning.",
	},
};
