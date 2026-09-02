import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { describe, test } from "node:test";

import { needsDispatch, parseRuns, versionBranch } from "./dispatch-version-pr-ci.ts";

const SHA = "a".repeat(40);
const OTHER = "b".repeat(40);

void describe("the Version PR's CI branch", () => {
	void test("follows the base branch changesets is configured with", async () => {
		assert.equal(versionBranch({ baseBranch: "main" }), "changeset-release/main");
		assert.equal(versionBranch({ baseBranch: "release/2" }), "changeset-release/release/2");
		// The committed configuration, so renaming the base branch cannot silently leave the Version
		// PR unvalidated while every check still passes.
		const config: unknown = JSON.parse(await readFile(".changeset/config.json", "utf8"));
		assert.equal(versionBranch(config), "changeset-release/main");
	});

	void test("refuses a configuration that names no base branch", () => {
		assert.throws(() => versionBranch({}), /baseBranch must be a string/);
		assert.throws(() => versionBranch({ baseBranch: "" }), /declares no baseBranch/);
	});
});

void describe("dispatching CI for a Version PR head", () => {
	void test("dispatches a head commit no run has covered", () => {
		assert.equal(needsDispatch(SHA, []), true);
		assert.equal(needsDispatch(SHA, [{ headSha: OTHER }]), true);
	});

	void test("stays quiet when the head already has a run", () => {
		// A push to main that leaves the Version PR's tree unchanged must not queue a second run of
		// the same commit, and a re-run of this workflow must not either.
		assert.equal(needsDispatch(SHA, [{ headSha: OTHER }, { headSha: SHA }]), false);
	});

	void test("reads head commits out of the workflow runs API", () => {
		assert.deepEqual(parseRuns({ workflow_runs: [{ head_sha: SHA, id: 1 }] }), [{ headSha: SHA }]);
		assert.throws(() => parseRuns({}), /workflow_runs must be an array/);
		assert.throws(() => parseRuns({ workflow_runs: [{ id: 1 }] }), /head_sha must be a string/);
	});
});
