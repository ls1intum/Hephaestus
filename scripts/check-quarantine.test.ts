import assert from "node:assert/strict";
import { test } from "node:test";

import { isTestSource, quarantineFindings } from "./check-quarantine.ts";

const today = new Date("2026-10-24T15:00:00Z");

void test("an unconditional skip without an owner and expiry fails", () => {
	const findings = quarantineFindings("src/a.test.ts", 'it.skip("loads", () => {});\n', today);
	assert.deepEqual(
		findings.map(({ line, problem }) => ({ line, problem })),
		[{ line: 1, problem: "skips a test without `quarantine #<issue> until <YYYY-MM-DD>`" }],
	);
	assert.equal(quarantineFindings("A.java", "@Disabled\nvoid loads() {}\n", today).length, 1);
	assert.equal(quarantineFindings("e2e/a.spec.ts", "xit('loads', () => {});\n", today).length, 1);
});

void test("a quarantine names its issue and ends within 14 days", () => {
	assert.deepEqual(
		quarantineFindings(
			"A.java",
			'@Disabled("quarantine #3150 until 2026-11-07: free-port race")\n',
			today,
		),
		[],
	);
	assert.deepEqual(
		quarantineFindings(
			"src/a.test.ts",
			'// quarantine #3150 until 2026-10-24: free-port race\nit.skip("loads", () => {});\n',
			today,
		),
		[],
	);
});

void test("an expired or distant quarantine fails", () => {
	const [expired] = quarantineFindings(
		"A.java",
		'@Disabled("quarantine #12 until 2026-10-23: race")\n',
		today,
	);
	assert.equal(expired?.problem, "quarantine of #12 expired on 2026-10-23");
	const [distant] = quarantineFindings(
		"A.java",
		'@Disabled("quarantine #12 until 2026-11-08: race")\n',
		today,
	);
	assert.equal(distant?.problem, "quarantine of #12 runs past 14 days (2026-11-08)");
});

void test("an environment-gated skip is not a quarantine", () => {
	const source = [
		'test.skip(!process.env.HEPHAESTUS_LIVE, "needs live credentials");',
		'it.skipIf(!hasDocker)("runs a container", () => {});',
	].join("\n");
	assert.deepEqual(quarantineFindings("e2e/live.spec.ts", source, today), []);
});

void test("only test sources are read", () => {
	assert.equal(isTestSource("server/application/src/test/java/de/A.java"), true);
	assert.equal(isTestSource("server/application/src/main/java/de/A.java"), false);
	assert.equal(isTestSource("webapp/src/routes/-a-route.test.tsx"), true);
	assert.equal(isTestSource("webapp/e2e/a.spec.ts"), true);
	assert.equal(isTestSource("webapp/src/a.ts"), false);
});
