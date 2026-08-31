import type { TrendSupport } from "@/api/types.gen";
import type { TrendDirection } from "@/components/practice-vocabulary/practice-trend-defs";

/** The counted unit is a *piece of reviewed work* — `practice-feedback-language.md` names it. */
function reviewedWork(count: number): string {
	return `${count} ${count === 1 ? "piece" : "pieces"} of reviewed work`;
}

/** A practice trend compares two bundles; a group trend pools finished comparisons. */
export type TrendScope = "practice" | "group";

/**
 * Says what the support establishes, never a comparison the server did not make: none is formed for
 * `INSUFFICIENT_EVIDENCE`, and a group trend compares no bundles at all. The calendar span covers
 * the visible evidence trail rather than the two bundles, so it stands as its own sentence.
 */
export function formatTrendProvenance(
	support: TrendSupport,
	direction: TrendDirection,
	scope: TrendScope,
): string {
	const current = support.currentOpportunities;
	const previous = support.previousOpportunities;
	if (current + previous === 0) return "No reviewed work is available yet.";

	const span = support.calendarSpanDays;
	const spanSentence = span ? ` Evidence spans ${span} ${span === 1 ? "day" : "days"}.` : "";

	if (direction === "INSUFFICIENT_EVIDENCE") {
		const missing = support.opportunitiesUntilComparable;
		const needed =
			missing > 0
				? ` ${missing} more with something to judge ${missing === 1 ? "is" : "are"} needed before a direction can be shown.`
				: "";
		return `Based on ${reviewedWork(current + previous)}.${needed}${spanSentence}`;
	}

	if (scope === "group") {
		const comparable = support.comparablePractices;
		const eligible = support.eligiblePractices;
		const coverage =
			comparable !== undefined && eligible !== undefined
				? ` ${comparable} of ${eligible} ${eligible === 1 ? "practice" : "practices"} here had enough evidence to compare.`
				: "";
		return `Across ${reviewedWork(current + previous)} in this group.${coverage}${spanSentence}`;
	}

	if (previous === 0) {
		return `Based on ${reviewedWork(current)}.${spanSentence}`;
	}
	return `Compared your latest ${reviewedWork(current)} with the ${previous} before ${
		previous === 1 ? "it" : "them"
	}.${spanSentence}`;
}
