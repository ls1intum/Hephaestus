import type { TrendSupport } from "@/api/types.gen";
import type { TrendDirection } from "@/components/practice-vocabulary/practice-trend-defs";

/** The counted unit is a *piece of reviewed work* — `practice-feedback-language.md` names it. */
function reviewedWork(count: number): string {
	return `${count} ${count === 1 ? "piece" : "pieces"} of reviewed work`;
}

/**
 * Which trend a chip is showing. The server computes the two differently and the sentence has to
 * follow: a practice trend really does hold one bundle of opportunities against the one before it,
 * while a group trend pools the practices' finished comparisons — there is no group-level "latest
 * against previous" to describe.
 */
export type TrendScope = "practice" | "group";

/**
 * Says what the returned support actually establishes — never a comparison the server did not make.
 *
 * Three cases the earlier one-liner got wrong. `INSUFFICIENT_EVIDENCE` means no posterior was formed
 * at all, so describing a comparison there contradicted the chip's own "Not enough to compare yet".
 * A group trend never compares bundles. And the calendar span covers the visible evidence trail, not
 * the two bundles, so it is its own sentence rather than a clause hung on the comparison.
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
