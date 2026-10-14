import assert from "node:assert/strict";
import test from "node:test";

import { runBundled } from "../../../../../../docker/agents/practice-scripts/run-bundled.ts";

function commitsRecord(commits: { sha: string; message: string }[]) {
	return {
		"commits.json": {
			commits: commits.map((commit) => ({ ...commit, parents: ["p"], files: [] })),
		},
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
	const result = await runBundled("ready-and-traceable-handoff", {
		item: metadata,
		context: commitsRecord([]),
	});
	assert.equal(result.facts?.issueMentionSyntaxCandidateCount, 1);
	assert.match(result.directions?.[0] ?? "", /#42/u);
	assert.equal(result.facts.checklistTicked, 1);
	assert.equal(result.facts.checklistUnticked, 0);
	assert.match(
		(result.directions ?? []).join("\n"),
		/neither whose lines they are nor what was done/u,
	);
});

void test("a reference in a commit message of the change counts, read from the staged commit record", async () => {
	const result = await runBundled("ready-and-traceable-handoff", {
		item: { ...metadata, body: "No mention here.", source_branch: "quiz-flow" },
		context: commitsRecord([{ sha: "a".repeat(40), message: "Fix quiz flow\n\nRefs #7" }]),
	});
	assert.equal(result.facts?.issueMentionSyntaxCandidateCount, 1);
	assert.equal(result.facts.commitCount, 1);
	assert.match(result.directions?.[0] ?? "", /#7/u);
});
