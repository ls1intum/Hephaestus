import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import {
	admittedResultSchema,
	admit,
	parseAdmission,
	admissionResponseSchema,
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

const response: unknown = JSON.parse(
	readFileSync(new URL("admission-response.json", import.meta.url), "utf8"),
);

void test("admission retries an interrupted answer with the exact frozen request", async () => {
	const request = { results: ["frozen measurement"] };
	const sent: string[] = [];
	const admission = await admit(request, async (body) => {
		sent.push(body);
		if (sent.length === 1) {
			request.results.push("must not enter the replay");
			return new Response("{", { status: 200 });
		}
		return Response.json(response);
	});
	assert.deepEqual(sent, [
		JSON.stringify({ results: ["frozen measurement"] }),
		JSON.stringify({ results: ["frozen measurement"] }),
	]);
	assert.equal(admission.results[0]?.practiceSlug, "handles-errors");
	assert.equal(admission.digest, "a".repeat(64));
});

void test("an explicit refusal and a malformed admission envelope are terminal", async () => {
	let calls = 0;
	await assert.rejects(
		admit({}, async () => {
			calls += 1;
			return Response.json({ detail: "not authorized" }, { status: 403 });
		}),
		/admission was refused: HTTP 403/u,
	);
	assert.equal(calls, 1);
	const valid = admissionResponseSchema.parse(response);
	for (const invalid of [
		{ ...valid, contractVersion: 2 },
		{ ...valid, admissionDigest: "not a digest" },
		{ ...valid, withheld: [{ practiceSlug: "names-things" }] },
		{ ...valid, publicReviewed: "false" },
	]) {
		assert.throws(() => parseAdmission(invalid));
	}
});
