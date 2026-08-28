import { describe, expect, it } from "vitest";

import { isRecord } from "./is-record";

describe("isRecord", () => {
	it("turns away an array, which would otherwise pass as a record with every field missing", () => {
		expect(isRecord([{ scope: "job", connectionId: 42 }])).toBe(false);
	});

	it("turns away null, which `typeof` alone calls an object", () => {
		expect(isRecord(null)).toBe(false);
	});

	it("accepts a plain object, which is the shape it exists to admit", () => {
		expect(isRecord({ scope: "job", connectionId: 42 })).toBe(true);
	});
});
