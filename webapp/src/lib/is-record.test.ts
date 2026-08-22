import { describe, expect, it } from "vitest";
import { isRecord } from "./is-record";

describe("isRecord", () => {
	// An array is an object whose every named key reads as `undefined`, so admitting one would let a
	// decoded JSON array through as a record with every field missing instead of turning it away.
	it("turns away an array, which would otherwise pass as a record with every field missing", () => {
		expect(isRecord([{ scope: "job", connectionId: 42 }])).toBe(false);
	});
});
