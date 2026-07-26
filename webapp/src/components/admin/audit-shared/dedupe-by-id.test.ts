import { describe, expect, it } from "vitest";
import { dedupeById } from "./dedupeById";

describe("dedupeById", () => {
	it("drops a row an earlier page already served, keeping order", () => {
		expect(dedupeById([{ id: 3 }, { id: 2 }, { id: 3 }, { id: 1 }])).toEqual([
			{ id: 3 },
			{ id: 2 },
			{ id: 1 },
		]);
	});

	it("keeps every row that carries no id rather than collapsing them into one", () => {
		expect(dedupeById([{ id: undefined }, { id: undefined }])).toHaveLength(2);
	});
});
