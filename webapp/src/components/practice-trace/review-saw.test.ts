import { describe, expect, it } from "vitest";

import { reviewReach } from "./review-saw";

describe("recorded result totals", () => {
	it("counts sibling outcomes as results", () => {
		expect(
			reviewReach({
				status: "PARTIAL",
				results: { met: 2, notMet: 1, doesNotApply: 0, couldNotDetermine: 0 },
				notMetPractices: [],
			}),
		).toBe("two results met");
	});
});
