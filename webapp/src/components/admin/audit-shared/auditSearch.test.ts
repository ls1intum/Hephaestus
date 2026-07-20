import { describe, expect, it } from "vitest";
import {
	auditSearchSchema,
	dayEndIso,
	dayStartIso,
	fromDayParam,
	narrowToEnum,
	toDateRange,
} from "./auditSearch";

describe("day bounds", () => {
	it("sends the next midnight as the exclusive upper bound", () => {
		// Not end-of-day: formatISO drops fractional seconds, so 23:59:59 against the server's
		// `occurred_at < :to` silently discards the range's final second.
		expect(String(dayEndIso(new Date(2026, 6, 15)))).toMatch(/^2026-07-16T00:00:00/);
	});

	it("sends midnight of the picked day as the inclusive lower bound", () => {
		expect(String(dayStartIso(new Date(2026, 6, 15)))).toMatch(/^2026-07-15T00:00:00/);
	});

	it("carries the local offset so a shared link means the same day to everyone", () => {
		expect(String(dayStartIso(new Date(2026, 6, 15)))).toMatch(/([+-]\d{2}:\d{2}|Z)$/);
	});
});

describe("fromDayParam", () => {
	it.each(["2026-02-31", "2026-13-01", "not-a-date", "2026"])("rejects %s", (value) => {
		expect(fromDayParam(value)).toBeUndefined();
	});

	it("parses a well-formed day", () => {
		expect(fromDayParam("2026-07-15")?.getDate()).toBe(15);
	});
});

describe("toDateRange", () => {
	it("keeps an open-ended range", () => {
		expect(toDateRange({ from: "2026-07-01", to: undefined })?.to).toBeUndefined();
	});

	it("is undefined when neither bound is usable", () => {
		expect(toDateRange({ from: undefined, to: undefined })).toBeUndefined();
	});
});

describe("narrowToEnum", () => {
	const allowed = ["CREATED", "UPDATED"] as const;

	it("drops values the API would reject", () => {
		expect(narrowToEnum(["CREATED", "RETIRED"], allowed)).toEqual(["CREATED"]);
	});

	it("returns undefined when every value is unknown, so the query is unfiltered rather than empty", () => {
		expect(narrowToEnum(["RETIRED"], allowed)).toBeUndefined();
	});

	it("treats an empty selection as no selection", () => {
		expect(narrowToEnum([], allowed)).toBeUndefined();
	});
});

describe("auditSearchSchema", () => {
	it("opens the log rather than erroring on a hand-typed or stale link", () => {
		// The page exists to be linked to; validateSearch throwing renders an error screen instead.
		expect(auditSearchSchema.parse({ tab: "nope", accountId: "abc", from: 5 })).toEqual({
			tab: "signins",
			accountId: undefined,
			from: undefined,
		});
	});

	it("accepts a single value where the API accepts repeated ones", () => {
		// The server takes `?action=CREATED&action=DELETED`, so a hand-written `?action=CREATED` is the
		// natural form to try; discarding it would filter nothing while showing no active filter.
		expect(auditSearchSchema.parse({ action: "CREATED" }).action).toEqual(["CREATED"]);
	});
});
