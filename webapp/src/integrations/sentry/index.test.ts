import { describe, expect, it } from "vitest";

import { DATA_COLLECTION } from "./index";

/** Every value in the collection map, however deeply Sentry nests its categories. */
function leaves(value: unknown): unknown[] {
	return typeof value === "object" && value !== null && !Array.isArray(value)
		? Object.values(value).flatMap(leaves)
		: [value];
}

// The type on `DATA_COLLECTION` forces a decision about each category Sentry defines. This forces
// that decision to be "off" — including inside `httpHeaders`, `graphQL` and `genAI`, where a
// widening is a nested field nobody reads twice.
describe("the data an error report may carry", () => {
	it("turns no category on", () => {
		expect(leaves(DATA_COLLECTION).filter((leaf) => leaf === true)).toStrictEqual([]);
		expect(DATA_COLLECTION.httpBodies).toStrictEqual([]);
	});
});
