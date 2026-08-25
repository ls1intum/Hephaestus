import assert from "node:assert/strict";
import { test } from "node:test";
import { regressions } from "./check-ci-performance.ts";
import type { TestSummary } from "./summarize-test-results.ts";

const summary = (startup: number, wall = 200): TestSummary => ({
	schemaVersion: 2,
	name: "profile",
	files: 1,
	tests: 1,
	failures: 0,
	errors: 0,
	skipped: 0,
	testTimeSeconds: 100,
	slowest: [],
	performance: {
		wallTimeSeconds: wall,
		cpuTimeSeconds: 150,
		maxRssKilobytes: 500_000,
		contextStarts: 10,
		contextStartupSeconds: startup,
		contextCacheMisses: 10,
	},
});

await test("does not signal an absolute-budget miss from one profile", () => {
	const current = summary(121);
	assert.ok(current.performance);
	current.performance.contextStarts = 16;
	assert.deepEqual(regressions(current, []), []);
});

await test("signals only three consecutive misses against five prior profiles", () => {
	const slow = summary(121, 250);
	assert.ok(slow.performance);
	slow.performance.contextStarts = 16;
	const baseline = [100, 101, 99, 100, 100].map((startup) => summary(startup, 200));
	assert.deepEqual(regressions(slow, [...baseline, slow, slow]), [
		"context starts exceeded 15 in three consecutive profiles",
		"context startup exceeded 120s in three consecutive profiles",
		"wall time exceeded variance limit 240.0 three times",
	]);
});
