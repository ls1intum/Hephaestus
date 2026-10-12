/**
 * What a practice program reads about its review, through the host library as the runner composes it:
 * `InputReader` validates the staged inputs, `runPracticeProgram` runs the staged program in its
 * child, and a fake review service resolves and renders each review input as the runner does.
 */
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { after, describe, test } from "node:test";

import type { Candidate, ReviewOptions } from "./lib/contract.ts";
import { parseDiff } from "./lib/diff-parser.ts";
import { EvidenceReader, noReceipts, type EvidenceSources } from "./lib/evidence-reader.ts";
import { isJsonObject } from "./lib/json.ts";
import { renderReviewInput, resolveLeads, type QuotedLead } from "./lib/leads.ts";
import { ModelBudget } from "./lib/models-host.ts";
import { notReached, runPracticeProgram, type ProgramOutcome } from "./lib/program-host.ts";
import { InputReader } from "./lib/script-inputs.ts";
import { issueRecord, pullRequestRecord, REVIEWED } from "./run-bundled.ts";
import { stagePracticeScripts, type StagedInputs } from "./stage.ts";

const roots: string[] = [];
after(async () =>
	Promise.all(roots.map(async (root) => rm(root, { recursive: true, force: true }))),
);

const CONTEXT_REFERENCE = "context";
const FULL_INPUT = "`out/review-input.json`";

/** One review that a program asked for, as the fake review service saw it. */
interface Asked {
	options: ReviewOptions;
	leads: QuotedLead[];
	section: string;
}

interface Hosted {
	outcome: ProgramOutcome;
	reviews: Asked[];
}

/** The practice revision and authored scope of each staged cause. */
const BOUND = {
	practice: { revisionId: 3 },
	scope: { key: "scm.pull_request:7:AUTHOR", mode: "change", description: "the reviewed change" },
};

/** The cause file of a pull request review with `value` as its cause. */
function pullRequestCause(value: Record<string, unknown>) {
	return {
		kind: "scm.pull_request",
		subject: { role: "AUTHOR" },
		...BOUND,
		cause: { origin: "LIVE", state: {}, admittedAt: "2026-01-01T00:00:00Z", ...value },
	};
}

/** A previous result of the practice, in the form the server stages it. */
function previousResult(fields: Record<string, unknown>) {
	return {
		practiceResultId: "5d2f7a8e-0c1b-4f3a-9e6d-2b8c4a1f0e77",
		reviewedAt: "2025-12-31T00:00:00Z",
		cause: "moment",
		citations: [],
		...fields,
	};
}

/**
 * Stage the programs with `inputs` beside each and the record of the work in the context folder, then
 * run each program as the runner does: inputs that do not parse fail it as unavailable, and each
 * review it asks for resolves its leads and renders its section before the fake review answers.
 */
async function host(
	programs: Record<string, string>,
	inputs: StagedInputs,
	record: { file: string; value: unknown },
	repo?: { path: string; diff: string },
): Promise<Map<string, Hosted>> {
	const { root, practices } = await stagePracticeScripts(programs, inputs);
	roots.push(root);
	const contextDir = path.join(root, "context");
	await mkdir(contextDir);
	await writeFile(path.join(contextDir, record.file), JSON.stringify(record.value));
	const reader = new InputReader({
		practicesDir: practices,
		contextDir,
		repo: repo?.path ?? root,
		log: () => undefined,
	});
	const hosted = new Map<string, Hosted>();
	for (const slug of Object.keys(programs)) {
		const script = path.join(practices, `${slug}.ts`);
		const program = { script: { path: script, source: await readFile(script) } };
		const read = await reader.read(slug);
		const reviews: Asked[] = [];
		if ("error" in read) {
			hosted.set(slug, {
				outcome: {
					...notReached(program, "unavailable"),
					status: "failed",
					error: `the review's inputs are not valid: ${read.error}`,
					durationMs: 0,
				},
				reviews,
			});
			continue;
		}
		const head = read.inputs.kind === "scm.pull_request" ? read.inputs.work.commit_sha : null;
		const change =
			repo === undefined || head === null
				? null
				: { base: "b".repeat(40), head, files: parseDiff(repo.diff) };
		const sources: EvidenceSources = {
			repo: repo?.path ?? root,
			headRevision: head,
			treeArtifact: "repos/reviewed/.git/HEAD",
			contextDir,
			contextReference: CONTEXT_REFERENCE,
			change,
			sourceKindOf: () => undefined,
		};
		const receipts = noReceipts();
		const outcome = await runPracticeProgram(
			program,
			{
				slug,
				inputs: read.inputs,
				criteria: "",
				...(read.changeSince === undefined ? {} : { changeSince: read.changeSince }),
				files: head === null ? null : { head: { revision: head }, change },
				now: "2026-01-01T00:00:00Z",
				deadline: Date.now() + 60_000,
			},
			{
				reader: new EvidenceReader(sources, receipts),
				models: {},
				budget: new ModelBudget(0, 1),
				review: async (options): Promise<Candidate> => {
					const leads = await resolveLeads(options.leads ?? [], sources, read.changeSince);
					reviews.push({
						options,
						leads,
						section: renderReviewInput(
							options,
							leads,
							CONTEXT_REFERENCE,
							FULL_INPUT,
							read.changeSince !== undefined,
						),
					});
					return REVIEWED;
				},
				signal: new AbortController().signal,
				log: () => undefined,
				receipts,
			},
		);
		hosted.set(slug, { outcome, reviews });
	}
	return hosted;
}

/** The outcome of `slug`, and its one review. */
function only(
	hosted: Map<string, Hosted>,
	slug: string,
): { outcome: ProgramOutcome; review: Asked } {
	const run = hosted.get(slug);
	assert.ok(run !== undefined, `${slug} ran`);
	assert.equal(run.outcome.status, "ok", run.outcome.error);
	assert.deepEqual(run.outcome.candidate, REVIEWED);
	const [review, ...more] = run.reviews;
	assert.ok(review !== undefined && more.length === 0, `${slug} asked one review`);
	return { outcome: run.outcome, review };
}

const pullRequest = pullRequestRecord({ title: "Count the items in the cart" });
const metadata = { file: "metadata.json", value: pullRequest };

const CHANGE = [
	"diff --git a/src/cart.ts b/src/cart.ts",
	"--- a/src/cart.ts",
	"+++ b/src/cart.ts",
	"@@ -1,1 +1,2 @@",
	" export class Cart {",
	"+  count += 1;",
].join("\n");

/** The error of `slug`, which failed as unavailable before it asked any review. */
function failed(hosted: Map<string, Hosted>, slug: string): string {
	const run = hosted.get(slug);
	assert.ok(run !== undefined);
	assert.partialDeepStrictEqual(run.outcome, {
		status: "failed",
		stopReason: "unavailable",
		calls: { review: 0 },
	});
	assert.equal(run.outcome.candidate, undefined);
	assert.deepEqual(run.reviews, []);
	return run.outcome.error ?? "";
}

/** A program that tells the review, as facts, what it read from `ctx`. */
const READS_CONTEXT = `export default {
	meta: {},
	run: async (ctx) =>
		ctx.review({
			facts: {
				kind: ctx.kind,
				title: ctx.work.title ?? "none",
				type: ctx.cause.type,
				signal: ctx.cause.signal ?? "none",
				reviewer: ctx.cause.review?.reviewer ?? "none",
				state: JSON.stringify(ctx.cause.state),
				role: ctx.subject.role,
				practice: ctx.practice.slug + "@" + ctx.practice.revisionId,
				scope: ctx.scope.key + " " + ctx.scope.mode,
				previous:
					ctx.previous === null
						? "none"
						: ctx.previous.practiceResultId + " " + ctx.previous.outcome + "/" + (ctx.previous.severity ?? "-"),
				abortSignal: ctx.abortSignal instanceof AbortSignal,
				files:
					ctx.files === null
						? "none"
						: ctx.files.head.revision.slice(0, 7) +
							(ctx.files.base === null ? " no base " : " base ") +
							(ctx.files.change === null ? "no change" : [...ctx.files.change.files.keys()].join(",")),
				retired: ["change", "changeDir", "repo", "context", "tools", "remaining", "metadata", "signal"]
					.filter((key) => key in ctx)
					.join(",") || "none",
			},
		}),
};
`;

void describe("what a program reads about its review", () => {
	void test("a pull request review gives the kind, the record, the cause, the subject, the scope, the previous result and the change", async () => {
		const checkout = await mkdtemp(path.join(tmpdir(), "practice-script-repo-"));
		roots.push(checkout);
		const hosted = await host(
			{ reads: READS_CONTEXT },
			{
				cause: pullRequestCause({
					type: "moment",
					signal: "scm.pull_request.reviewed",
					state: { draftStatus: "NOT_DRAFT", state: "OPEN" },
					review: { id: 5, reviewer: "ada" },
				}),
				previous: previousResult({ cause: "requested", outcome: "NOT_MET", severity: "MAJOR" }),
			},
			metadata,
			{ path: checkout, diff: CHANGE },
		);
		const { review } = only(hosted, "reads");
		assert.deepEqual(review.options.facts, {
			kind: "scm.pull_request",
			title: "Count the items in the cart",
			type: "moment",
			signal: "scm.pull_request.reviewed",
			reviewer: "ada",
			state: '{"draftStatus":"NOT_DRAFT","state":"OPEN"}',
			role: "AUTHOR",
			practice: "reads@3",
			scope: "scm.pull_request:7:AUTHOR change",
			previous: "5d2f7a8e-0c1b-4f3a-9e6d-2b8c4a1f0e77 NOT_MET/MAJOR",
			abortSignal: true,
			files: "aaaaaaa no base src/cart.ts",
			retired: "none",
		});
	});

	void test("an issue review gives the issue's record and no files", async () => {
		const hosted = await host(
			{ reads: READS_CONTEXT },
			{
				cause: {
					kind: "scm.issue",
					subject: { role: "AUTHOR" },
					...BOUND,
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
			{ file: "metadata.json", value: issueRecord({ title: "Cart total is wrong" }) },
		);
		const { review } = only(hosted, "reads");
		assert.partialDeepStrictEqual(review.options.facts, {
			kind: "scm.issue",
			title: "Cart total is wrong",
			type: "recheck",
			signal: "scm.issue.updated",
			previous: "none",
			files: "none",
		});
	});

	void test("inputs that do not parse fail each program as unavailable, with the file that holds the error", async () => {
		const program = "export default { meta: {}, run: async (ctx) => ctx.review() };\n";

		const scheduled = await host(
			{ first: program, second: program },
			{ cause: pullRequestCause({ type: "scheduled" }), previous: null },
			metadata,
		);
		for (const slug of ["first", "second"]) {
			assert.match(
				failed(scheduled, slug),
				new RegExp(
					`^the review's inputs are not valid: ${slug}\\.cause\\.json: cause\\.type `,
					"u",
				),
			);
		}

		// A cause in the form before the practice revision and the scope.
		const unbound = await host(
			{ first: program },
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
		assert.match(
			failed(unbound, "first"),
			/^the review's inputs are not valid: first\.cause\.json: practice /u,
		);

		// A previous result in the form before it named the result it is.
		const unnamed = await host(
			{ first: program },
			{
				cause: pullRequestCause({ type: "requested", origin: "MANUAL" }),
				previous: {
					reviewedAt: "2025-12-31T00:00:00Z",
					cause: "moment",
					outcome: "MET",
					citations: [],
				},
			},
			metadata,
		);
		assert.match(
			failed(unnamed, "first"),
			/^the review's inputs are not valid: first\.previous\.json: previous\.practiceResultId /u,
		);

		const missing = await host(
			{ first: program },
			{ cause: pullRequestCause({ type: "requested", origin: "MANUAL" }) },
			metadata,
		);
		assert.equal(
			failed(missing, "first"),
			"the review's inputs are not valid: first.previous.json was not staged",
		);

		const noRecord = await host(
			{ first: program },
			{ cause: pullRequestCause({ type: "requested", origin: "MANUAL" }), previous: null },
			{ file: "other.json", value: {} },
		);
		assert.equal(
			failed(noRecord, "first"),
			"the review's inputs are not valid: metadata.json was not staged",
		);
	});

	void test("a program that expects another kind of work fails, and does not report that it found nothing", async () => {
		const hosted = await host(
			{
				issues: `import { expectKind } from "../lib/practice-script.ts";
export default {
	meta: {},
	run: async (ctx) => {
		expectKind(ctx, "scm.issue");
		return ctx.review();
	},
};
`,
			},
			{ cause: pullRequestCause({ type: "requested", origin: "MANUAL" }), previous: null },
			metadata,
		);
		const run = hosted.get("issues");
		assert.ok(run !== undefined);
		assert.partialDeepStrictEqual(run.outcome, {
			status: "failed",
			stopReason: "error",
			error: "the script reads scm.issue, but this review is about scm.pull_request",
			calls: { review: 0 },
		});
		assert.equal(run.outcome.candidate, undefined);
		assert.deepEqual(run.reviews, []);
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

/** A program that cites the line of the earlier review and the line added since, and reads the change since. */
const CITES_LINES = `export default {
	meta: {},
	run: async (ctx) =>
		ctx.review({
			leads: [
				{ at: { change: "cart.ts", line: 2 }, label: "A line that the change adds." },
				{ at: { change: "cart.ts", line: 3 }, label: "A line that the change adds." },
			],
			facts: {
				changeSince:
					ctx.previous?.changeSince === undefined ? "unknown" : [...ctx.previous.changeSince.keys()].join(","),
			},
		}),
};
`;

const synchronized = pullRequestCause({ type: "moment", signal: "scm.pull_request.synchronized" });

void describe("leads on lines added since the last review", () => {
	void test("each lead on a line added since the previous head is marked (new), and the section counts them", async () => {
		const repo = await history();
		const hosted = await host(
			{ cites: CITES_LINES },
			{
				cause: synchronized,
				previous: previousResult({
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
				}),
			},
			{ file: "metadata.json", value: { ...pullRequest, commit_sha: repo.head } },
			{ path: repo.root, diff: repo.diff },
		);
		const { review } = only(hosted, "cites");
		assert.deepEqual(review.options.facts, { changeSince: "cart.ts" });
		assert.partialDeepStrictEqual(review.leads, [
			{ quote: "old" },
			{ quote: "new", newSinceLastReview: true },
		]);
		assert.equal("newSinceLastReview" in (review.leads[0] ?? {}), false);
		assert.equal(
			review.section,
			[
				"**Leads:**",
				"1 of 2 leads point at lines added since the last review of this practice, each marked (new).",
				"- A line that the change adds.",
				"  - at `cart.ts` [L2]: `old`",
				"  - at `cart.ts` [L3] (new): `new`",
				"",
				"**Facts:**",
				"- changeSince = cart.ts",
				"",
			].join("\n"),
		);
	});

	void test("after a force push nothing is marked and the change since is absent, never empty", async () => {
		const repo = await history();
		const hosted = await host(
			{ cites: CITES_LINES },
			{
				cause: synchronized,
				previous: previousResult({ outcome: "MET", head: repo.rewritten }),
			},
			{ file: "metadata.json", value: { ...pullRequest, commit_sha: repo.head } },
			{ path: repo.root, diff: repo.diff },
		);
		const { review } = only(hosted, "cites");
		assert.deepEqual(review.options.facts, { changeSince: "unknown" });
		assert.ok(review.leads.every((lead) => lead.newSinceLastReview === undefined));
		assert.doesNotMatch(review.section, /\(new\)|since the last review/u);
	});
});

/**
 * The example of `docs/admin/practice-scripts.mdx` § Different work for each cause, in the result
 * contract: one program that asks a different review on a request, a recheck, and each moment.
 */
const BY_CAUSE = `import {
	addedComments,
	definePracticeScript,
	expectKind,
	requireChange,
	type DiffFile,
	type ReviewLead,
} from "../lib/practice-script.ts";

const OPEN_WORK = /\\b(?:TODO|FIXME)\\b/u;
const TRACKING = /#\\d+|\\b[A-Z][A-Z0-9]+-\\d+\\b|https?:\\/\\/\\S+/u;

export default definePracticeScript({
	meta: {},
	async run(ctx) {
		expectKind(ctx, "scm.pull_request");
		const todos = addedComments(requireChange(ctx.files)).filter(
			(comment) => OPEN_WORK.test(comment.text) && !TRACKING.test(comment.text),
		);
		// The leads in the files of \`only\`, or in the whole change.
		const leads = (only?: ReadonlyMap<string, DiffFile>): ReviewLead[] =>
			todos
				.filter((comment) => only === undefined || only.has(comment.at.change))
				.map((comment) => ({ at: comment.at, label: "A TODO that the change adds, with no tracking reference." }));

		const { cause } = ctx;
		if (cause.type === "requested") {
			return ctx.review({
				leads: leads(),
				directions: ["A person asked for this review. Judge the work as it is now."],
			});
		}
		if (cause.type === "recheck") {
			return ctx.review({
				leads: leads(),
				directions: ["The last review found untracked TODOs. Check if the new commits track them."],
			});
		}
		if (cause.signal === "scm.pull_request.synchronized") {
			// Only the files that the new commits changed, when that change is known.
			return ctx.review({ leads: leads(ctx.previous?.changeSince) });
		}
		if (cause.signal === "scm.pull_request.merged") {
			return ctx.review({
				leads: leads(),
				directions: ["The work is merged. Each untracked TODO is now on the target branch."],
			});
		}
		return ctx.review({ leads: leads() });
	},
});
`;

/** The changed file that each lead of a review cites. */
function cited(review: Asked): string[] {
	return review.leads.map((lead) => {
		assert.ok(isJsonObject(lead.at) && "change" in lead.at);
		return lead.at.change;
	});
}

void describe("one program that reads its cause", () => {
	void test("the documented example asks a different review on a request, a push and a merge", async () => {
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
		const record = { file: "metadata.json", value: { ...pullRequest, commit_sha: head } };
		const repo = { path: root, diff: git("diff", base, head) };
		const earlier = previousResult({
			signal: "scm.pull_request.opened",
			outcome: "NOT_MET",
			severity: "MINOR",
			head: previous,
		});

		const pushed = await host(
			{ "by-cause": BY_CAUSE },
			{ cause: synchronized, previous: earlier },
			record,
			repo,
		);
		assert.deepEqual(cited(only(pushed, "by-cause").review), ["total.ts"]);

		const requested = only(
			await host(
				{ "by-cause": BY_CAUSE },
				{ cause: pullRequestCause({ type: "requested", origin: "MANUAL" }), previous: earlier },
				record,
				repo,
			),
			"by-cause",
		).review;
		assert.deepEqual(cited(requested).toSorted(), ["cart.ts", "total.ts"]);
		assert.match(requested.section, /^- A person asked for this review\./u);

		const merged = only(
			await host(
				{ "by-cause": BY_CAUSE },
				{
					cause: pullRequestCause({ type: "moment", signal: "scm.pull_request.merged" }),
					previous: null,
				},
				record,
				repo,
			),
			"by-cause",
		).review;
		assert.deepEqual(cited(merged).toSorted(), ["cart.ts", "total.ts"]);
		assert.match(merged.section, /^- The work is merged\./u);
	});
});
