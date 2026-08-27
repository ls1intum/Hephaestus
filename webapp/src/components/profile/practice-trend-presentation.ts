import {
	CircleDashedIcon,
	CircleHelpIcon,
	type LucideIcon,
	TrendingDownIcon,
	TrendingUpIcon,
} from "lucide-react";
import type { PracticeTrend, TrendSupport } from "@/api/types.gen";

export type TrendDirection = PracticeTrend["direction"];
export type TrendTone = "positive" | "negative" | "neutral" | "muted";

interface TrendPresentation {
	label: string;
	Icon: LucideIcon;
	tone: TrendTone;
}

/** Exhaustive developer-facing vocabulary for the opportunity-indexed direction. */
export const PRACTICE_TREND_PRESENTATION = {
	IMPROVING: {
		label: "More positive recently",
		Icon: TrendingUpIcon,
		tone: "positive",
	},
	DECLINING: {
		label: "More difficulties recently",
		Icon: TrendingDownIcon,
		tone: "negative",
	},
	UNCERTAIN: {
		label: "Direction unclear",
		Icon: CircleHelpIcon,
		tone: "neutral",
	},
	INSUFFICIENT_EVIDENCE: {
		label: "Not enough to compare yet",
		Icon: CircleDashedIcon,
		tone: "muted",
	},
} as const satisfies Record<TrendDirection, TrendPresentation>;

function reviewedItems(count: number): string {
	return `${count} reviewed ${count === 1 ? "work item" : "work items"}`;
}

/** Formats visible provenance from the returned evidence support, never from an assumed time bin. */
export function formatTrendProvenance(support: TrendSupport): string {
	const current = support.currentOpportunities;
	const previous = support.previousOpportunities;
	const total = current + previous;
	if (total === 0) return "No reviewed work items are available yet.";

	const span = support.calendarSpanDays;
	const spanText = span ? `, spanning ${span} ${span === 1 ? "day" : "days"}` : "";
	if (previous === 0) {
		return `Based on ${reviewedItems(current)}${spanText}.`;
	}
	return `Compared your latest ${reviewedItems(current)} with the ${previous} before ${
		previous === 1 ? "it" : "them"
	}${spanText}.`;
}

/** Formats the unmet evidence precondition as a concrete next milestone. */
export function formatTrendGap(support: TrendSupport): string {
	const missing = support.opportunitiesUntilComparable;
	if (missing === 0) return "A comparison is available.";
	return `${missing} more reviewed ${missing === 1 ? "work item" : "work items"} will make a comparison possible.`;
}

/** Formats group coverage; practice-scoped support has no coverage sentence. */
export function formatTrendCoverage(support: TrendSupport): string | undefined {
	const comparable = support.comparablePractices;
	const eligible = support.eligiblePractices;
	if (comparable === undefined || eligible === undefined) return undefined;
	if (eligible === 0) return "No practices are eligible for comparison in this group.";
	return `${comparable} of ${eligible} ${eligible === 1 ? "practice" : "practices"} in this group had comparable evidence.`;
}
