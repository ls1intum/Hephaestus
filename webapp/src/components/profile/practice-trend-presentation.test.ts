import { describe, expect, it } from "vitest";
import type { TrendSupport } from "@/api/types.gen";
import { formatTrendProvenance } from "./practice-trend-presentation";

const support = (overrides: Partial<TrendSupport> = {}): TrendSupport => ({
	currentOpportunities: 4,
	previousOpportunities: 4,
	opportunitiesUntilComparable: 0,
	bundleSize: 4,
	ropeHalfWidth: 0.15,
	credibilityThreshold: 0.9,
	...overrides,
});

describe("practice trend copy", () => {
	it("describes a practice comparison the server actually made", () => {
		expect(formatTrendProvenance(support({ calendarSpanDays: 9 }), "IMPROVING", "practice")).toBe(
			"Compared your latest 4 pieces of reviewed work with the 4 before them. Evidence spans 9 days.",
		);
	});

	it("handles singular provenance and a zero-evidence state", () => {
		expect(
			formatTrendProvenance(
				support({ currentOpportunities: 1, previousOpportunities: 0, calendarSpanDays: 1 }),
				"IMPROVING",
				"practice",
			),
		).toBe("Based on 1 piece of reviewed work. Evidence spans 1 day.");
		expect(
			formatTrendProvenance(
				support({ currentOpportunities: 0, previousOpportunities: 0 }),
				"INSUFFICIENT_EVIDENCE",
				"practice",
			),
		).toBe("No reviewed work is available yet.");
	});

	it("claims no comparison when the server formed none", () => {
		// The chip reads "Not enough to compare yet". Its own tooltip used to answer "Compared your
		// latest 4 with the 3 before them" — a comparison PracticeTrendCalculator skips outright while
		// `opportunitiesUntilComparable` is above zero.
		const sentence = formatTrendProvenance(
			support({ previousOpportunities: 3, opportunitiesUntilComparable: 1 }),
			"INSUFFICIENT_EVIDENCE",
			"practice",
		);

		expect(sentence).not.toContain("Compared");
		expect(sentence).toBe(
			"Based on 7 pieces of reviewed work. 1 more with something to judge is needed before a direction can be shown.",
		);
	});

	it("does not describe a group trend as a comparison of bundles", () => {
		// A group trend pools the practices' finished comparisons; there is no group-level "latest
		// against previous" to report, and its opportunity counts are the union across practices.
		const sentence = formatTrendProvenance(
			support({
				currentOpportunities: 7,
				previousOpportunities: 5,
				comparablePractices: 3,
				eligiblePractices: 5,
			}),
			"IMPROVING",
			"group",
		);

		expect(sentence).not.toContain("Compared");
		expect(sentence).toBe(
			"Across 12 pieces of reviewed work in this group. 3 of 5 practices here had enough evidence to compare.",
		);
	});
});
