import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { after, describe, test } from "node:test";

import { isJsonObject } from "./lib/json.ts";
import { stagePracticeScripts, type StagedInputs } from "./stage.ts";

const runner = path.join(import.meta.dirname, "runner.ts");
const pullRequestRecord = path.join(import.meta.dirname, "fixtures", "pull-request-metadata.json");

const roots: string[] = [];
after(async () =>
	Promise.all(roots.map(async (root) => rm(root, { recursive: true, force: true }))),
);

interface Run {
	json: (slug: string) => Promise<Record<string, unknown>>;
	section: (slug: string) => Promise<string>;
}

/**
 * Stages the scripts with `inputs` beside each, the record of the work under the context folder, and
 * runs the runner on the repository `repo`, or on the stage when none is given.
 */
async function run(
	scripts: Record<string, string>,
	inputs: StagedInputs,
	record: { file: string; value: unknown },
	repo?: { path: string; diff: string },
): Promise<Run> {
	const { root, practices } = await stagePracticeScripts(scripts, inputs);
	roots.push(root);
	const output = path.join(root, "out");
	const context = path.join(root, "context");
	await mkdir(context);
	await writeFile(path.join(context, record.file), JSON.stringify(record.value));
	const args = [
		runner,
		"--repo",
		repo?.path ?? root,
		"--context",
		context,
		"--context-reference",
		"context",
		"--practices",
		practices,
		"--output",
		output,
	];
	if (repo !== undefined) {
		await writeFile(path.join(root, "change.diff"), repo.diff);
		args.push("--diff", path.join(root, "change.diff"));
	}
	const { status, stderr } = spawnSync(process.execPath, args, { encoding: "utf8" });
	assert.equal(status, 0, stderr);
	return {
		json: async (slug) => {
			const parsed: unknown = JSON.parse(await readFile(path.join(output, `${slug}.json`), "utf8"));
			assert.ok(isJsonObject(parsed), `${slug}.json holds an object`);
			return parsed;
		},
		section: async (slug) => readFile(path.join(output, `${slug}.md`), "utf8"),
	};
}

const parsedRecord: unknown = JSON.parse(await readFile(pullRequestRecord, "utf8"));
assert.ok(isJsonObject(parsedRecord));
const pullRequest = parsedRecord;
const metadata = { file: "metadata.json", value: pullRequest };

/** The cause file of a pull request review with `value` as its cause. */
function pullRequestCause(value: Record<string, unknown>) {
	return {
		kind: "scm.pull_request",
		subject: { role: "AUTHOR" },
		cause: { origin: "LIVE", state: {}, admittedAt: "2026-01-01T00:00:00Z", ...value },
	};
}

/** The changed file that each lead of `slug` cites. */
async function cited(result: Run, slug: string): Promise<string[]> {
	const { leads } = await result.json(slug);
	assert.ok(Array.isArray(leads));
	return leads.map((lead: unknown) => {
		assert.ok(isJsonObject(lead) && isJsonObject(lead.at));
		return String(lead.at.change);
	});
}

/** A definition script whose facts are what it read from `ctx`. */
const READS_CONTEXT = `export default {
	meta: { kinds: {} },
	run: async (ctx) => ({
		leads: [],
		facts: {
			kind: ctx.kind,
			title: ctx.work.title ?? "none",
			type: ctx.cause.type,
			signal: ctx.cause.signal ?? "none",
			reviewer: ctx.cause.review?.reviewer ?? "none",
			state: JSON.stringify(ctx.cause.state),
			role: ctx.subject.role,
			previous: ctx.previous === null ? "none" : ctx.previous.outcome + "/" + (ctx.previous.severity ?? "-"),
			abortSignal: ctx.abortSignal instanceof AbortSignal,
			hasChange: "change" in ctx,
			hasMetadata: "metadata" in ctx,
			hasSignal: "signal" in ctx,
		},
	}),
};
`;

void describe("what a definition script reads about its review", () => {
	void test("a pull request review gives the kind, the record, the cause, the subject, the previous claim and the change", async () => {
		const { json } = await run(
			{ reads: READS_CONTEXT },
			{
				cause: {
					kind: "scm.pull_request",
					subject: { role: "REVIEWER" },
					cause: {
						type: "moment",
						signal: "scm.pull_request.reviewed",
						origin: "LIVE",
						state: { draftStatus: "NOT_DRAFT", state: "OPEN" },
						admittedAt: "2026-01-01T00:00:00Z",
						review: { id: 5, reviewer: "ada" },
					},
				},
				previous: {
					reviewedAt: "2025-12-31T00:00:00Z",
					cause: "requested",
					outcome: "NOT_MET",
					severity: "MAJOR",
					citations: [],
				},
			},
			metadata,
		);
		const { facts } = await json("reads");
		assert.deepEqual(facts, {
			kind: "scm.pull_request",
			title: "Count the items in the cart",
			type: "moment",
			signal: "scm.pull_request.reviewed",
			reviewer: "ada",
			state: '{"draftStatus":"NOT_DRAFT","state":"OPEN"}',
			role: "REVIEWER",
			previous: "NOT_MET/MAJOR",
			abortSignal: true,
			hasChange: true,
			hasMetadata: false,
			hasSignal: false,
		});
	});

	void test("an issue review gives the issue's record and no change", async () => {
		const { json } = await run(
			{ reads: READS_CONTEXT },
			{
				cause: {
					kind: "scm.issue",
					subject: { role: "AUTHOR" },
					cause: {
						type: "recheck",
						signal: "scm.issue.updated",
						origin: "LIVE",
						state: { state: "OPEN" },
						admittedAt: "2026-01-01T00:00:00Z",
					},
				},
				previous: null,
			},
			{
				file: "metadata.json",
				value: {
					issue_number: 3,
					title: "Cart total is wrong",
					body: "",
					state: "OPEN",
					state_reason: null,
					html_url: null,
					repository_full_name: "example/shop",
					author: "jennifer",
					author_id: 11,
					issue_type: null,
					is_locked: false,
					sub_issues_total: null,
					sub_issues_completed: null,
					milestone: null,
					closed_at: null,
					labels: [],
					assignees: [],
				},
			},
		);
		const { facts } = await json("reads");
		assert.partialDeepStrictEqual(facts, {
			kind: "scm.issue",
			title: "Cart total is wrong",
			type: "recheck",
			signal: "scm.issue.updated",
			previous: "none",
			hasChange: false,
		});
	});

	void test("inputs that do not parse fail each script with the file that holds the error", async () => {
		const definition =
			"export default { meta: { kinds: {} }, run: async () => ({ leads: [] }) };\n";
		const { json, section } = await run(
			{ definition },
			{
				cause: {
					kind: "scm.pull_request",
					subject: { role: "AUTHOR" },
					cause: {
						type: "scheduled",
						origin: "LIVE",
						state: {},
						admittedAt: "2026-01-01T00:00:00Z",
					},
				},
				previous: null,
			},
			metadata,
		);
		const failed = await json("definition");
		assert.partialDeepStrictEqual(failed, { contract: "definition", status: "error" });
		assert.match(
			String(failed.error),
			/^the review's inputs are not valid: definition\.cause\.json: cause\.type /u,
		);
		assert.match(await section("definition"), /\*\*Script failed\.\*\*/u);

		const missing = await run({ definition }, { cause: { kind: "scm.pull_request" } }, metadata);
		assert.partialDeepStrictEqual(await missing.json("definition"), {
			status: "error",
			error: "the review's inputs are not valid: definition.previous.json was not staged",
		});

		const noRecord = await run(
			{ definition },
			{ cause: { kind: "scm.pull_request" }, previous: null },
			{ file: "other.json", value: {} },
		);
		assert.partialDeepStrictEqual(await noRecord.json("definition"), {
			status: "error",
			error: "the review's inputs are not valid: metadata.json was not staged",
		});
	});

	void test("a script that expects another kind of work fails, and does not report that it found nothing", async () => {
		const { json } = await run(
			{
				issues: `import { expectKind } from "../lib/practice-script.ts";
export default {
	meta: { kinds: {} },
	run: async (ctx) => {
		expectKind(ctx, "scm.issue");
		return { leads: [] };
	},
};
`,
			},
			{
				cause: {
					kind: "scm.pull_request",
					subject: { role: "AUTHOR" },
					cause: {
						type: "requested",
						origin: "MANUAL",
						state: {},
						admittedAt: "2026-01-01T00:00:00Z",
					},
				},
				previous: null,
			},
			metadata,
		);
		assert.partialDeepStrictEqual(await json("issues"), {
			status: "error",
			error: "the script reads scm.issue, but this review is about scm.pull_request",
			leads: [],
		});
	});
});

/** A repository with a base, an earlier reviewed head, a rewritten head, and the reviewed head. */
async function history() {
	const root = await mkdtemp(path.join(tmpdir(), "practice-script-history-"));
	roots.push(root);
	const git = (...args: string[]) =>
		execFileSync(
			"git",
			["-C", root, "-c", "user.name=t", "-c", "user.email=t@example.invalid", ...args],
			{ encoding: "utf8" },
		).trim();
	const commit = async (text: string) => {
		await writeFile(path.join(root, "cart.ts"), text);
		git("add", "-A");
		git("commit", "-q", "-m", "change");
		return git("rev-parse", "HEAD");
	};
	git("init", "-q");
	const base = await commit("a\n");
	const rewritten = await commit("a\nforced\n");
	git("reset", "-q", "--hard", base);
	const previous = await commit("a\nold\n");
	const head = await commit("a\nold\nnew\n");
	return { root, base, rewritten, previous, head, diff: git("diff", base, head) };
}

/** A script that cites the line of the earlier review and the line added since, and reads the change since. */
const CITES_LINES = `export default {
	meta: { kinds: { "added-line": "A line that the change adds." } },
	run: async (ctx) => ({
		leads: [
			{ at: { change: "cart.ts", line: 2 }, kind: "added-line" },
			{ at: { change: "cart.ts", line: 3 }, kind: "added-line" },
		],
		facts: { changeSince: ctx.previous?.changeSince === undefined ? "unknown" : [...ctx.previous.changeSince.keys()].join(",") },
	}),
};
`;

void describe("leads on lines added since the last review", () => {
	void test("the runner marks each lead on a line added since the previous head and the section counts them", async () => {
		const repo = await history();
		const { json, section } = await run(
			{ cites: CITES_LINES },
			{
				cause: {
					kind: "scm.pull_request",
					subject: { role: "AUTHOR" },
					cause: {
						type: "moment",
						signal: "scm.pull_request.synchronized",
						origin: "LIVE",
						state: {},
						admittedAt: "2026-01-01T00:00:00Z",
					},
				},
				previous: {
					reviewedAt: "2025-12-31T00:00:00Z",
					cause: "moment",
					signal: "scm.pull_request.opened",
					outcome: "NOT_MET",
					severity: "MINOR",
					citations: [
						{
							sourceKind: "scm.pull-request.diff",
							path: "cart.ts",
							startLine: 2,
							endLine: 2,
							side: "NEW",
						},
					],
					head: repo.previous,
				},
			},
			{ file: "metadata.json", value: { ...pullRequest, commit_sha: repo.head } },
			{ path: repo.root, diff: repo.diff },
		);
		const result = await json("cites");
		assert.equal(result.newSinceLastReview, 1);
		assert.partialDeepStrictEqual(result, {
			facts: { changeSince: "cart.ts" },
			leads: [{ quote: "old" }, { quote: "new", newSinceLastReview: true }],
		});
		const text = await section("cites");
		assert.match(
			text,
			/\*\*Leads:\*\*\n1 of 2 leads point at lines added since the last review of this practice, each marked \(new\)\.\n- added-line at `cart\.ts` \[L2\]: `old`\n- added-line \(new\) at `cart\.ts` \[L3\]: `new`/u,
		);
	});

	void test("after a force push nothing is marked and the change since is absent, never empty", async () => {
		const repo = await history();
		const { json, section } = await run(
			{ cites: CITES_LINES },
			{
				cause: {
					kind: "scm.pull_request",
					subject: { role: "AUTHOR" },
					cause: {
						type: "moment",
						signal: "scm.pull_request.synchronized",
						origin: "LIVE",
						state: {},
						admittedAt: "2026-01-01T00:00:00Z",
					},
				},
				previous: {
					reviewedAt: "2025-12-31T00:00:00Z",
					cause: "moment",
					outcome: "MET",
					citations: [],
					head: repo.rewritten,
				},
			},
			{ file: "metadata.json", value: { ...pullRequest, commit_sha: repo.head } },
			{ path: repo.root, diff: repo.diff },
		);
		const result = await json("cites");
		assert.equal(result.newSinceLastReview, undefined);
		assert.partialDeepStrictEqual(result, { facts: { changeSince: "unknown" } });
		const text = await section("cites");
		assert.doesNotMatch(text, /\(new\)|since the last review/u);
	});
});

void describe("one script that reads its cause", () => {
	void test("the documented example does different work on a request, a push and a merge", async () => {
		const root = await mkdtemp(path.join(tmpdir(), "practice-script-causes-"));
		roots.push(root);
		const git = (...args: string[]) =>
			execFileSync(
				"git",
				["-C", root, "-c", "user.name=t", "-c", "user.email=t@example.invalid", ...args],
				{ encoding: "utf8" },
			).trim();
		const commit = async (files: Record<string, string>) => {
			for (const [file, text] of Object.entries(files)) {
				await writeFile(path.join(root, file), text);
			}
			git("add", "-A");
			git("commit", "-q", "-m", "change");
			return git("rev-parse", "HEAD");
		};
		git("init", "-q");
		const base = await commit({ "README.md": "shop\n" });
		const previous = await commit({ "cart.ts": "// TODO count twice\nexport const count = 1;\n" });
		const head = await commit({ "total.ts": "// TODO add tax\nexport const total = 2;\n" });
		const script = await readFile(
			path.join(import.meta.dirname, "fixtures", "by-cause.ts"),
			"utf8",
		);
		const record = {
			file: "metadata.json",
			value: { ...pullRequest, commit_sha: head },
		};
		const repo = { path: root, diff: git("diff", base, head) };
		const earlier = {
			reviewedAt: "2025-12-31T00:00:00Z",
			cause: "moment",
			signal: "scm.pull_request.opened",
			outcome: "NOT_MET",
			severity: "MINOR",
			citations: [],
			head: previous,
		};

		const pushed = await run(
			{ "by-cause": script },
			{
				cause: pullRequestCause({
					type: "moment",
					signal: "scm.pull_request.synchronized",
				}),
				previous: earlier,
			},
			record,
			repo,
		);
		assert.deepEqual(await cited(pushed, "by-cause"), ["total.ts"]);

		const requested = await run(
			{ "by-cause": script },
			{
				cause: pullRequestCause({ type: "requested", origin: "MANUAL" }),
				previous: earlier,
			},
			record,
			repo,
		);
		const requestedFiles = await cited(requested, "by-cause");
		assert.deepEqual(requestedFiles.toSorted(), ["cart.ts", "total.ts"]);
		assert.match(await requested.section("by-cause"), /^- A person asked for this review\./u);

		const merged = await run(
			{ "by-cause": script },
			{
				cause: pullRequestCause({ type: "moment", signal: "scm.pull_request.merged" }),
				previous: null,
			},
			record,
			repo,
		);
		const mergedFiles = await cited(merged, "by-cause");
		assert.deepEqual(mergedFiles.toSorted(), ["cart.ts", "total.ts"]);
		assert.match(await merged.section("by-cause"), /^- The work is merged\./u);
	});
});
