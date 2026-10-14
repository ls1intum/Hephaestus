import { describe, expect, it } from "vitest";

import { lineageRecords } from "./lineage-chain";
import { lineageStateCase, STATE_RESULT_ID } from "./lineage-state-fixtures";
import { ALL_STEPS, filteredLineage, parseTraceFilter, tracePractices } from "./trace-filter";

describe("filteredLineage", () => {
	it("leaves the graph alone without a filter", () => {
		const { lineage } = lineageStateCase("delivery-states");
		expect(filteredLineage(lineage, ALL_STEPS)).toBe(lineage);
	});

	it("keeps only the lost feedback and the results behind it, with their problems", () => {
		const { lineage } = lineageStateCase("delivery-states");
		const records = lineageRecords(filteredLineage(lineage, { kind: "losses" }));
		expect(records.feedback.map((entry) => entry.state).sort()).toStrictEqual([
			"FAILED",
			"REJECTED",
			"WITHHELD",
		]);
		expect(records.results.map((entry) => entry.id)).toStrictEqual([STATE_RESULT_ID]);
		expect(records.results[0]?.problems).toHaveLength(2);
		expect(records.item).toBeDefined();
		expect(records.reviews).toHaveLength(1);
	});

	it("keeps a not-met result that led to no feedback among the losses", () => {
		const { lineage } = lineageStateCase("not-met-silent");
		const records = lineageRecords(filteredLineage(lineage, { kind: "losses" }));
		expect(records.results.map((entry) => entry.id)).toStrictEqual([STATE_RESULT_ID]);
	});

	it("keeps one practice's results and the feedback written from them", () => {
		const { lineage } = lineageStateCase("complete");
		const filter = parseTraceFilter("practice:thin-controllers");
		const records = lineageRecords(filteredLineage(lineage, filter));
		expect(records.results.map((entry) => entry.practiceSlug)).toStrictEqual(["thin-controllers"]);
		expect(records.feedback).toStrictEqual([]);
	});

	it("keeps every piece of feedback and no result", () => {
		const { lineage } = lineageStateCase("complete");
		const records = lineageRecords(filteredLineage(lineage, { kind: "feedback" }));
		expect(records.results).toStrictEqual([]);
		expect(records.feedback).toHaveLength(1);
	});
});

describe("tracePractices", () => {
	it("lists the practices the results answered, by name", () => {
		const { lineage } = lineageStateCase("complete");
		expect(tracePractices(lineage).map((entry) => entry.slug)).toHaveLength(3);
	});
});
