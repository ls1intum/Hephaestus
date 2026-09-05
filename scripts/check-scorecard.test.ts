import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { test } from "node:test";
import { pathToFileURL } from "node:url";

import { checkScorecard } from "./check-scorecard.ts";
import { asArray, asRecord, readJsonFile } from "./lib/json.ts";

const baseline = asRecord(await readJsonFile("security/scorecard-baseline.json"), "baseline");
const now = Date.parse("2026-09-05T19:00:22Z");
const assessment = () => ({
	date: "2026-09-05T19:00:22Z",
	repo: { name: baseline.repository },
	checks: structuredClone(asArray(baseline.checks, "checks")).map((check) =>
		asRecord(check, "check"),
	),
});

/** The audit #1653 recorded, restated so that lowering a minimum is a deliberate edit here too. */
const enforced: Record<string, number> = {
	"Binary-Artifacts": 10,
	"CI-Tests": 10,
	"Code-Review": 10,
	"Dangerous-Workflow": 10,
	"Dependency-Update-Tool": 10,
	License: 10,
	Maintained: 10,
	"Pinned-Dependencies": 10,
	SAST: 10,
	"Security-Policy": 10,
	"Token-Permissions": 10,
	Vulnerabilities: 10,
	"Signed-Releases": 8,
	"Branch-Protection": 4,
};

void test("the committed baseline enforces the recorded minimums", () => {
	const excluded = asRecord(baseline.excluded, "excluded");
	assert.deepEqual(
		Object.fromEntries(
			asArray(baseline.checks, "checks")
				.map((check) => asRecord(check, "check"))
				.filter((check) => !Object.hasOwn(excluded, String(check.name)))
				.map((check) => [check.name, check.score]),
		),
		enforced,
	);
});

void test("the committed baseline passes and improvements do not compensate for regressions", () => {
	assert.deepEqual(checkScorecard(baseline, assessment(), now), []);
	const value = assessment();
	for (const check of value.checks) {
		if (check.name === "Branch-Protection") check.score = 10;
		if (check.name === "Pinned-Dependencies") check.score = 9;
	}
	assert.deepEqual(checkScorecard(baseline, value, now), ["Pinned-Dependencies: 9 (minimum 10)"]);
});

void test("each enforced check independently rejects any drop or missing evidence", () => {
	for (const [name, minimum] of Object.entries(enforced)) {
		const value = assessment();
		assert.deepEqual(
			checkScorecard(
				baseline,
				{ ...value, checks: value.checks.filter((check) => check.name !== name) },
				now,
			),
			[`${name}: missing (minimum ${minimum})`],
		);
		for (const check of value.checks) if (check.name === name) check.score = minimum - 1;
		assert.deepEqual(checkScorecard(baseline, value, now), [
			`${name}: ${minimum - 1} (minimum ${minimum})`,
		]);
	}
});

void test("only the four deliberate exclusions may drop without failure", () => {
	const value = assessment();
	for (const check of value.checks)
		if (["Contributors", "CII-Best-Practices", "Fuzzing", "Packaging"].includes(String(check.name)))
			check.score = -1;
	assert.deepEqual(checkScorecard(baseline, value, now), []);
});

void test("new checks do not silently become a new policy", () => {
	const value = assessment();
	value.checks.push({ name: "New-Check", score: 0 });
	assert.deepEqual(checkScorecard(baseline, value, now), []);
});

void test("rejects stale, future, invalid and pre-baseline assessments", () => {
	for (const date of ["2026-08-01", "2026-09-04T18:00:00Z", "2026-09-06", "not-a-date"])
		assert.throws(() => checkScorecard(baseline, { ...assessment(), date }, now));
	assert.throws(() => checkScorecard(baseline, assessment(), now + 8 * 86_400_000 + 1), /stale/);
	assert.deepEqual(checkScorecard(baseline, assessment(), now + 8 * 86_400_000), []);
});

void test("rejects malformed, duplicate and wrong-repository evidence", () => {
	for (const value of [null, {}, { ...assessment(), repo: { name: "github.com/attacker/repo" } }]) {
		assert.throws(() => checkScorecard(baseline, value, now));
	}
	for (const score of [null, "10", 11, -2, 9.5])
		assert.throws(() =>
			checkScorecard(baseline, { ...assessment(), checks: [{ name: "SAST", score }] }, now),
		);
	assert.throws(
		() =>
			checkScorecard(
				baseline,
				{
					...assessment(),
					checks: [
						{ name: "SAST", score: 10 },
						{ name: "SAST", score: 10 },
					],
				},
				now,
			),
		/duplicate/,
	);
});

void test("invalid policy cannot pass vacuously", () => {
	for (const change of [
		{ checks: [] },
		{ checks: [{ name: "SAST", score: -1 }], excluded: {} },
		{ checks: [{ name: "SAST", score: 10 }], excluded: { SAST: "All checks disabled" } },
		{ excluded: { SAST: "" } },
		{ excluded: { Typo: "reason" } },
		{ date: "invalid" },
	])
		assert.throws(() => checkScorecard({ ...baseline, ...change }, assessment(), now));
});

void test("the CLI preserves regression evidence and fails on transport and JSON errors", async (t) => {
	const directory = await mkdtemp(join(tmpdir(), "scorecard-cli-"));
	t.after(() => rm(directory, { recursive: true, force: true }));
	await mkdir(join(directory, "security"));
	await writeFile(join(directory, "security/scorecard-baseline.json"), JSON.stringify(baseline));
	const preload = join(directory, "fetch.mjs");
	await writeFile(
		preload,
		`globalThis.fetch = async () => new Response(process.env.RESPONSE_BODY, { status: Number(process.env.RESPONSE_STATUS) });`,
	);
	const summary = join(directory, "summary.md");
	const evidence = join(directory, "tmp/scorecard-assessment.json");
	const current = { ...assessment(), date: new Date().toISOString() };
	const regression = {
		...current,
		checks: current.checks.filter((check) => check.name !== "SAST"),
	};
	for (const [name, status, body, failure] of [
		["passing assessment", 200, JSON.stringify(current), false],
		["missing check", 200, JSON.stringify(regression), true],
		["HTTP failure", 503, "service unavailable", true],
		["invalid JSON", 200, "not JSON", true],
	] as const) {
		await t.test(name, async () => {
			await rm(summary, { force: true });
			await rm(evidence, { force: true });
			const result = spawnSync(
				process.execPath,
				["--import", pathToFileURL(preload).href, resolve("scripts/check-scorecard.ts")],
				{
					cwd: directory,
					timeout: 10_000,
					encoding: "utf8",
					env: {
						...process.env,
						GITHUB_STEP_SUMMARY: summary,
						RESPONSE_BODY: body,
						RESPONSE_STATUS: String(status),
					},
				},
			);
			assert.equal(result.status, failure ? 1 : 0, result.stderr);
			if (status === 200 && body !== "not JSON") {
				assert.deepEqual(JSON.parse(await readFile(evidence, "utf8")), JSON.parse(body));
				assert.match(
					await readFile(summary, "utf8"),
					failure ? /SAST: missing/ : /All enforced checks meet/,
				);
			} else {
				await assert.rejects(readFile(evidence), { code: "ENOENT" });
			}
		});
	}
});
