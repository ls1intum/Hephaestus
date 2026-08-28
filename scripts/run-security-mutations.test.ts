import assert from "node:assert/strict";
import { test } from "node:test";
import { summarizePitXml } from "./run-security-mutations.ts";

await test("accepts only complete PIT outcomes", () => {
	const result = summarizePitXml(`<mutations>
		<mutation status="KILLED"/><mutation status="SURVIVED"/>
		<mutation status="NO_COVERAGE"/><mutation status="EQUIVALENT"/>
	</mutations>`);

	assert.equal(result.valid, true);
	assert.equal(result.total, 4);
	assert.equal(result.counts.get("KILLED"), 1);
	assert.deepEqual(result.actionable, [
		{ className: "?", description: "?", line: "?", method: "?", status: "SURVIVED" },
		{ className: "?", description: "?", line: "?", method: "?", status: "NO_COVERAGE" },
	]);
});

await test("extracts mutations that need review", () => {
	const result = summarizePitXml(`<mutations><mutation status="SURVIVED">
		<mutatedClass>example.Guard</mutatedClass><mutatedMethod>allows</mutatedMethod>
		<lineNumber>42</lineNumber><description>negated conditional</description>
	</mutation></mutations>`);

	assert.deepEqual(result.actionable, [
		{
			className: "example.Guard",
			description: "negated conditional",
			line: "42",
			method: "allows",
			status: "SURVIVED",
		},
	]);
});

await test("accepts a singleton mutation", () => {
	assert.equal(summarizePitXml(`<mutations><mutation status="KILLED"/></mutations>`).valid, true);
});

await test("rejects technical failures even when PIT produced XML", () => {
	for (const status of [
		"RUN_ERROR",
		"TIMED_OUT",
		"MEMORY_ERROR",
		"NON_VIABLE",
		"NOT_STARTED",
		"STARTED",
	]) {
		assert.equal(
			summarizePitXml(`<mutations><mutation status="${status}"/></mutations>`).valid,
			false,
		);
	}
});

await test("rejects empty and malformed report structures", () => {
	assert.equal(summarizePitXml("<mutations/>").valid, false);
	assert.equal(summarizePitXml("<unrelated/>").valid, false);
	assert.equal(summarizePitXml("<mutations><mutation></mutations>").valid, false);
	assert.equal(summarizePitXml("<mutations><mutation/></mutations>").valid, false);
	assert.equal(summarizePitXml('<mutations><mutation status="FUTURE"/></mutations>').valid, false);
	assert.equal(
		summarizePitXml('<mutations><mutation status="KILLED"/><mutation>broken</mutation></mutations>')
			.valid,
		false,
	);
});
