import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdir, mkdtemp, readdir, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { test, type TestContext } from "node:test";

import { LEGACY_CONTRACT_ERROR, RETIRED_FORM_ERROR } from "./lib/contract.ts";
import { isJsonObject } from "./lib/json.ts";
import { pullRequestRecord, REVIEWED } from "./run-bundled.ts";

const validate = path.join(import.meta.dirname, "validate.ts");

const CHANGE = [
	"diff --git a/src/cart.ts b/src/cart.ts",
	"--- a/src/cart.ts",
	"+++ b/src/cart.ts",
	"@@ -1,1 +1,3 @@",
	" export class Cart {",
	"+  // increment the item count by one",
	"+  count += 1;",
].join("\n");

/** A program that rules the practice out on its own, citing the line of the change it read. */
const RETURNS_RESULT = `export default {
	meta: {},
	run: async (ctx) => ({
		outcome: "NOT_APPLICABLE",
		summary: "The change adds one comment",
		explanation: "The practice is about tests; the change adds none.",
		citations: [await ctx.files.change.cite("src/cart.ts", "NEW", 2)],
		evidence: {
			inapplicability: {
				consulted: ["scm.pull-request.diff"],
				subject: "tests that the change adds",
				ruledOutBy: "The change adds one comment and one statement.",
			},
		},
		coverage: { source: "claimed", selected: 1, checked: 1, total: 1, complete: true },
	}),
};
`;

/** A program that asks one review about the added comment. */
const ASKS_REVIEW = `export default {
	meta: {},
	run: async (ctx) =>
		ctx.review({
			leads: [{ at: { change: "src/cart.ts", line: 2 }, label: "An added comment that may restate the code." }],
			facts: { comments: 1 },
		}),
};
`;

/** A program that tells the review why it runs. */
const BY_CAUSE = `export default {
	meta: {},
	run: async (ctx) => {
		const { cause } = ctx;
		const direction =
			cause.type === "requested"
				? "A person asked for this review."
				: cause.type === "recheck"
					? "The last review was " + (ctx.previous?.outcome ?? "none") + "."
					: cause.signal === "scm.pull_request.merged"
						? "The work is merged."
						: "The work changed.";
		return ctx.review({ directions: [direction] });
	},
};
`;

/** A folder with a change, a repository, captured context, a work record and a review result. */
async function workspace(t: TestContext) {
	const root = await mkdtemp(path.join(tmpdir(), "practice-script-validate-"));
	t.after(async () => rm(root, { recursive: true, force: true }));
	await mkdir(path.join(root, "repo"));
	await mkdir(path.join(root, "context"));
	await writeFile(path.join(root, "change.diff"), CHANGE);
	await writeFile(path.join(root, "metadata.json"), JSON.stringify(pullRequestRecord()));
	await writeFile(path.join(root, "reviewed.json"), JSON.stringify(REVIEWED));
	const program = async (slug: string, source: string) => {
		const file = path.join(root, `${slug}.ts`);
		await writeFile(file, source);
		return file;
	};
	return {
		root,
		program,
		/** The options of a pull request with its change and its record. */
		pullRequest: [
			"--repo",
			path.join(root, "repo"),
			"--diff",
			path.join(root, "change.diff"),
			"--metadata",
			path.join(root, "metadata.json"),
		],
		review: ["--review", path.join(root, "reviewed.json")],
	};
}

function run(args: string[], env: NodeJS.ProcessEnv = process.env) {
	return spawnSync(process.execPath, [validate, ...args], { encoding: "utf8", env });
}

/** The files that validate.ts prints, by name: each under a `==> <name> <==` line. */
function printed(stdout: string): Map<string, string> {
	const files = new Map<string, string>();
	const parts = stdout.split(/^==> (?<name>\S+) <==\n/mu).slice(1);
	for (let i = 0; i + 1 < parts.length; i += 2) {
		files.set(parts[i] ?? "", (parts[i + 1] ?? "").trimEnd());
	}
	return files;
}

/** The report `<slug>.json` that a run printed. */
function report(stdout: string, slug: string): Record<string, unknown> {
	const value: unknown = JSON.parse(printed(stdout).get(`${slug}.json`) ?? "null");
	assert.ok(isJsonObject(value), stdout);
	return value;
}

/** The last line a run wrote to stderr: its one-line summary. */
const summary = (stderr: string) => stderr.trimEnd().split("\n").at(-1) ?? "";

void test("a program that returns its own result runs without models and prints it", async (t) => {
	const { root, program, pullRequest } = await workspace(t);
	const out = path.join(root, "out");
	const { status, stdout, stderr } = run([
		"--script",
		await program("rules-out", RETURNS_RESULT),
		...pullRequest,
		"--out",
		out,
	]);
	assert.equal(status, 0, stderr);
	const jsonPath = path.join(out, "rules-out.json");
	const files = printed(stdout);
	// With `--out`, each file is printed under the path that it was written to.
	assert.deepEqual([...files.keys()], [jsonPath]);
	assert.partialDeepStrictEqual(JSON.parse(files.get(jsonPath) ?? ""), {
		practiceSlug: "rules-out",
		program: { kind: "custom" },
		status: "ok",
		stopReason: null,
		calls: { review: 0 },
		reviews: [],
		candidate: {
			outcome: "NOT_APPLICABLE",
			citations: [
				{
					sourceKind: "scm.pull-request.diff",
					artifactPath: "context/change.json",
					path: "src/cart.ts",
					side: "NEW",
					startLine: 2,
					endLine: 2,
					quote: "  // increment the item count by one",
				},
			],
		},
	});
	const written = await readFile(jsonPath, "utf8");
	assert.equal(written.trimEnd(), files.get(jsonPath));
	assert.equal(summary(stderr), "rules-out: ok: the program returned a NOT_APPLICABLE result");
});

void test("a program that asks a review without --review fails as unavailable, and never gets a made-up result", async (t) => {
	const { program, pullRequest } = await workspace(t);
	const { status, stdout, stderr } = run([
		"--script",
		await program("asks", ASKS_REVIEW),
		...pullRequest,
	]);
	assert.equal(status, 1, stderr);
	const printedReport = report(stdout, "asks");
	assert.partialDeepStrictEqual(printedReport, {
		status: "failed",
		stopReason: "unavailable",
		calls: { review: 1 },
		reviews: [{ section: "asks.review-1.md", unrated: { reason: "unavailable" } }],
	});
	assert.equal("candidate" in printedReport, false);
	assert.match(summary(stderr), /^asks: failed \(unavailable\): .*--review <candidate\.json>/u);
	// The input that the program asked for is printed all the same.
	assert.match(printed(stdout).get("asks.review-1.md") ?? "", /^ {2}- at `src\/cart\.ts` \[L2\]/mu);
});

void test("with --review, review() returns that result and the tool prints the review input and the result", async (t) => {
	const { root, program, pullRequest, review } = await workspace(t);
	const out = path.join(root, "out");
	const { status, stdout, stderr } = run([
		"--script",
		await program("asks", ASKS_REVIEW),
		...pullRequest,
		...review,
		"--out",
		out,
	]);
	assert.equal(status, 0, stderr);
	const files = printed(stdout);
	const sectionPath = path.join(out, "asks.review-1.md");
	assert.deepEqual([...files.keys()], [path.join(out, "asks.json"), sectionPath]);
	assert.partialDeepStrictEqual(JSON.parse(files.get(path.join(out, "asks.json")) ?? ""), {
		status: "ok",
		calls: { review: 1 },
		reviews: [
			{
				input: {
					leads: [
						{
							at: { change: "src/cart.ts", line: 2 },
							label: "An added comment that may restate the code.",
							quote: "  // increment the item count by one",
						},
					],
					facts: { comments: 1 },
				},
				section: sectionPath,
				candidate: REVIEWED,
			},
		],
		candidate: REVIEWED,
	});
	const section = files.get(sectionPath) ?? "";
	assert.equal(
		section,
		[
			"**Leads:**",
			"- An added comment that may restate the code.",
			"  - at `src/cart.ts` [L2]: `// increment the item count by one`",
			"",
			"**Facts:**",
			"- comments = 1",
		].join("\n"),
	);
	const written = await readFile(sectionPath, "utf8");
	assert.equal(written.trimEnd(), section);
});

void test("a program in the retired form exits with 1 and the line that tells how to rewrite it", async (t) => {
	const { program, pullRequest } = await workspace(t);
	const script = await program(
		"counts-lines",
		"export default (_repo, diff) => ({ hints: [], metrics: { files: diff.size }, directions: [] });\n",
	);
	const { status, stdout, stderr } = run(["--script", script, ...pullRequest]);
	assert.equal(status, 1);
	assert.partialDeepStrictEqual(report(stdout, "counts-lines"), {
		status: "failed",
		error: RETIRED_FORM_ERROR,
	});
	assert.equal(summary(stderr), `counts-lines: failed (invalid-program): ${RETIRED_FORM_ERROR}`);
});

void test("a program written for the leads contract exits with 1 and the line that tells how to upgrade it", async (t) => {
	const { program, pullRequest } = await workspace(t);
	const script = await program(
		"old-leads",
		'export default { meta: { version: 1, kinds: { "x": "A lead." } }, run: async () => ({ leads: [] }) };\n',
	);
	const { status, stdout, stderr } = run(["--script", script, ...pullRequest]);
	assert.equal(status, 1);
	assert.partialDeepStrictEqual(report(stdout, "old-leads"), {
		status: "failed",
		stopReason: "invalid-program",
		error: LEGACY_CONTRACT_ERROR,
	});
	assert.equal(summary(stderr), `old-leads: failed (invalid-program): ${LEGACY_CONTRACT_ERROR}`);
	// A leads value returned under a current `meta` is refused with the same line.
	const returnsLeads = await program(
		"returns-leads",
		"export default { meta: {}, run: async () => ({ leads: [] }) };\n",
	);
	const returned = run(["--script", returnsLeads, ...pullRequest]);
	assert.equal(returned.status, 1);
	assert.partialDeepStrictEqual(report(returned.stdout, "returns-leads"), {
		status: "failed",
		stopReason: "invalid-candidate",
		error: LEGACY_CONTRACT_ERROR,
	});
});

void test("a program that requires a model is skipped, and one that returns no result fails", async (t) => {
	const { program, pullRequest } = await workspace(t);
	const skipped = run([
		"--script",
		await program(
			"requires-decision",
			'export default { meta: { models: { decision: "required" } }, run: async () => { throw new Error("never runs"); } };\n',
		),
		...pullRequest,
	]);
	assert.equal(skipped.status, 1, skipped.stderr);
	assert.partialDeepStrictEqual(report(skipped.stdout, "requires-decision"), {
		status: "skipped",
		stopReason: "unavailable",
		models: [{ slot: "decision", need: "required", bound: false, notRated: {} }],
	});
	const failed = run([
		"--script",
		await program(
			"invalid-result",
			'export default { meta: {}, run: async () => ({ outcome: "MET" }) };\n',
		),
		...pullRequest,
	]);
	assert.equal(failed.status, 1);
	const failedReport = report(failed.stdout, "invalid-result");
	assert.partialDeepStrictEqual(failedReport, {
		status: "failed",
		stopReason: "invalid-candidate",
	});
	assert.match(
		String(failedReport.error),
		/^the program returned no valid result: malformed_candidate: /u,
	);
});

void test("a program whose process is killed exits with an error that is not a timeout, and no staged folder is left", async (t) => {
	const { root, program, pullRequest } = await workspace(t);
	const temporary = path.join(root, "tmp");
	await mkdir(temporary);
	// A kill that the host did not send, as the kernel sends when memory runs out.
	const script = await program(
		"killed",
		'process.kill(process.pid, "SIGKILL");\nexport default {};\n',
	);
	const { status, stdout, stderr } = run(["--script", script, ...pullRequest], {
		...process.env,
		TMPDIR: temporary,
	});
	assert.equal(status, 1, stderr);
	assert.partialDeepStrictEqual(report(stdout, "killed"), {
		status: "failed",
		stopReason: "error",
		error: "the program's process was killed by SIGKILL",
	});
	assert.deepEqual(await readdir(temporary), []);
});

void test("a program runs as a request by hand, or at the moment and with the previous result that the options give", async (t) => {
	const { root, program, pullRequest, review } = await workspace(t);
	const base = ["--script", await program("by-cause", BY_CAUSE), ...pullRequest, ...review];
	const directionOf = (stdout: string) => printed(stdout).get("by-cause.review-1.md") ?? "";

	const requested = run(base);
	assert.equal(requested.status, 0, requested.stderr);
	assert.equal(directionOf(requested.stdout), "- A person asked for this review.");

	const merged = run([...base, "--signal", "scm.pull_request.merged"]);
	assert.equal(merged.status, 0, merged.stderr);
	assert.equal(directionOf(merged.stdout), "- The work is merged.");

	await writeFile(
		path.join(root, "previous.json"),
		JSON.stringify({
			practiceResultId: "0b6c1f43-6f0e-4d0c-9a59-6d1f0d6f8a11",
			reviewedAt: "2026-01-01T00:00:00Z",
			cause: "moment",
			outcome: "NOT_MET",
			severity: "MINOR",
			citations: [],
		}),
	);
	const recheck = run([
		...base,
		"--signal",
		"scm.pull_request.synchronized",
		"--recheck",
		"--previous",
		path.join(root, "previous.json"),
	]);
	assert.equal(recheck.status, 0, recheck.stderr);
	assert.equal(directionOf(recheck.stdout), "- The last review was NOT_MET.");

	// A cause file in the server's form names the practice revision and the scope.
	await writeFile(
		path.join(root, "cause.json"),
		JSON.stringify({
			kind: "scm.pull_request",
			subject: { role: "AUTHOR" },
			practice: { revisionId: 4 },
			scope: {
				key: "scm.pull_request:7:AUTHOR",
				mode: "change",
				description: "the reviewed change",
			},
			cause: {
				type: "moment",
				signal: "scm.pull_request.opened",
				origin: "LIVE",
				state: {},
				admittedAt: "2026-01-01T00:00:00Z",
			},
		}),
	);
	const byFile = run([...base, "--cause", path.join(root, "cause.json")]);
	assert.equal(byFile.status, 0, byFile.stderr);
	assert.equal(directionOf(byFile.stdout), "- The work changed.");

	// The record names the kind: a pull request record cannot be read as an issue.
	const issue = run([...base, "--signal", "scm.issue.opened"]);
	assert.equal(issue.status, 1);
	assert.partialDeepStrictEqual(report(issue.stdout, "by-cause"), {
		status: "failed",
		stopReason: "unavailable",
		calls: { review: 0 },
	});
	assert.match(
		String(report(issue.stdout, "by-cause").error),
		/^the review's inputs are not valid: metadata\.json: work\./u,
	);
	// A previous result without the id of the result it is fails on its own file.
	await writeFile(
		path.join(root, "old-previous.json"),
		JSON.stringify({
			reviewedAt: "2026-01-01T00:00:00Z",
			cause: "moment",
			outcome: "MET",
			citations: [],
		}),
	);
	const oldPrevious = run([...base, "--previous", path.join(root, "old-previous.json")]);
	assert.equal(oldPrevious.status, 1);
	assert.match(
		String(report(oldPrevious.stdout, "by-cause").error),
		/^the review's inputs are not valid: by-cause\.previous\.json: previous\.practiceResultId /u,
	);
});

void test("a file whose name is not a practice identifier, or a missing argument, is refused", async (t) => {
	const { root, program } = await workspace(t);
	const script = await program("Not_A_Slug", RETURNS_RESULT);
	const named = run(["--script", script, "--repo", root]);
	assert.equal(named.status, 2);
	assert.match(named.stderr, /practice's identifier/u);
	assert.equal(named.stderr.trimEnd().split("\n").length, 1);
	const missing = run(["--script", script]);
	assert.equal(missing.status, 2);
	assert.match(
		missing.stderr,
		/^--script and --repo are required\. Usage: node validate\.ts --script <file\.ts> --repo <clone>/u,
	);
});

void test("an option or an input that the command cannot use stops it with one line and exit code 2", async (t) => {
	const { root, program } = await workspace(t);
	const repo = path.join(root, "repo");
	const script = await program("asks", ASKS_REVIEW);
	await writeFile(path.join(root, "list.json"), "[]");
	await writeFile(path.join(root, "broken.json"), "{");
	await writeFile(path.join(root, "empty.diff"), "");
	await writeFile(path.join(root, "notes.diff"), "No patch here.\n");
	await writeFile(path.join(root, "leads.json"), JSON.stringify({ leads: [] }));
	const cases: [string[], RegExp][] = [
		[["--script", script, "--repo", repo, "--bogus"], /^Unknown option '--bogus'\. Usage: /u],
		[["--script", script, "--repo", repo, "extra"], /^Unexpected argument 'extra'\. Usage: /u],
		[["--repo", repo, "--script"], /^Option '--script <value>' argument missing\. Usage: /u],
		[
			["--script", script, "--repo", path.join(root, "absent")],
			/^--repo: \S+absent is not a folder$/u,
		],
		[
			["--script", script, "--repo", path.join(root, "change.diff")],
			/^--repo: .* is not a folder$/u,
		],
		[["--script", script, "--repo", repo, "--context", path.join(root, "absent")], /^--context: /u],
		[["--script", path.join(root, "absent.ts"), "--repo", repo], /^--script: ENOENT/u],
		[
			["--script", script, "--repo", repo, "--diff", path.join(root, "absent.diff")],
			/^--diff: ENOENT/u,
		],
		[
			["--script", script, "--repo", repo, "--diff", path.join(root, "empty.diff")],
			/^--diff: \S+empty\.diff holds no unified diff$/u,
		],
		[
			["--script", script, "--repo", repo, "--diff", path.join(root, "notes.diff")],
			/^--diff: \S+notes\.diff holds no unified diff$/u,
		],
		[
			["--script", script, "--repo", repo, "--out", path.join(root, "list.json")],
			/^--out: EEXIST/u,
		],
		[
			[
				"--script",
				script,
				"--repo",
				repo,
				"--cause",
				path.join(root, "list.json"),
				"--signal",
				"scm.issue.opened",
			],
			/^--cause and --signal exclude each other\. Usage: /u,
		],
		[["--script", script, "--repo", repo, "--recheck"], /^--recheck needs --signal\. Usage: /u],
		[
			["--script", script, "--repo", repo, "--signal", "scm.pull_request.manual_review"],
			/^--signal: scm\.pull_request\.manual_review is not a moment of any kind of work$/u,
		],
		[
			["--script", script, "--repo", repo, "--previous", path.join(root, "broken.json")],
			/^--previous: \S+broken\.json is not JSON: /u,
		],
		[
			["--script", script, "--repo", repo, "--metadata", path.join(root, "broken.json")],
			/^--metadata: \S+broken\.json is not JSON: /u,
		],
		[
			["--script", script, "--repo", repo, "--metadata", path.join(root, "list.json")],
			/^--metadata: \S+list\.json must hold a JSON object$/u,
		],
		[
			["--script", script, "--repo", repo, "--review", path.join(root, "broken.json")],
			/^--review: \S+broken\.json is not JSON: /u,
		],
		[
			["--script", script, "--repo", repo, "--review", path.join(root, "leads.json")],
			/^--review: \S+leads\.json is not a result: malformed_candidate: /u,
		],
	];
	for (const [args, message] of cases) {
		const { status, stdout, stderr } = run(args);
		assert.equal(status, 2, `${args.join(" ")}\n${stderr}`);
		assert.equal(stdout, "", args.join(" "));
		assert.match(stderr.trimEnd(), message, args.join(" "));
		assert.equal(stderr.trimEnd().split("\n").length, 1, stderr);
	}
});
