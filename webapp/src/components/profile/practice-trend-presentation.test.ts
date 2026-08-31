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
	it("formats a complete comparison from support", () => {
		expect(formatTrendProvenance(support({ calendarSpanDays: 9 }))).toBe(
			"Compared your latest 4 pieces of reviewed work with the 4 before them, spanning 9 days.",
		);
	});

	it("handles singular provenance and a zero-evidence state", () => {
		expect(
			formatTrendProvenance(
				support({ currentOpportunities: 1, previousOpportunities: 0, calendarSpanDays: 1 }),
			),
		).toBe("Based on 1 piece of reviewed work, spanning 1 day.");
		expect(
			formatTrendProvenance(
				support({ currentOpportunities: 0, previousOpportunities: 0, calendarSpanDays: undefined }),
			),
		).toBe("No reviewed work is available yet.");
	});
});
