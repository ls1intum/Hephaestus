import { describe, expect, it } from "vitest";
import { asDate, asWire, type Wire } from "./dates";

describe("asDate", () => {
	// The instant, not merely the type: `toBeInstanceOf(Date)` is satisfied by `new Date(0)`.
	it("parses the ISO string the SDK actually returns for a date field", () => {
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

/**
 * `Wire` earns its keep only if it *rejects* the shape it exists to prevent, so the assertions here
 * are the `@ts-expect-error`s: each one fails the typecheck the moment `Wire` stops catching a
 * `Date`, which is the regression that would let a fixture type-check and then hand a component a
 * value production never sends. The runtime `expect`s only keep the file honest as a test.
 */
describe("Wire", () => {
	type View = { id: string; createdAt: Date; nested: { at: Date }[] };

	it("accepts the wire shape, typed as the generated view", () => {
		const view = asWire<View>({
			id: "a",
			createdAt: "2026-07-24T10:30:00.000Z",
			nested: [{ at: "2026-07-24T10:30:00.000Z" }],
		});

		// Not `toBeInstanceOf(Date)`: the value really is a string, which is the point.
		expect(asDate(view.createdAt)?.toISOString()).toBe("2026-07-24T10:30:00.000Z");
	});

	it("rejects a Date at the top level", () => {
		// @ts-expect-error a real Date is exactly what `Wire` exists to keep out of a fixture
		const view = asWire<View>({ id: "a", createdAt: new Date(0), nested: [] });

		expect(view.id).toBe("a");
	});

	it("rejects a Date nested inside an array, where the recursion could quietly stop", () => {
		// @ts-expect-error `Wire` must recurse through arrays, not just top-level members
		const view = asWire<View>({ id: "a", createdAt: "x", nested: [{ at: new Date(0) }] });

		expect(view.id).toBe("a");
	});

	it("leaves a non-Date member alone", () => {
		const id: Wire<View>["id"] = "a";

		expect(id).toBe("a");
	});
});
