import assert from "node:assert/strict";
import { test } from "node:test";
import { practiceIndexSchema } from "./runtime-inputs.ts";

void test("a malformed source declaration cannot silently narrow an absence search", () => {
	const entry = {
		slug: "test-practice",
		name: "Test practice",
		readsSources: ["scm.pull-request.diff"],
		exhaustiveSources: ["scm.pull-request.diff"],
	};
	assert.equal(practiceIndexSchema.safeParse([entry]).success, true);
	for (const changed of [
		{ ...entry, exhaustiveSources: ["scm.pull-request.diff", 42] },
		{ ...entry, readsSources: [null] },
		{ ...entry, knownLimitations: [42] },
		{ ...entry, contextPaths: [false] },
		{ ...entry, name: undefined },
		{ ...entry, exhaustiveSources: undefined },
	]) {
		assert.equal(practiceIndexSchema.safeParse([changed]).success, false);
	}
});
