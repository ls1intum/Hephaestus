import { describe, expect, it } from "vitest";

import { lineageRecords } from "./lineage-chain";
import { lineageStateCase, richReviewFixture, STATE_RESULT_ID } from "./lineage-state-fixtures";
import {
	ALL_STEPS,
	filteredLineage,
	parseTraceFilter,
	shownRecords,
	traceFilterValue,
	traceOutcomes,
	tracePractices,
} from "./trace-filter";

describe("filteredLineage", () => {
	it("leaves the graph alone without a filter", () => {
		const { lineage } = lineageStateCase("delivery-states");
		expect(filteredLineage(lineage, ALL_STEPS)).toBe(lineage);
	});

	it("keeps only the lost feedback and the results behind it, with their problems", () => {
		const { lineage } = lineageStateCase("delivery-states");
		const records = lineageRecords(filteredLineage(lineage, { kind: "losses" }));
		expect(
			records.feedback.map((entry) => entry.state).sort((left, right) => left.localeCompare(right)),
		).toStrictEqual(["FAILED", "REJECTED", "WITHHELD"]);
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

	it("keeps one outcome's results with their problems, and the feedback written from them", () => {
		const { lineage } = lineageStateCase("complete");
		const whole = lineageRecords(lineage);
		const commentId = "5e5e0002-0000-4000-8000-000000000001";
		const comment = whole.feedback.find((entry) => entry.id === commentId);
		const source = whole.results.find((result) => result.id === STATE_RESULT_ID);
		expect(comment?.basis).toContain(STATE_RESULT_ID);
		expect(source?.outcome).toBe("NOT_MET");
		const filter = parseTraceFilter(traceFilterValue({ kind: "outcome", outcome: "NOT_MET" }));
		expect(filter).toStrictEqual({ kind: "outcome", outcome: "NOT_MET" });
		const same = whole.results.filter((result) => result.outcome === "NOT_MET");
		const records = lineageRecords(filteredLineage(lineage, filter));
		expect(records.results.map((result) => result.id)).toStrictEqual(same.map(({ id }) => id));
		expect(records.results.flatMap((result) => result.problems)).toStrictEqual(
			same.flatMap((result) => result.problems),
		);
		expect(records.feedback.map((entry) => entry.id)).toStrictEqual([commentId]);
		expect(records.reviews).toHaveLength(1);
	});

	it("reads an outcome it does not know as every step", () => {
		expect(parseTraceFilter("outcome:PASSED")).toBe(ALL_STEPS);
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

describe("traceOutcomes", () => {
	it("lists every outcome the results have, with its count, Not met first", () => {
		const { lineage } = lineageStateCase("outcomes");
		const outcomes = traceOutcomes(lineage);
		expect(outcomes.map(({ outcome }) => outcome)).toStrictEqual([
			"NOT_MET",
			"MET",
			"COULD_NOT_DETERMINE",
			"DOES_NOT_APPLY",
		]);
		expect(outcomes.reduce((sum, { count }) => sum + count, 0)).toBe(
			lineageRecords(lineage).results.length,
		);
	});
});

describe("shownRecords", () => {
	it("keeps every result and problem a shown piece of feedback rests on, hidden ones too", () => {
		const { lineage } = richReviewFixture;
		const whole = lineageRecords(lineage);
		const outcomeOf = new Map(whole.results.map((result) => [result.id, result.outcome]));
		// This fixture's final piece rests on a Not met and a Met result; the filter hides one.
		const acrossId = "5e5e0006-0000-4000-8000-000000000006";
		const across = whole.feedback.find((entry) => entry.id === acrossId);
		expect(across?.basis.map((id) => outcomeOf.get(id))).toStrictEqual(["NOT_MET", "MET"]);
		const filter = { kind: "outcome", outcome: "MET" } as const;
		const shown = shownRecords(whole, filteredLineage(lineage, filter));
		expect(shown.results.every((result) => result.outcome === "MET")).toBe(true);
		const kept = shown.feedback.find((entry) => entry.id === acrossId);
		expect(kept).toBeDefined();
		expect(kept?.basis).toStrictEqual(across?.basis);
		expect(kept?.problemBasis).toStrictEqual(across?.problemBasis);
	});

	it("shows the whole chain's records when nothing is filtered", () => {
		const { lineage } = richReviewFixture;
		const whole = lineageRecords(lineage);
		expect(shownRecords(whole, filteredLineage(lineage, ALL_STEPS))).toStrictEqual(whole);
	});
});
