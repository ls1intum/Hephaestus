import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import nodePath from "node:path";
import { after, test } from "node:test";

import {
	executionReportSchema,
	LEGACY_CONTRACT_ERROR,
	REASONS,
	type ExecutionEntry,
} from "../../../../../../docker/agents/practice-scripts/lib/contract.ts";
import { PRACTICES_DIR } from "./golden-review-workspace.ts";
import {
	capturedLines,
	directProgram,
	field,
	readJsonFile,
	runRunner,
	stagePractices,
	type RunnerExit,
	type SessionBehaviour,
	type StagedPractice,
} from "./program-host-runner.ts";

// The runner runs each admitted practice's program on the host: a custom program in a child process, the
// built-in in the runner. These runs assert what reaches out/: the execution report, and the admission request
// as result.json, review-state.json and the request body.

const roots: string[] = [];
after(() => {
	for (const root of roots) {
		rmSync(root, { recursive: true, force: true });
	}
});

/** A program whose `review()` asks for a review with a prepared direction, and returns its result. */
const REVIEWING_PROGRAM = `import { definePracticeScript } from "../lib/practice-script.ts";
export default definePracticeScript({
	meta: {},
	async run(ctx) {
		return ctx.review({ directions: ["Check each added authentication call."] });
	},
});
`;

/** A program written for the retired leads contract. */
const LEGACY_PROGRAM = `import { definePracticeScript } from "../lib/practice-script.ts";
export default definePracticeScript({
	meta: { version: 1, kinds: { call: "An added call." } },
	async run() {
		return { leads: [] };
	},
});
`;

interface Run extends RunnerExit {
	cwd: string;
}

async function run(
	practices: readonly StagedPractice[],
	sessions: Record<string, SessionBehaviour> = {},
	env: Record<string, string> = {},
	stage: (cwd: string) => void = () => undefined,
): Promise<Run> {
	const cwd = mkdtempSync(nodePath.join(tmpdir(), "pi-program-host-"));
	roots.push(cwd);
	stagePractices(cwd, practices);
	stage(cwd);
	return { cwd, ...(await runRunner(cwd, sessions, env)) };
}

/** The execution report, parsed by the contract the server reads it with. */
function executionReport(cwd: string) {
	const parsed = executionReportSchema.safeParse(readJsonFile(cwd, "out/practice-scripts.json"));
	assert.ok(parsed.success, parsed.error?.message);
	return parsed.data;
}

function entryOf(entries: readonly ExecutionEntry[], slug: string): ExecutionEntry {
	const entry = entries.find((item) => item.practiceSlug === slug);
	assert.ok(entry !== undefined, `${slug} is reported`);
	return entry;
}

/** The practices whose results `out/result.json` submits for admission, in its order. */
function admittedSlugs(cwd: string): unknown[] {
	const results: unknown = field(readJsonFile(cwd, "out/result.json"), "results");
	assert.ok(Array.isArray(results));
	return results.map((result: unknown) => field(result, "practiceSlug"));
}

const noCalls = { review: 0, chat: 0, decision: 0, embedding: 0, reranking: 0 };
const digestOf = (cwd: string, slug: string) =>
	createHash("sha256")
		.update(readFileSync(nodePath.join(cwd, PRACTICES_DIR, `${slug}.ts`)))
		.digest("hex");

const main = await run(
	[
		{ slug: "a-direct", program: directProgram() },
		{ slug: "b-builtin" },
		{ slug: "c-legacy", program: LEGACY_PROGRAM },
		{ slug: "d-silent", program: REVIEWING_PROGRAM },
		{ slug: "e-no-inputs", inputs: false },
	],
	{ "d-silent": "silent" },
);

void test("the runner admits the results that passed the host's checks and exits", () => {
	assert.equal(main.status, 0, main.stderr);
});

void test("a custom program that returns a result directly opens no session, and its result is admitted", () => {
	const prompted = capturedLines(main.cwd, "prompts").map((line) => field(line, "slug"));
	assert.equal(prompted.includes("a-direct"), false);
	const [admission] = capturedLines(main.cwd, "admission");
	const body: unknown = field(admission, "body");
	assert.partialDeepStrictEqual(body, {
		results: [
			{
				practiceSlug: "a-direct",
				candidate: {
					outcome: "NOT_APPLICABLE",
					summary: "No user interface changes; note none",
					citations: [
						{
							sourceKind: "scm.pull-request.core",
							artifactPath: "evidence/metadata.json",
							startLine: 1,
							endLine: 1,
						},
					],
					coverage: { source: "claimed", selected: 1, checked: 1, total: 1, complete: true },
				},
			},
		],
	});
	const entry = entryOf(executionReport(main.cwd).practices, "a-direct");
	assert.deepEqual(
		{ ...entry, durationMs: typeof entry.durationMs },
		{
			practiceSlug: "a-direct",
			program: { kind: "custom", digest: digestOf(main.cwd, "a-direct") },
			models: [{ slot: "chat", need: "optional", bound: false, notRated: {} }],
			status: "ok",
			stopReason: null,
			calls: noCalls,
			coverageReached: { reads: 0, truncatedReads: 0, failedReads: 0 },
			durationMs: "number",
		},
	);
});

void test("the built-in program opens one review session and reports the reads that session made", () => {
	const sessions = capturedLines(main.cwd, "prompts").filter(
		(line) => field(line, "slug") === "b-builtin",
	);
	assert.equal(sessions.length, 1);
	assert.match(
		JSON.stringify(capturedLines(main.cwd, "submitted")),
		/b-builtin: stored \(negative\)/u,
	);
	const entry = entryOf(executionReport(main.cwd).practices, "b-builtin");
	assert.deepEqual(
		{ ...entry, durationMs: typeof entry.durationMs },
		{
			practiceSlug: "b-builtin",
			program: { kind: "builtin", digest: null },
			models: [],
			status: "ok",
			stopReason: null,
			calls: { ...noCalls, review: 1 },
			coverageReached: { reads: 1, truncatedReads: 0, failedReads: 0 },
			durationMs: "number",
		},
	);
});

void test("a program in the retired leads contract fails with the legacy error, and only it is left out", () => {
	const entry = entryOf(executionReport(main.cwd).practices, "c-legacy");
	assert.partialDeepStrictEqual(entry, {
		program: { kind: "custom", digest: digestOf(main.cwd, "c-legacy") },
		status: "failed",
		stopReason: "invalid-program",
		error: LEGACY_CONTRACT_ERROR,
		calls: noCalls,
	});
	assert.deepEqual(admittedSlugs(main.cwd), ["a-direct", "b-builtin"]);
});

void test("a review session that ends without a result fails its program with an unrated reason", () => {
	const prompts = capturedLines(main.cwd, "prompts").filter(
		(line) => field(line, "slug") === "d-silent",
	);
	// One session, asked once more for the result it owes; its prepared input is staged for it to read.
	assert.equal(prompts.length, 2);
	assert.equal(new Set(prompts.map((line) => field(line, "session"))).size, 1);
	assert.ok(prompts.every((line) => field(line, "reviewInput") === true));
	const entry = entryOf(executionReport(main.cwd).practices, "d-silent");
	assert.partialDeepStrictEqual(entry, {
		status: "failed",
		stopReason: "off-format",
		error: "the review session of d-silent ended without a result",
		calls: { ...noCalls, review: 1 },
	});
	assert.ok(REASONS.some((reason) => reason === entry.stopReason));
	assert.equal(admittedSlugs(main.cwd).includes("d-silent"), false);
});

void test("a practice without its staged inputs fails as unavailable and opens no session", () => {
	const entry = entryOf(executionReport(main.cwd).practices, "e-no-inputs");
	assert.partialDeepStrictEqual(entry, {
		program: { kind: "builtin", digest: null },
		status: "failed",
		stopReason: "unavailable",
		calls: noCalls,
		durationMs: 0,
	});
	assert.match(entry.error ?? "", /inputs are not valid/u);
	const prompted = capturedLines(main.cwd, "prompts").map((line) => field(line, "slug"));
	assert.equal(prompted.includes("e-no-inputs"), false);
});

void test("the execution report has one entry for each admitted practice, sorted by slug", () => {
	const report = executionReport(main.cwd);
	assert.equal(report.truncated, false);
	assert.deepEqual(
		report.practices.map((entry) => entry.practiceSlug),
		["a-direct", "b-builtin", "c-legacy", "d-silent", "e-no-inputs"],
	);
});

void test("the admission request is result.json and review-state.json, sorted by slug, sent after the review input is removed", () => {
	const admissions = capturedLines(main.cwd, "admission");
	assert.equal(admissions.length, 1);
	const [admission] = admissions;
	const body: unknown = field(admission, "body");
	assert.deepEqual(readJsonFile(main.cwd, "out/result.json"), body);
	assert.deepEqual(readJsonFile(main.cwd, "out/review-state.json"), body);
	assert.partialDeepStrictEqual(body, {
		schemaVersion: 2,
		results: [{ practiceSlug: "a-direct" }, { practiceSlug: "b-builtin" }],
	});
	assert.equal(field(admission, "reviewInputPresent"), false);
	assert.equal(existsSync(nodePath.join(main.cwd, "work/review-input")), false);
	assert.deepEqual(capturedLines(main.cwd, "change-view"), [true]);
});

void test("a practice that the run's safety line stops before it starts is reported not reached", async () => {
	// Programs end RETURN_MS (5 s) before the safety line, so the budget leaves the first review room to finish.
	const stopped = await run(
		[{ slug: "a-first" }, { slug: "b-hangs" }, { slug: "c-never" }],
		{ "b-hangs": "hang" },
		{ AGENT_BUDGET_MS: "7000" },
	);
	assert.equal(stopped.status, 0, stopped.stderr);
	const report = executionReport(stopped.cwd);
	assert.partialDeepStrictEqual(entryOf(report.practices, "a-first"), { status: "ok" });
	assert.partialDeepStrictEqual(entryOf(report.practices, "b-hangs"), {
		status: "failed",
		stopReason: "deadline",
		calls: { ...noCalls, review: 1 },
	});
	assert.deepEqual(entryOf(report.practices, "c-never"), {
		practiceSlug: "c-never",
		program: { kind: "builtin", digest: null },
		models: [],
		status: "not-reached",
		stopReason: "deadline",
		calls: noCalls,
		coverageReached: { reads: 0, truncatedReads: 0, failedReads: 0 },
		durationMs: null,
	});
	const prompted = capturedLines(stopped.cwd, "prompts").map((line) => field(line, "slug"));
	assert.equal(prompted.includes("c-never"), false);
});

void test("a composition continuation derives no change, runs no program and writes no execution report", async () => {
	const continued = await run(
		[
			{ slug: "a-direct", program: directProgram('ctx.log("the program ran");') },
			{ slug: "b-builtin" },
		],
		{},
		{},
		(cwd) => {
			writeFileSync(
				nodePath.join(cwd, "evidence/composition-continuation.json"),
				JSON.stringify({ schemaVersion: 2, admissionDigest: "digest", results: [], withheld: [] }),
			);
		},
	);
	assert.equal(continued.status, 0, continued.stderr);
	assert.doesNotMatch(continued.stderr, /the program ran/u);
	assert.deepEqual(capturedLines(continued.cwd, "prompts"), []);
	assert.deepEqual(capturedLines(continued.cwd, "admission"), []);
	assert.deepEqual(capturedLines(continued.cwd, "change-view"), []);
	assert.equal(existsSync(nodePath.join(continued.cwd, "out/practice-scripts.json")), false);
});
