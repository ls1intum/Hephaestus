import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import nodePath from "node:path";
import { test } from "node:test";

import {
	capturedLines,
	field,
	readJsonFile,
	runRunner,
	stagePractices,
} from "./program-host-runner.ts";

const HEAD = "a".repeat(40);
const TREE = "scm.repository.tree";
const CORE = "scm.pull-request.core";
const text = "class Auth {\n  insecure();\n}\n";

async function run(
	citation: Record<string, unknown>,
	absolute = false,
	extraTexts: { commit: string; content: string }[] = [],
	prepared = false,
) {
	const cwd = mkdtempSync(nodePath.join(tmpdir(), "pi-citation-"));
	stagePractices(cwd, [
		{
			slug: "test-practice",
			...(prepared
				? {
						program: `import { definePracticeScript } from "../lib/core/practice-script.ts";
export default definePracticeScript({
    meta: {},
    async run(ctx) { return ctx.review({ facts: { commitSubject: "Add authentication" } }); },
});
`,
					}
				: {}),
		},
	]);
	const write = (path: string, content: string) => {
		mkdirSync(nodePath.dirname(nodePath.join(cwd, path)), { recursive: true });
		writeFileSync(nodePath.join(cwd, path), content);
	};
	write("repos/primary/.git/HEAD", HEAD);
	write("repos/primary/src/Auth.java", text);
	write("evidence/auth.txt", text);
	write("evidence/message.txt", text);
	write(
		"work/change/diff.patch",
		"diff --git a/src/Auth.java b/src/Auth.java\n--- a/src/Auth.java\n+++ b/src/Auth.java\n@@ -10,0 +10,1 @@\n[L10] + insecure();\ndiff --git a/src/Other.java b/src/Other.java\n--- a/src/Other.java\n+++ b/src/Other.java\n@@ -20,0 +20,1 @@\n[L20] + insecure();\n",
	);
	write(
		"evidence/commits.json",
		JSON.stringify([{ sha: HEAD, message: "Add authentication" }], null, 2),
	);
	const manifest = {
		files: [
			{ kind: CORE, file: { path: "evidence/metadata.json" } },
			{ kind: "scm.pull-request.diff", file: { path: "evidence/change.json" } },
			{ kind: TREE, file: { path: "repos/primary/.git/HEAD" } },
			{ kind: TREE, file: { path: "repos/primary/.git/hephaestus-captured-refs" } },
			{ kind: CORE, file: { path: "evidence/commits.json" } },
		],
		sources: [
			{
				kind: CORE,
				state: { availability: "AVAILABLE" },
				citation: { side: "FORBIDDEN", commit: "FORBIDDEN" },
			},
			{
				kind: "scm.pull-request.diff",
				state: { availability: "AVAILABLE" },
				citation: { side: "REQUIRED", commit: "OPTIONAL" },
			},
			{
				kind: TREE,
				state: { availability: "AVAILABLE" },
				citation: { side: "FORBIDDEN", commit: "OPTIONAL" },
			},
		],
		citableTexts: [
			{
				source: CORE,
				path: "thread/message",
				side: "NEW",
				commit: null,
				stagedPath: "evidence/message.txt",
				sha256: createHash("sha256").update(text).digest("hex"),
			},
			{
				source: TREE,
				path: "src/Auth.java",
				side: "NEW",
				commit: HEAD,
				stagedPath: "evidence/auth.txt",
				sha256: createHash("sha256").update(text).digest("hex"),
			},
			...extraTexts.map(({ commit, content }) => {
				write(`evidence/auth-${commit}.txt`, content);
				return {
					source: TREE,
					path: "src/Auth.java",
					side: "NEW",
					commit,
					stagedPath: `evidence/auth-${commit}.txt`,
					sha256: createHash("sha256").update(content).digest("hex"),
				};
			}),
		],
	};
	write("evidence/manifest.json", JSON.stringify(manifest));
	const candidate = {
		outcome: "NOT_MET",
		summary: "Authentication call is insecure",
		explanation: "The call uses insecure().",
		citations: [],
		evidence: {},
		problems: [
			{
				severity: "MAJOR",
				summary: "Authentication call is insecure",
				explanation: "The call uses insecure().",
				citations: [
					{ ...citation, ...(absolute ? { path: nodePath.join(cwd, String(citation.path)) } : {}) },
				],
				basis: {
					type: "duty",
					event: 0,
					criterionQuote: "Added authentication calls use checked(), not insecure().",
					explanation: "The cited authentication call does not use checked().",
				},
			},
		],
	};
	try {
		const exit = await runRunner(cwd, {}, { PI_HOST_SESSION_CANDIDATE: JSON.stringify(candidate) });
		const results = existsSync(nodePath.join(cwd, "out/result.json"))
			? field(readJsonFile(cwd, "out/result.json"), "results")
			: [];
		return {
			...exit,
			results,
			replies: capturedLines(cwd, "submitted"),
		};
	} finally {
		rmSync(cwd, { recursive: true, force: true });
	}
}

for (const [name, citation, absolute] of [
	[
		"repository-relative file",
		{ path: "src/Auth.java", startLine: 2, quote: "insecure();" },
		false,
	],
	[
		"checkout path",
		{ path: "repos/primary/src/Auth.java", startLine: 2, quote: "insecure();" },
		false,
	],
	[
		"absolute sandbox path",
		{ path: "repos/primary/src/Auth.java", startLine: 2, quote: "insecure();" },
		true,
	],
	[
		"repository file with the live HEAD alias",
		{
			sourceKind: TREE,
			sourcePath: ".git/HEAD",
			path: "src/Auth.java",
			startLine: 2,
			quote: "insecure();",
		},
		false,
	],
	[
		"captured plaintext file",
		{ path: "evidence/auth.txt", startLine: 2, quote: "insecure();" },
		false,
	],
	["captured logical text", { path: "thread/message", startLine: 2, quote: "insecure();" }, false],
	[
		"captured plaintext record",
		{ path: "evidence/message.txt", startLine: 2, quote: "insecure();" },
		false,
	],
	["captured record", { path: "evidence/metadata.json", startLine: 1, quote: "Add login" }, false],
	[
		"record source with a short path",
		{
			sourcePath: "evidence/metadata.json",
			path: "metadata.json",
			startLine: 1,
			quote: "Add login",
		},
		false,
	],
	[
		"diff source and physical line",
		{
			sourcePath: "work/change/diff.patch",
			path: "src/Auth.java",
			startLine: 5,
			quote: "+ insecure();",
		},
		false,
	],
	[
		"diff view alone",
		{ path: "work/change/diff.patch", startLine: 5, quote: "+ insecure();" },
		false,
	],
] satisfies [string, Record<string, unknown>, boolean][]) {
	void test(`submit_result accepts the ${name} without internal source bookkeeping`, async () => {
		const result = await run(citation, absolute);
		assert.equal(result.status, 0, result.stderr);
		assert.ok(Array.isArray(result.results) && result.results.length === 1, result.stderr);
		assert.match(JSON.stringify(result.replies), /stored/u);
		const first: unknown = result.results[0];
		if (name.includes("file") || name.includes("checkout") || name.includes("absolute")) {
			assert.partialDeepStrictEqual(first, {
				candidate: {
					problems: [
						{
							citations: [
								{
									sourceKind: TREE,
									sourcePath: "repos/primary/.git/HEAD",
									path: "src/Auth.java",
									commit: HEAD,
									startLine: 2,
									endLine: 2,
									quote: "insecure();",
								},
							],
						},
					],
				},
			});
		}
	});
}

void test("the live Git-log-as-HEAD shape is refused with an exact captured commit citation", async () => {
	// The live call cited three lines of git log as .git/HEAD. Keep its shape, with synthetic text and SHA.
	const result = await run({
		sourceKind: TREE,
		sourcePath: ".git/HEAD",
		path: ".git/HEAD",
		commit: HEAD,
		startLine: 1,
		endLine: 3,
		quote: `commit ${HEAD}\n    Add authentication\n    Add checked helper`,
	});
	assert.match(result.stderr, /A Git log is not the content of \.git\/HEAD/u);
	assert.match(result.stderr, /Corrected citation example:/u);
	assert.match(result.stderr, /commits\.json/u);
	const example = /Corrected citation example: (?<example>\{[^\n]*?\})\./u.exec(result.stderr)
		?.groups?.example;
	assert.ok(example !== undefined, result.stderr);
	const corrected: unknown = JSON.parse(example);
	assert.ok(typeof corrected === "object" && corrected !== null);
	assert.deepEqual(corrected, { path: "evidence/commits.json", startLine: 3, quote: HEAD });
	const accepted = await run({ ...corrected });
	assert.equal(accepted.status, 0, accepted.stderr);
	assert.ok(Array.isArray(accepted.results) && accepted.results.length === 1, accepted.stderr);
});

void test("generated practice input redirects citations to captured evidence without admitting it", async () => {
	const result = await run(
		{
			path: "work/review-input/test-practice.json",
			startLine: 3,
			quote: '"commitSubject": "Add authentication"',
		},
		false,
		[],
		true,
	);
	assert.match(result.stderr, /generated practice preparation, not captured evidence/u);
	assert.match(result.stderr, /read\(\{"path":"evidence\/manifest\.json"\}\)/u);
	assert.doesNotMatch(result.stderr, /no such file at commit/u);
	assert.deepEqual(result.results, []);

	const captured = await run(
		{
			path: "evidence/commits.json",
			startLine: 4,
			quote: '"message": "Add authentication"',
		},
		false,
		[],
		true,
	);
	assert.equal(captured.status, 0, captured.stderr);
	assert.ok(Array.isArray(captured.results) && captured.results.length === 1, captured.stderr);
	assert.match(JSON.stringify(captured.replies), /stored/u);
});

void test("an absolute generated-input citation receives the same captured-source correction", async () => {
	const result = await run(
		{
			path: "work/review-input/test-practice.json",
			startLine: 3,
			quote: '"commitSubject": "Add authentication"',
		},
		true,
		[],
		true,
	);
	assert.match(result.stderr, /generated practice preparation, not captured evidence/u);
	assert.match(result.stderr, /read\(\{"path":"evidence\/manifest\.json"\}\)/u);
	assert.doesNotMatch(result.stderr, /no such file at commit/u);
	assert.deepEqual(result.results, []);
});

void test("a missing repository file keeps the normal citation mismatch", async () => {
	const result = await run(
		{ path: "src/Missing.java", startLine: 1, quote: "missing();" },
		false,
		[],
		true,
	);
	assert.match(result.stderr, /citation does not match src\/Missing\.java/u);
	assert.match(result.stderr, /no such file at commit/u);
	assert.doesNotMatch(result.stderr, /generated practice preparation/u);
	assert.deepEqual(result.results, []);
});

void test("a wrong repository quotation is refused and its corrected citation verifies", async () => {
	const result = await run({ path: "src/Auth.java", startLine: 2, quote: "insecure(user);" });
	assert.match(result.stderr, /reads .*insecure/u);
	const example = /Corrected citation example: (?<example>\{[^\n]*?\})\./u.exec(result.stderr)
		?.groups?.example;
	assert.ok(example !== undefined, result.stderr);
	const corrected: unknown = JSON.parse(example);
	assert.ok(typeof corrected === "object" && corrected !== null);
	const accepted = await run({ ...corrected });
	assert.equal(accepted.status, 0, accepted.stderr);
	assert.ok(Array.isArray(accepted.results) && accepted.results.length === 1, accepted.stderr);
});

void test("a repeated diff quote resolves the physical line's file instead of choosing another occurrence", async () => {
	const result = await run({
		path: "work/change/diff.patch",
		startLine: 10,
		quote: "+ insecure();",
	});
	assert.equal(result.status, 0, result.stderr);
	assert.ok(Array.isArray(result.results) && result.results.length === 1, result.stderr);
	assert.partialDeepStrictEqual(result.results[0], {
		candidate: {
			problems: [
				{
					citations: [
						{
							sourceKind: "scm.pull-request.diff",
							sourcePath: "evidence/change.json",
							path: "src/Other.java",
							side: "NEW",
							startLine: 20,
							endLine: 20,
							quote: " insecure();",
						},
					],
				},
			],
		},
	});
});

void test("an absolute path outside the sandbox cannot provide evidence", async () => {
	const result = await run({ path: "/etc/passwd", startLine: 1, quote: "root" });
	assert.match(result.stderr, /outside the captured sandbox/u);
	assert.deepEqual(result.results, []);
});

for (const citation of [
	{ path: "src/Auth.java", startLine: 2, quote: "insecure();", side: "OLD" },
	{ path: "thread/message", startLine: 2, quote: "insecure();", side: "OLD" },
	{ path: "evidence/metadata.json", startLine: 1, quote: "Add login", commit: HEAD },
]) {
	void test(`a prohibited citation field on ${citation.path} gives a corrected example that verifies`, async () => {
		const result = await run(citation);
		assert.match(result.stderr, /forbidden by the source catalog/u);
		const example = /Corrected citation example: (?<example>\{[^\n]*?\})\./u.exec(result.stderr)
			?.groups?.example;
		assert.ok(example !== undefined, result.stderr);
		const corrected: unknown = JSON.parse(example);
		assert.ok(typeof corrected === "object" && corrected !== null);
		const accepted = await run({ ...corrected });
		assert.equal(accepted.status, 0, accepted.stderr);
		assert.ok(Array.isArray(accepted.results) && accepted.results.length === 1, accepted.stderr);
	});
}

void test("a file captured at several commits resolves an omitted commit to the reviewed head", async () => {
	const result = await run({ path: "src/Auth.java", startLine: 2, quote: "insecure();" }, false, [
		{ commit: "c".repeat(40), content: "class Auth {\n  legacy();\n}\n" },
	]);
	assert.equal(result.status, 0, result.stderr);
	assert.ok(Array.isArray(result.results) && result.results.length === 1, result.stderr);
	assert.match(JSON.stringify(result.replies), /stored/u);
});

void test("an explicit commit still selects an older captured version of the file", async () => {
	const older = "c".repeat(40);
	const result = await run(
		{ path: "src/Auth.java", startLine: 2, quote: "legacy();", commit: older },
		false,
		[{ commit: older, content: "class Auth {\n  legacy();\n}\n" }],
	);
	assert.equal(result.status, 0, result.stderr);
	assert.ok(Array.isArray(result.results) && result.results.length === 1, result.stderr);
});
