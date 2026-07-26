import { describe, expect, it } from "vitest";
import { asDate } from "./dates";

describe("asDate", () => {
	it("parses the ISO string the SDK actually returns for a date field", () => {
		// The generated client types these as `Date` but ships them as strings at runtime.
		expect(asDate("2026-07-24T10:30:00.000Z")?.toISOString()).toBe("2026-07-24T10:30:00.000Z");
	});

	it("passes a real Date through untouched", () => {
		const date = new Date("2026-07-24T10:30:00.000Z");
		expect(asDate(date)).toBe(date);
	});

	it.each([
		["null", null],
		["undefined", undefined],
		["an empty string", ""],
		["a non-date string", "not a date"],
		["an Invalid Date", new Date("nonsense")],
	])("degrades %s to undefined rather than to a fabricated now", (_name, value) => {
		expect(asDate(value)).toBeUndefined();
	});
});
