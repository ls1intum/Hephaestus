import { describe, expect, it } from "vitest";
import { budgetResetDayLabel, budgetUsedPercent, projectBudget } from "./usageUtils";

describe("budgetUsedPercent", () => {
	it("has no percentage to report without a cap", () => {
		expect(budgetUsedPercent(4.25, undefined)).toBeUndefined();
	});

	it("reads a $0 cap as fully used rather than dividing by zero", () => {
		expect(budgetUsedPercent(0, 0)).toBe(100);
	});

	it("reports the share of the cap consumed", () => {
		expect(budgetUsedPercent(8.4, 10)).toBeCloseTo(84);
	});
});

describe("budgetResetDayLabel", () => {
	it("names the first of the following month", () => {
		expect(budgetResetDayLabel("2026-07")).toBe("August 1");
	});

	it("rolls over the year", () => {
		expect(budgetResetDayLabel("2026-12")).toBe("January 1");
	});
});

describe("projectBudget", () => {
	const july = "2026-07";

	it("projects the day the cap is reached from the month's average pace", () => {
		// $8.40 over 10 days = $0.84/day; $10 / $0.84 = 11.9 -> the 12th.
		const projection = projectBudget(8.4, 10, july, new Date("2026-07-10T12:00:00.000Z"));
		expect(projection?.projectedMonthEndUsd).toBeCloseTo(26.04);
		expect(projection?.reachedOn?.toISOString().slice(0, 10)).toBe("2026-07-12");
	});

	it("reports a month-end total instead when the pace never reaches the cap", () => {
		const projection = projectBudget(2, 10, july, new Date("2026-07-10T12:00:00.000Z"));
		expect(projection?.projectedMonthEndUsd).toBeCloseTo(6.2);
		expect(projection?.reachedOn).toBeNull();
	});

	it("never projects a day already past", () => {
		// The cap was passed on paper days ago; the honest answer is "today", not a date in the past.
		const projection = projectBudget(30, 10, july, new Date("2026-07-20T12:00:00.000Z"));
		expect(projection?.reachedOn?.toISOString().slice(0, 10)).toBe("2026-07-20");
	});

	it("clamps a slow pace to the last day of the month", () => {
		// 31-day month: $1/day against a $31 cap lands exactly on the 31st, never the 32nd.
		const projection = projectBudget(15, 31, july, new Date("2026-07-15T12:00:00.000Z"));
		expect(projection?.reachedOn?.toISOString().slice(0, 10)).toBe("2026-07-31");
	});

	it("says nothing in the first two days of the month", () => {
		expect(projectBudget(8.4, 10, july, new Date("2026-07-02T12:00:00.000Z"))).toBeNull();
	});

	it("says nothing when there is no spend to extrapolate from", () => {
		expect(projectBudget(0, 10, july, new Date("2026-07-10T12:00:00.000Z"))).toBeNull();
	});

	it("says nothing without a positive cap to reach", () => {
		expect(projectBudget(8.4, undefined, july, new Date("2026-07-10T12:00:00.000Z"))).toBeNull();
		expect(projectBudget(8.4, 0, july, new Date("2026-07-10T12:00:00.000Z"))).toBeNull();
	});

	it("says nothing about a month that is not the one in progress", () => {
		expect(projectBudget(8.4, 10, "2026-06", new Date("2026-07-10T12:00:00.000Z"))).toBeNull();
	});
});
