import assert from "node:assert/strict";
import { test } from "node:test";
import { markdown, parseJUnit, parsePerformance, summarize } from "./summarize-test-results.ts";

const REPORT = `<?xml version="1.0"?>
<testsuite name="example" tests="3" failures="1" errors="0" skipped="1" time="1.75">
  <testcase name="fast &amp; safe" classname="ExampleTest" time="0.25"/>
  <testcase name="slow" classname="ExampleTest" time="1.5"><failure message="nope"/></testcase>
  <testcase name="disabled" classname="ExampleTest" time="0"><skipped/></testcase>
</testsuite>`;

await test("parses outcomes and XML entities from JUnit test cases", () => {
	assert.deepEqual(parseJUnit(REPORT), [
		{
			className: "ExampleTest",
			name: "fast & safe",
			timeSeconds: 0.25,
			failed: false,
			errored: false,
			skipped: false,
		},
		{
			className: "ExampleTest",
			name: "slow",
			timeSeconds: 1.5,
			failed: true,
			errored: false,
			skipped: false,
		},
		{
			className: "ExampleTest",
			name: "disabled",
			timeSeconds: 0,
			failed: false,
			errored: false,
			skipped: true,
		},
	]);
});

await test("summarizes reports and ranks the slowest tests", () => {
	const summary = summarize("Server", [REPORT]);
	assert.equal(summary.tests, 3);
	assert.equal(summary.failures, 1);
	assert.equal(summary.errors, 0);
	assert.equal(summary.skipped, 1);
	assert.equal(summary.testTimeSeconds, 1.75);
	assert.deepEqual(summary.slowest[0], { test: "ExampleTest.slow", seconds: 1.5 });
	assert.match(markdown(summary), /\*\*3 tests\*\* · 1 failed · 0 errors · 1 skipped/);
});

await test("uses XML structure rather than matching tag-like text", () => {
	const report = `<testsuites><testsuite name="edge cases">
		<testcase classname="ExampleTest" name="accepts > in attributes" time="0.1">
			<system-out><![CDATA[diagnostic text containing <failure but no failure element]]></system-out>
		</testcase>
		<testcase classname="ExampleTest" name="failed" time="0.2"><failure>assertion</failure></testcase>
	</testsuite></testsuites>`;

	assert.deepEqual(parseJUnit(report), [
		{
			className: "ExampleTest",
			name: "accepts > in attributes",
			timeSeconds: 0.1,
			failed: false,
			errored: false,
			skipped: false,
		},
		{
			className: "ExampleTest",
			name: "failed",
			timeSeconds: 0.2,
			failed: true,
			errored: false,
			skipped: false,
		},
	]);
});

await test("extracts process and Spring context metrics", () => {
	const performance = parsePerformance(
		"Started FirstTest in 4.25 seconds\nDefaultContextCache@abc missCount = 1\nStarted SecondTest in 5.75 seconds\nDefaultContextCache@abc missCount = 2",
		`User time (seconds): 12.5
System time (seconds): 2.5
Elapsed (wall clock) time (h:mm:ss or m:ss): 1:03.50
Maximum resident set size (kbytes): 524288`,
	);
	assert.deepEqual(performance, {
		wallTimeSeconds: 63.5,
		cpuTimeSeconds: 15,
		maxRssKilobytes: 524288,
		contextStarts: 2,
		contextStartupSeconds: 10,
		contextCacheMisses: 2,
	});
});
