import { describe, expect, it } from "vitest";
import { isRecord } from "./is-record";

describe("isRecord", () => {
	// An array is an object, and the domain fields a caller reads off one are all `undefined`, so
	// admitting it would let a decoded JSON array through as a record with every field missing.
	it("turns away an array, which would otherwise pass as a record with every field missing", () => {
		expect(isRecord([{ scope: "job", connectionId: 42 }])).toBe(false);
	});

	// `typeof null === "object"`, so the null check is the guard, not a formality: drop it and every
	// caller's first property read throws instead of taking the "not a record" branch.
	it("turns away null, which `typeof` alone calls an object", () => {
		expect(isRecord(null)).toBe(false);
	});

	it("accepts a plain object, which is the shape it exists to admit", () => {
		expect(isRecord({ scope: "job", connectionId: 42 })).toBe(true);
	});
});
