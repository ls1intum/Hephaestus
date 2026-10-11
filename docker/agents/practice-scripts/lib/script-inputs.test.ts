import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { after, describe, test } from "node:test";

import { parseDiff } from "./diff-parser.ts";
import { isJsonObject } from "./json.ts";
import { expectKind } from "./practice-script.ts";
import { addedSince, changeSince, parseInputs } from "./script-inputs.ts";

const roots: string[] = [];
after(async () =>
	Promise.all(roots.map(async (root) => rm(root, { recursive: true, force: true }))),
);

const parsedRecord: unknown = JSON.parse(
	await readFile(
		path.join(import.meta.dirname, "..", "fixtures", "pull-request-metadata.json"),
		"utf8",
	),
);
assert.ok(isJsonObject(parsedRecord));
const pullRequest = parsedRecord;

/** The cause file of a pull request review admitted under `cause`. */
function staged(cause: Record<string, unknown>) {
	return {
		kind: "scm.pull_request",
		subject: { role: "AUTHOR" },
		cause: { origin: "LIVE", state: {}, admittedAt: "2026-01-02T03:04:05.123456Z", ...cause },
	};
}

type Context = { kind: "scm.pull_request"; change: number } | { kind: "scm.issue" };
/** A context of an issue, typed as either kind. */
const issueContext = (): Context => ({ kind: "scm.issue" });

void describe("the inputs of a definition script", () => {
	void test("a pull request cause, its previous claim and its record parse into one typed value", () => {
		const parsed = parseInputs(
			"cites-tests",
			staged({
				type: "moment",
				signal: "scm.pull_request.synchronized",
				state: { draftStatus: "NOT_DRAFT", state: "OPEN" },
			}),
			{
				reviewedAt: "2026-01-01T00:00:00Z",
				cause: "moment",
				signal: "scm.pull_request.opened",
				outcome: "NOT_MET",
				severity: "MINOR",
				citations: [
					{
						sourceKind: "scm.pull-request.diff",
						path: "src/cart.ts",
						startLine: 2,
						endLine: 2,
						side: "NEW",
					},
				],
				head: "1".repeat(40),
			},
			{ ...pullRequest, unknownField: "dropped" },
		);
		assert.ok("inputs" in parsed, JSON.stringify(parsed));
		const { inputs } = parsed;
		assert.equal(inputs.kind, "scm.pull_request");
		assert.equal(inputs.cause.type, "moment");
		assert.equal(inputs.previous?.outcome, "NOT_MET");
		assert.equal(inputs.work.title, "Count the items in the cart");
		// A field the server does not write stays out of the record.
		assert.equal("unknownField" in inputs.work, false);
	});

	void test("each kind takes its own moments, review state and record", () => {
		const conversation = parseInputs(
			"answers-questions",
			{
				kind: "chat.conversation_thread",
				subject: { role: "AUTHOR" },
				cause: {
					type: "moment",
					signal: "chat.conversation_thread.settled",
					origin: "LIVE",
					state: {},
					admittedAt: "2026-01-01T00:00:00Z",
				},
			},
			null,
			{
				_meta: { trustLevel: "UNTRUSTED_EXTERNAL" },
				channel: "C1",
				threadTs: "1.0",
				messages: [{ ts: "1.0", author: "U1", text: "Why?", underReview: true }],
				messageCount: 1,
				truncated: false,
			},
		);
		assert.ok("inputs" in conversation, JSON.stringify(conversation));
		const document = parseInputs(
			"keeps-docs-current",
			{
				kind: "docs.document",
				subject: { role: "AUTHOR" },
				cause: {
					type: "requested",
					origin: "MANUAL",
					state: { state: "ACTIVE" },
					admittedAt: "2026-01-01T00:00:00Z",
				},
			},
			null,
			{
				title: "Runbook",
				collection: "Ops",
				collectionSlug: "ops",
				slug: "runbook",
				createdBy: "Ada",
				updatedBy: null,
				createdAt: null,
				updatedAt: null,
				archived: false,
			},
		);
		assert.ok("inputs" in document, JSON.stringify(document));
		// A pull request moment is not a moment of an issue.
		const issue = parseInputs(
			"links-work",
			{
				kind: "scm.issue",
				subject: { role: "AUTHOR" },
				cause: {
					type: "moment",
					signal: "scm.pull_request.merged",
					origin: "LIVE",
					state: {},
					admittedAt: "2026-01-01T00:00:00Z",
				},
			},
			null,
			{},
		);
		assert.ok("error" in issue);
		assert.match(issue.error, /^links-work\.cause\.json: cause\.signal /u);
	});

	void test("an input that does not parse names the file that holds it", () => {
		const cases: [unknown, unknown, unknown, RegExp][] = [
			[[], null, pullRequest, /^p\.cause\.json: must hold a JSON object$/u],
			[staged({ type: "scheduled" }), null, pullRequest, /^p\.cause\.json: cause\.type /u],
			// A request names no moment: the script cannot read one into it.
			[
				staged({ type: "requested", signal: "scm.pull_request.opened" }),
				null,
				pullRequest,
				/^p\.cause\.json: cause /u,
			],
			[
				staged({ type: "requested", state: { state: "ARCHIVED" } }),
				null,
				pullRequest,
				/^p\.cause\.json: cause\.state\.state /u,
			],
			[
				staged({ type: "requested" }),
				{
					reviewedAt: "2026-01-01T00:00:00Z",
					cause: "moment",
					outcome: "NOT_MET",
					citations: [],
					summary: "model text",
				},
				pullRequest,
				/^p\.previous\.json: previous /u,
			],
			[
				staged({ type: "requested" }),
				{
					reviewedAt: "2026-01-01T00:00:00Z",
					cause: "moment",
					outcome: "MET",
					citations: [],
					head: "main",
				},
				pullRequest,
				/^p\.previous\.json: previous\.head /u,
			],
			[
				staged({ type: "requested" }),
				null,
				{ title: "no number" },
				/^metadata\.json: work\.basis /u,
			],
			[staged({ type: "requested" }), null, undefined, /^metadata\.json: work /u],
		];
		for (const [cause, previous, work, message] of cases) {
			const parsed = parseInputs("p", cause, previous, work);
			assert.ok("error" in parsed, JSON.stringify(cause));
			assert.match(parsed.error, message);
		}
	});

	void test("expectKind narrows to the kind of the work, and a mismatch is an error, never an empty result", () => {
		const ctx = issueContext();
		assert.throws(() => {
			expectKind(ctx, "scm.pull_request");
		}, /^Error: the script reads scm\.pull_request, but this review is about scm\.issue$/u);
		expectKind(ctx, "scm.issue");
		assert.equal(ctx.kind, "scm.issue");
	});
});

/** Commits `files` on top of the checkout's head and returns the new commit id. */
async function commit(repo: string, files: Record<string, string>): Promise<string> {
	for (const [file, text] of Object.entries(files)) {
		await writeFile(path.join(repo, file), text);
	}
	const git = (...args: string[]) =>
		execFileSync(
			"git",
			["-C", repo, "-c", "user.name=t", "-c", "user.email=t@example.invalid", ...args],
			{ encoding: "utf8" },
		).trim();
	git("add", "-A");
	git("commit", "-q", "-m", "change");
	return git("rev-parse", "HEAD");
}

async function repository(): Promise<string> {
	const repo = await mkdtemp(path.join(tmpdir(), "practice-script-git-"));
	roots.push(repo);
	execFileSync("git", ["init", "-q", repo]);
	return repo;
}

void describe("the change since the previous review", () => {
	void test("it is the diff from the previous head when that head is an ancestor, and empty when nothing changed", async () => {
		const repo = await repository();
		const previous = await commit(repo, { "cart.ts": "a\nb\n" });
		const head = await commit(repo, { "cart.ts": "a\nnew\nb\n", "total.ts": "sum\n" });
		const since = await changeSince(repo, previous, head);
		assert.ok("change" in since, JSON.stringify(since));
		assert.deepEqual([...(since.change.get("cart.ts")?.addedLines ?? [])], [[2, "new"]]);
		assert.deepEqual([...(since.change.get("total.ts")?.addedLines ?? [])], [[1, "sum"]]);
		const same = await changeSince(repo, head, head);
		assert.ok("change" in same);
		assert.equal(same.change.size, 0);
	});

	void test("after a force push the previous head is not an ancestor, so the change is not known rather than empty", async () => {
		const repo = await repository();
		const base = await commit(repo, { "cart.ts": "a\n" });
		const rewritten = await commit(repo, { "cart.ts": "a\nold\n" });
		execFileSync("git", ["-C", repo, "reset", "-q", "--hard", base]);
		const head = await commit(repo, { "cart.ts": "a\nnew\n" });
		const since = await changeSince(repo, rewritten, head);
		assert.ok("unknown" in since);
		assert.match(since.unknown, /is not an ancestor of the reviewed head$/u);
		// A commit that the checkout does not hold proves nothing either.
		assert.ok("unknown" in (await changeSince(repo, "f".repeat(40), head)));
		assert.ok("unknown" in (await changeSince(repo, "--output=/tmp/x", head)));
	});

	void test("a lead is new when its range cites a line of the new side that the change since added", () => {
		const since = parseDiff(
			[
				"diff --git a/cart.ts b/cart.ts",
				"--- a/cart.ts",
				"+++ b/cart.ts",
				"@@ -1,2 +1,3 @@",
				" a",
				"+new",
				" b",
			].join("\n"),
		);
		assert.equal(addedSince({ change: "cart.ts", line: 2 }, since), true);
		assert.equal(addedSince({ change: "cart.ts", line: 1, endLine: 3 }, since), true);
		assert.equal(addedSince({ change: "cart.ts", line: 3 }, since), false);
		assert.equal(addedSince({ change: "cart.ts", line: 2, side: "OLD" }, since), false);
		assert.equal(addedSince({ change: "total.ts", line: 2 }, since), false);
		assert.equal(addedSince({ file: "cart.ts", line: 2 }, since), false);
		assert.equal(addedSince({ record: "comments.json", line: 2 }, since), false);
	});
});
