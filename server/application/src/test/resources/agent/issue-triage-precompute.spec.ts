import assert from "node:assert/strict";
import test from "node:test";

import triagesTheIssueWithLabelsAndOwnership from "../../../main/resources/practices/precompute/triages-the-issue-with-labels-and-ownership.ts";

void test("a native issue type supplies classification metadata without a label", () => {
	const result = triagesTheIssueWithLabelsAndOwnership("owner/repository", new Map(), {
		issue_type: "Bug",
		labels: [],
		assignees: [],
	});

	assert.equal(result.metrics.hasIssueType, 1);
	assert.equal(result.metrics.labelCount, 0);
	assert.equal(result.directions.length, 1);
	assert.match(result.directions[0] ?? "", /issueType="Bug"/);
});

void test("an unclassified issue reports the complete metadata gap", () => {
	const result = triagesTheIssueWithLabelsAndOwnership("owner/repository", new Map(), {});

	assert.equal(result.metrics.hasIssueType, 0);
	assert.equal(
		result.directions.at(-1),
		"No issue type, label, assignee, or milestone is present.",
	);
});
