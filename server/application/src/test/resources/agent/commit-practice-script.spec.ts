import assert from "node:assert/strict";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

import {
	readCapturedCommits,
	readCommits,
	type ChangedFile,
} from "../../../../../../docker/agents/practice-scripts/lib/change.ts";
import { subjectFacts } from "../../../../../../docker/agents/practice-scripts/lib/commit-subjects.ts";
import { runBundled } from "../../../../../../docker/agents/practice-scripts/run-bundled.ts";

void test("captured commits distinguish empty history from missing or malformed records", async () => {
	const root = mkdtempSync(path.join(tmpdir(), "captured-commits-"));
	try {
		assert.equal(await readCapturedCommits(root), null);
		for (const source of ["{", '{"commits":[null]}', '{"commits":{}}', '{"commits":[{}]}']) {
			writeFileSync(path.join(root, "commits.json"), source);
			assert.equal(await readCapturedCommits(root), null);
			assert.deepEqual(await readCommits(root), []);
		}
		writeFileSync(path.join(root, "commits.json"), '{"commits":[]}');
		assert.deepEqual(await readCapturedCommits(root), []);
		writeFileSync(
			path.join(root, "commits.json"),
			JSON.stringify({
				commits: [{ sha: "1234567", message: "", parents: [], files: [], authoredAt: "" }],
			}),
		);
		const captured = await readCapturedCommits(root);
		assert.equal(captured?.[0]?.authoredAt, "");
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

const commit = (sha: string, message: string, parents = ["p"], files: ChangedFile[] = []) => ({
	sha,
	message,
	author: "a",
	authoredAt: "",
	committer: "a",
	committedAt: "",
	parents,
	files,
	line: 0,
});

void test("a subject's shape is a fact — bare, repeated, listed, cut off — and a merge is set aside", () => {
	const facts = subjectFacts([
		commit("1111111", "Add button to start run\n"),
		commit("2222222", "fix\n"),
		commit("3333333", "Add button to start run\n"),
		commit("4444444", "add location manager, SwiftData persistence, and UI color cleanup\n"),
		commit("5555555", "Add support for\n"),
		commit("6666666", "Merge branch 'main' into feature\n", ["p1", "p2"]),
		commit(
			"7777777",
			"Refactor ProfileViewModel\n\n- remove stored context\n- add logging\n- add enum\n",
		),
		commit("8888888", "update readme\n"),
		commit("9999999", "Show loading indicator. Implement feedback from review\n"),
	]);
	const by = Object.fromEntries(facts.map((f) => [f.sha, f]));
	assert.deepEqual(
		[by["1111111"]?.bare, by["2222222"]?.bare, by["8888888"]?.bare],
		[false, true, false],
	);
	// commits.json promises no order: both commits carrying the subject are repeats.
	assert.deepEqual(
		[by["1111111"]?.repeat, by["3333333"]?.repeat, by["2222222"]?.repeat],
		[true, true, false],
	);
	assert.equal(by["4444444"]?.joinedClauses, true);
	assert.equal(by["7777777"]?.joinedClauses, true);
	assert.equal(by["1111111"]?.joinedClauses, false);
	// Two sentences in one subject are two clauses joined by a full stop.
	assert.equal(by["9999999"]?.joinedClauses, true);
	assert.equal(by["5555555"]?.cutOff, true);
	assert.equal(by["6666666"]?.merge, true);
});

/** A context folder whose `commits.json` holds `commits`, and a run of the script on it. */
function stage(slug: string, commits: unknown[]) {
	const contextDir = mkdtempSync(path.join(tmpdir(), "commit-practice-script-"));
	writeFileSync(path.join(contextDir, "commits.json"), JSON.stringify({ commits }));
	return {
		root: contextDir,
		contextDir,
		script: async () => runBundled(slug, { contextDir, contextReference: "areas/changed-work" }),
	};
}

void test("both commit practices read the subjects from the commit record and state shapes, not verdicts", async () => {
	const commits = [
		commit("1111111", "Add button to start run\n"),
		commit("2222222", "add location manager, SwiftData persistence, and UI color cleanup\n"),
		commit("3333333", "Merge branch 'main'\n", ["p1", "p2"]),
	];
	for (const slug of ["commit-subjects-explain-each-change", "commits-are-atomic-and-cohesive"]) {
		const { root, script } = stage(slug, commits);
		try {
			const result = await script();
			assert.equal(result.facts?.authoredCommits, 2);
			assert.equal(result.facts.mergeCommits, 1);
			assert.match(
				result.directions?.[0] ?? "",
				/2 authored commit\(s\), 1 merge commit\(s\) excluded/u,
			);
			// One lead per authored commit, named by its sha; the merge is set aside.
			assert.deepEqual(
				result.leads.map((lead) => [lead.at, lead.kind, lead.facts?.sha]),
				[
					[{ record: "commits.json" }, "commit", "1111111"],
					[{ record: "commits.json" }, "commit", "2222222"],
				],
			);
		} finally {
			rmSync(root, { recursive: true, force: true });
		}
	}
});

void test("a terse subject that names its object is not a shape to check, whatever files its commit touched", async () => {
	const image: ChangedFile = { status: "M", path: "docs/screenshot.png" };
	const { root, script } = stage("commit-subjects-explain-each-change", [
		commit("1111111", "Update Readme\n", ["p"], [image]),
		commit("2222222", "update\n", ["p"], [image]),
	]);
	try {
		const result = await script();
		assert.deepEqual(
			result.leads.map((lead) => [lead.facts?.sha, lead.facts?.bare]),
			[
				["1111111", false],
				["2222222", true],
			],
		);
		assert.equal("paths" in (result.leads[0]?.facts ?? {}), false);
		assert.doesNotMatch(result.directions?.[1] ?? "", /about the files it touched/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("subject leads keep informative conjunctions and vague subjects separate from commit scope", async () => {
	const { root, script } = stage("commit-subjects-explain-each-change", [
		commit(
			"1111111",
			"Restore request failures and remove the HTTP status check",
			["p"],
			[{ status: "M", path: "App/Model.swift", additions: 2, deletions: 8 }],
		),
		commit("2222222", "fix"),
		commit("3333333", "Merge branch 'main'", ["p1", "p2"]),
	]);
	try {
		const result = await script();
		assert.equal(result.facts?.authoredCommits, 2);
		assert.equal(result.facts.mergeCommits, 1);
		assert.deepEqual(
			result.leads.map((lead) => lead.facts),
			[
				{ sha: "1111111", bare: false, repeat: false, cutOff: false },
				{ sha: "2222222", bare: true, repeat: false, cutOff: false },
			],
		);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("one authored commit is still a history to judge, and an unread commit record is a collection gap", async () => {
	for (const slug of ["commit-subjects-explain-each-change", "commits-are-atomic-and-cohesive"]) {
		const { root, script, contextDir } = stage(slug, [
			commit("1111111", "Add the quiz view and reformat the project\n"),
		]);
		try {
			const single = await script();
			assert.equal(single.facts?.authoredCommits, 1);
			assert.equal(single.leads.length, 1);
			assert.match(single.directions?.[0] ?? "", /^1 authored commit\(s\), one lead each/u);
			assert.doesNotMatch((single.directions ?? []).join(" "), /no partition|nothing to judge/u);
			for (const source of [
				"{",
				'{"commits":[null]}',
				'{"commits":[{}]}',
				'{"commits":[{"sha":"1234567","message":"","parents":"missing","files":[]}]}',
				'{"commits":[{"sha":"1234567","message":"","parents":[],"files":[{}]}]}',
			]) {
				writeFileSync(path.join(contextDir, "commits.json"), source);
				const unread = await script();
				assert.deepEqual(unread.leads, []);
				assert.equal(unread.facts, undefined);
				assert.match(unread.directions?.[0] ?? "", /missing or malformed: a collection gap/u);
			}
			writeFileSync(path.join(contextDir, "commits.json"), '{"commits":[]}');
			const empty = await script();
			assert.match(empty.directions?.[0] ?? "", /^No authored commit in the reviewed range/u);
			assert.equal(empty.facts?.authoredCommits, 0);
			assert.equal(empty.facts.mergeCommits, 0);
			writeFileSync(
				path.join(contextDir, "commits.json"),
				JSON.stringify({ commits: [commit("3333333", "Merge branch 'main'", ["p1", "p2"])] }),
			);
			const merges = await script();
			assert.deepEqual(merges.leads, []);
			assert.equal(merges.facts?.authoredCommits, 0);
			assert.equal(merges.facts.mergeCommits, 1);
		} finally {
			rmSync(root, { recursive: true, force: true });
		}
	}
});

const renamed = (name: string) => ({
	status: "R",
	oldPath: `IntrocourseApp/${name}`,
	path: `PillApp/${name}`,
	additions: 0,
	deletions: 0,
});

void test("a commit lead lists the edited files with their line counts before the moves, and names no kinds", async () => {
	const commits = [
		// A README subject whose commit also moves the source tree and adds a view: only the edited files
		// and their counts show the second piece of work, so the moves must not push them out of view.
		commit(
			"1111111",
			"Create problem statement\n",
			["p"],
			[
				...["Assets.xcassets/Contents.json", "Info.plist", "ContentView.swift"].map(renamed),
				...["AccentColor.colorset/Contents.json", "AppIcon.appiconset/Contents.json"].map(renamed),
				renamed("PillApp.swift"),
				{ status: "A", path: "PillApp/WelcomeView.swift", additions: 18, deletions: 0 },
				{ status: "M", path: "README.md", additions: 20, deletions: 5 },
			],
		),
		// A feature and its package entry: two top-level locations, one piece of work.
		commit(
			"2222222",
			"Add order history, and adjust invoice loading\n",
			["p"],
			[
				{ status: "M", path: "Orders/HistoryViewModel.swift", additions: 40, deletions: 2 },
				{ status: "M", path: "project.yml", additions: 3, deletions: 0 },
				{
					status: "R",
					oldPath: "Orders/InvoiceResponse.swift",
					path: "Orders/InvoiceListResponse.swift",
					additions: 3,
					deletions: 3,
				},
			],
		),
	];
	const { root, script } = stage("commits-are-atomic-and-cohesive", commits);
	try {
		const result = await script();
		const [readme, feature] = result.leads.map((lead) => lead.facts ?? {});
		assert.ok(readme && feature);
		assert.equal(readme.moved, 6);
		assert.match(
			String(readme.paths),
			/^PillApp\/WelcomeView\.swift \+18\/-0, README\.md \+20\/-5, PillApp\//u,
		);
		// A rename that edits lines is not a move.
		assert.equal(feature.moved, 0);
		assert.equal(feature.joinedClauses, true);
		assert.equal(
			feature.paths,
			"Orders/HistoryViewModel.swift +40/-2, project.yml +3/-0, Orders/InvoiceListResponse.swift +3/-3",
		);
		for (const lead of result.leads) {
			assert.equal("kinds" in (lead.facts ?? {}), false);
		}
		// The directions describe the record; none of them counts concerns or names a lapse.
		assert.doesNotMatch((result.directions ?? []).join(" "), /literally|concern|tangle|lapse/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});
