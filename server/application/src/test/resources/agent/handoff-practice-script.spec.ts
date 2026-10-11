import assert from "node:assert/strict";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import test from "node:test";

import { runBundled } from "../../../../../../docker/agents/practice-scripts/run-bundled.ts";

/**
 * A context folder with the commit record where the script reads it, `commits.json`, and a run of the
 * script on a pull request with it.
 */
function stage(commits: { sha: string; message: string }[]) {
	const root = mkdtempSync(path.join(tmpdir(), "handoff-practice-script-"));
	const contextDir = path.join(root, "context");
	mkdirSync(contextDir, { recursive: true });
	writeFileSync(
		path.join(contextDir, "commits.json"),
		JSON.stringify({
			commits: commits.map((commit) => ({ ...commit, parents: ["p"], files: [] })),
		}),
	);
	return {
		root,
		script: async (work: Record<string, unknown>) =>
			runBundled("ready-and-traceable-handoff", {
				repo: path.join(root, "repo"),
				work,
				contextDir,
			}),
	};
}

const metadata = {
	pr_number: 1,
	pr_url: "https://example.org/team/project/pull/1",
	repository_full_name: "team/project",
	source_branch: "42-quiz-flow",
	target_branch: "main",
	body: "Closes #42\n- [x] Tested locally\nOpen the quiz and finish ten questions.",
};

void test("a testing checklist does not turn a traceable handoff into a test-absence claim", async () => {
	const { root, script } = stage([]);
	try {
		const result = await script(metadata);
		assert.equal(result.facts?.issueMentionSyntaxCandidateCount, 1);
		assert.match(result.directions?.[0] ?? "", /#42/u);
		// The checklist is counted as written, so a tick is a fact rather than a guess.
		assert.equal(result.facts.checklistTicked, 1);
		assert.equal(result.facts.checklistUnticked, 0);
		// The counts claim nothing about whose lines they are; the direction says so.
		assert.match(
			(result.directions ?? []).join("\n"),
			/neither whose lines they are nor what was done/u,
		);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});

void test("a reference in a commit message of the change counts, read from the staged commit record", async () => {
	const { root, script } = stage([{ sha: "a".repeat(40), message: "Fix quiz flow\n\nRefs #7" }]);
	try {
		const result = await script({
			...metadata,
			body: "No mention here.",
			source_branch: "quiz-flow",
		});
		assert.equal(result.facts?.issueMentionSyntaxCandidateCount, 1);
		assert.equal(result.facts.commitCount, 1);
		assert.match(result.directions?.[0] ?? "", /#7/u);
	} finally {
		rmSync(root, { recursive: true, force: true });
	}
});
