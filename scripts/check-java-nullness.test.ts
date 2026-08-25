import assert from "node:assert/strict";
import test from "node:test";
import { nullnessPolicyViolations } from "./check-java-nullness.ts";

const source = (content: string) => [{ path: "Example.java", content }];

await test("accepts unrelated suppressions", () => {
	assert.deepEqual(
		nullnessPolicyViolations(source('@SuppressWarnings({ "unchecked", "deprecation" })')),
		[],
	);
});

await test("rejects direct and namespaced NullAway suppressions", () => {
	for (const warning of ["NullAway", "NullAway.Init", "NullAway.Optional"]) {
		assert.deepEqual(nullnessPolicyViolations(source(`@SuppressWarnings("${warning}")`)), [
			"Example.java",
		]);
	}
	assert.deepEqual(nullnessPolicyViolations(source('@java.lang.SuppressWarnings("NullAway")')), [
		"Example.java",
	]);
});

await test("rejects suppression arrays, concatenation, and Unicode escapes", () => {
	const examples = [
		'@SuppressWarnings({ "unchecked", "NullAway" })',
		'@SuppressWarnings("Null" + "Away")',
		'@SuppressWarnings("Null\\u0041way")',
	];
	for (const example of examples) {
		assert.deepEqual(nullnessPolicyViolations(source(example)), ["Example.java"]);
	}
});

await test("rejects suppression names hidden behind constants", () => {
	assert.deepEqual(nullnessPolicyViolations(source("@SuppressWarnings(NULL_AWAY)")), [
		"Example.java",
	]);
});

await test("ignores the warning name outside SuppressWarnings", () => {
	assert.deepEqual(nullnessPolicyViolations(source('String checker = "NullAway";')), []);
});
