import { afterEach, describe, expect, it, vi } from "vitest";
import {
	addMonths,
	budgetResetDayLabel,
	budgetUsedPercent,
	canStepForwardFrom,
	currentMonthUtc,
	formatDayLabel,
	formatMonthLabel,
	formatUsageDay,
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
		// Two different questions, and neither is the other's negation. "Is this the current month"
		// gates the cap editor and the "at today's rate" hint, which must not appear over a report that
		// cannot exist; "may the stepper move on" must stay false past this month too. A single
		// comparison serving both gets one of them wrong here, whichever way it is written.
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
		expect(currentMonthUtc()).toBe("2026-07");
		expect(isCurrentMonthUtc("2026-07")).toBe(true);
		expect(isCurrentMonthUtc("2026-08")).toBe(false);
	});
});

describe("addMonths", () => {
	it.each([
		["forward", "2026-07", 1, "2026-08"],
		["backward", "2026-07", -1, "2026-06"],
		["forward over a year end", "2026-12", 1, "2027-01"],
		["backward over a year start", "2026-01", -1, "2025-12"],
		["a whole year at a time", "2026-03", -12, "2025-03"],
	])("steps %s", (_name, month, delta, expected) => {
		expect(addMonths(month, delta)).toBe(expected);
	});

	it("lands on a month that has no 31st without rolling into the next one", () => {
		// The arithmetic runs on day 1 of each month for exactly this reason: stepping from a 31-day
		// month with the day left in place puts January 31 into March.
		expect(addMonths("2026-01", 1)).toBe("2026-02");
	});
});

describe("month and day labels", () => {
	it("names a month by the month it is in UTC, whatever the viewer's offset", () => {
		// The label is built from `Date.UTC(...)` and rendered with `timeZone: "UTC"`; drop either and
		// a reader west of Greenwich reads "June 2026" over a July report.
		expect(formatMonthLabel("2026-07")).toContain("2026");
		expect(formatMonthLabel("2026-07")).not.toContain("June");
		expect(formatMonthLabel("2026-01")).not.toContain("December");
	});

	it("labels a day by its UTC date, not the viewer's", () => {
		// Midnight UTC is the previous evening in the Americas; the buckets are the server's UTC days.
		expect(formatUsageDay(new Date("2026-07-05T00:00:00.000Z"))).toBe("Jul 5");
		expect(formatDayLabel(new Date("2026-07-05T00:00:00.000Z"))).toBe("July 5");
	});

	it.each([
		["a day the server could not parse", "not a date"],
		["a missing day", undefined],
	])("shows a dash rather than 'Invalid Date' for %s", (_name, value) => {
		// A single malformed row must cost one cell, not the whole breakdown table.
		expect(formatUsageDay(value as unknown as Date)).toBe("–");
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
