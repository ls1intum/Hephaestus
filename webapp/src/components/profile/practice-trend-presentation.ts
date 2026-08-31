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

/** The counted unit is a *piece of reviewed work* — `practice-feedback-language.md` names it. */
function reviewedWork(count: number): string {
	return `${count} ${count === 1 ? "piece" : "pieces"} of reviewed work`;
}

/** Formats visible provenance from the returned evidence support, never from an assumed time bin. */
export function formatTrendProvenance(support: TrendSupport): string {
	const current = support.currentOpportunities;
	const previous = support.previousOpportunities;
	const total = current + previous;
	if (total === 0) return "No reviewed work is available yet.";

	const span = support.calendarSpanDays;
	const spanText = span ? `, spanning ${span} ${span === 1 ? "day" : "days"}` : "";
	if (previous === 0) {
		return `Based on ${reviewedWork(current)}${spanText}.`;
	}
	return `Compared your latest ${reviewedWork(current)} with the ${previous} before ${
		previous === 1 ? "it" : "them"
	}${spanText}.`;
}
