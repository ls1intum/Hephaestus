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

await test("enforces absolute context budgets without history", () => {
	const current = summary(121);
	assert.ok(current.performance);
	current.performance.contextStarts = 16;
	assert.deepEqual(regressions(current, []), [
		"context starts 16 > 15",
		"context startup 121.0s > 120s",
	]);
});

await test("uses median variance instead of a noisy single run", () => {
	assert.deepEqual(
		regressions(summary(100, 250), [summary(100, 198), summary(101, 200), summary(99, 202)]),
		["wall time 250.0 > variance limit 240.0"],
	);
});
