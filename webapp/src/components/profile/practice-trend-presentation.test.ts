import { describe, expect, it } from "vitest";
import type { TrendSupport } from "@/api/types.gen";
import {
	formatTrendCoverage,
	formatTrendGap,
	formatTrendProvenance,
} from "./practice-trend-presentation";

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
	it("formats a complete comparison from support", () => {
		expect(formatTrendProvenance(support({ calendarSpanDays: 9 }))).toBe(
			"Compared your latest 4 reviewed work items with the 4 before them, spanning 9 days.",
		);
	});

	it("handles singular provenance and a zero-evidence state", () => {
		expect(
			formatTrendProvenance(
				support({ currentOpportunities: 1, previousOpportunities: 0, calendarSpanDays: 1 }),
			),
		).toBe("Based on 1 reviewed work item, spanning 1 day.");
		expect(
			formatTrendProvenance(
				support({ currentOpportunities: 0, previousOpportunities: 0, calendarSpanDays: undefined }),
			),
		).toBe("No reviewed work items are available yet.");
	});

	it("formats singular, plural, and zero comparison gaps", () => {
		expect(formatTrendGap(support({ opportunitiesUntilComparable: 1 }))).toBe(
			"1 more reviewed work item will make a comparison possible.",
		);
		expect(formatTrendGap(support({ opportunitiesUntilComparable: 2 }))).toBe(
			"2 more reviewed work items will make a comparison possible.",
		);
		expect(formatTrendGap(support())).toBe("A comparison is available.");
	});

	it("formats area coverage and omits it for a practice", () => {
		expect(formatTrendCoverage(support({ comparablePractices: 3, eligiblePractices: 5 }))).toBe(
			"3 of 5 practices in this area had comparable evidence.",
		);
		expect(formatTrendCoverage(support({ comparablePractices: 1, eligiblePractices: 1 }))).toBe(
			"1 of 1 practice in this area had comparable evidence.",
		);
		expect(formatTrendCoverage(support())).toBeUndefined();
	});
});
