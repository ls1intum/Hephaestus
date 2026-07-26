import { afterEach, describe, expect, it, vi } from "vitest";
import {
	budgetResetDayLabel,
	budgetUsedPercent,
	canStepForwardFrom,
	isCurrentMonthUtc,
	projectBudget,
} from "./usage-utils";

afterEach(() => {
	vi.useRealTimers();
});

describe("which month is now", () => {
	/** Any instant inside the month; only the yyyy-MM part is ever read. */
	function nowAt(iso: string) {
		vi.useFakeTimers();
		vi.setSystemTime(new Date(iso));
	}

	it("answers both questions no for a month later than this one", () => {
		// The two used to be one comparison — `month >= now` for "is this the current month", negated
		// for "may the stepper move on". A later month then restored the cap editor and the "at today's
		// rate" hint over a report that cannot exist, and, once the two were separated, `!isCurrentMonth`
		// would have let the stepper walk further into the future. Neither is the other's negation.
		nowAt("2026-07-15T00:00:00.000Z");
		expect(isCurrentMonthUtc("2026-08")).toBe(false);
		expect(canStepForwardFrom("2026-08")).toBe(false);
	});

	it("is the current month only on the month itself", () => {
		nowAt("2026-07-15T00:00:00.000Z");
		expect(isCurrentMonthUtc("2026-07")).toBe(true);
		expect(isCurrentMonthUtc("2026-06")).toBe(false);
	});

	it("steps forward from any month before this one, across a year boundary", () => {
		nowAt("2026-01-01T00:00:00.000Z");
		expect(canStepForwardFrom("2025-12")).toBe(true);
		expect(canStepForwardFrom("2026-01")).toBe(false);
	});

	it("reads the month in UTC, not the viewer's timezone", () => {
		// 23:30 on July 31 in UTC is already August 1 east of Greenwich; the server buckets in UTC and
		// so does this, or a reader an hour ahead would be told their live month had closed.
		nowAt("2026-07-31T23:30:00.000Z");
		expect(isCurrentMonthUtc("2026-07")).toBe(true);
		expect(isCurrentMonthUtc("2026-08")).toBe(false);
	});
});

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
	it.each([
		["2026-07", "August 1"],
		["2026-12", "January 1"],
	])("names the first day after %s", (month, label) => {
		expect(budgetResetDayLabel(month)).toBe(label);
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

	it.each<[string, number, number | undefined, string, string]>([
		["the month is too young to extrapolate from", 8.4, 10, july, "2026-07-02T12:00:00.000Z"],
		["there is no spend to extrapolate from", 0, 10, july, "2026-07-10T12:00:00.000Z"],
		["there is no cap to reach", 8.4, undefined, july, "2026-07-10T12:00:00.000Z"],
		["the cap is $0, which pauses rather than projects", 8.4, 0, july, "2026-07-10T12:00:00.000Z"],
		["the month is not the one in progress", 8.4, 10, "2026-06", "2026-07-10T12:00:00.000Z"],
	])("says nothing when %s", (_name, spend, cap, month, now) => {
		expect(projectBudget(spend, cap, month, new Date(now))).toBeNull();
	});
});
