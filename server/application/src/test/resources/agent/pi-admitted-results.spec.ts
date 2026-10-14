import assert from "node:assert/strict";
import { test } from "node:test";
import {
	admittedResultSchema,
	leanResults,
} from "../../../main/resources/agent/pi-admitted-results.ts";

const admitted = {
	resultId: "123e4567-e89b-42d3-a456-426614174000",
	practiceSlug: "secure-authentication",
	outcome: "NOT_MET",
	evidence: { citations: [{ quote: "current state", path: "context/metadata.json" }] },
	problems: [
		{ id: "problem-1", severity: "MAJOR", citations: [{ quote: "insecure()", anchorable: true }] },
	],
	explanation: "An authentication call is unguarded.",
};

void test("admission projection preserves evidence and gives composition stable citation and problem references", () => {
	const result = admittedResultSchema.parse(admitted);
	assert.equal(result.explanation, admitted.explanation);
	assert.deepEqual(result.citations, [{ ...admitted.evidence.citations[0], index: 0 }]);
	assert.deepEqual(result.problems, [
		{
			problemId: "problem-1",
			severity: "MAJOR",
			citations: [{ quote: "insecure()", anchorable: true, index: 0 }],
		},
	]);
	assert.equal(leanResults([result])[0]?.problems[0]?.citations[0]?.anchorable, true);
});

void test("admission rejects an invalid identity, unknown outcome and a verdict inconsistent with its problems", () => {
	for (const result of [
		{ ...admitted, resultId: "result-1" },
		{ ...admitted, outcome: "WITHHELD" },
		{ ...admitted, outcome: "MET" },
		{ ...admitted, problems: [] },
		{ ...admitted, problems: [{ id: "", citations: [] }] },
		{ ...admitted, evidence: { citations: ["not a citation"] } },
	]) {
		assert.equal(admittedResultSchema.safeParse(result).success, false);
	}
	assert.equal(
		admittedResultSchema.safeParse({ ...admitted, outcome: "MET", problems: [] }).success,
		true,
	);
});
