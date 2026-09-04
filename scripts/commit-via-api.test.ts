import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

import { changedPaths, commitInput } from "./commit-via-api.ts";

/**
 * Git hooks export GIT_DIR, GIT_INDEX_FILE and friends, which would point every command below at the
 * repository being pushed instead of at the fixture. Strip them so the fixtures are self-contained
 * however these tests are invoked.
 */
const cleanGitEnv = (): NodeJS.ProcessEnv =>
	Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith("GIT_")));

type Git = (...args: string[]) => string;

/** A throwaway repo holding one tracked file per shape of change these tests make. */
function repoWithCommittedFiles(): { repo: string; git: Git; status: () => string } {
	const repo = mkdtempSync(join(tmpdir(), "commit-via-api-"));
	const git: Git = (...args) =>
		execFileSync("git", args, {
			cwd: repo,
			encoding: "utf8",
			stdio: ["ignore", "pipe", "pipe"],
			env: cleanGitEnv(),
		});
	git("init", "--quiet", "--initial-branch=main");
	git("config", "user.email", "test@example.invalid");
	git("config", "user.name", "Test");
	for (const path of ["kept.txt", "edited.txt", "removed.txt", "moved.txt"])
		writeFileSync(join(repo, path), `${path}\n`);
	git("add", "-A");
	git("commit", "--quiet", "-m", "initial");
	return {
		repo,
		git,
		status: () => git("status", "--porcelain=v1", "-z", "--untracked-files=all"),
	};
}

await test("enumerates every added, modified and deleted path in the work tree", (t) => {
	const { repo, status } = repoWithCommittedFiles();
	t.after(() => rmSync(repo, { recursive: true, force: true }));
	writeFileSync(join(repo, "edited.txt"), "edited\n");
	rmSync(join(repo, "removed.txt"));
	mkdirSync(join(repo, "generated"));
	writeFileSync(join(repo, "generated/added.txt"), "added\n");

	// An untracked directory is enumerated file by file: the mutation commits contents, not trees.
	assert.deepEqual(changedPaths(status()), {
		additions: ["edited.txt", "generated/added.txt"],
		deletions: ["removed.txt"],
	});
});

await test("splits a rename into the source deleted and the destination added", (t) => {
	const { repo, git, status } = repoWithCommittedFiles();
	t.after(() => rmSync(repo, { recursive: true, force: true }));
	git("mv", "moved.txt", "arrived.txt");

	assert.deepEqual(changedPaths(status()), {
		additions: ["arrived.txt"],
		deletions: ["moved.txt"],
	});
});

await test("has nothing to commit from a clean work tree", (t) => {
	const { repo, status } = repoWithCommittedFiles();
	t.after(() => rmSync(repo, { recursive: true, force: true }));

	assert.deepEqual(changedPaths(status()), { additions: [], deletions: [] });
});

await test("refuses a work tree with a conflict in it", () => {
	assert.throws(() => changedPaths("UU merged.txt\0"), /merged\.txt is unmerged/);
});

await test("builds the mutation input, with file contents base64-encoded", () => {
	const input = commitInput(
		{
			repository: "hephaestus-build/Hephaestus",
			branch: "deploy-state",
			expectedHeadOid: "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c",
			headline: "chore(deploy): channels/staging.json -> v1.2.3",
		},
		new Map([["channels/staging.json", Buffer.from('{"release":"v1.2.3"}\n')]]),
		["channels/retired.json"],
	);

	assert.deepEqual(input, {
		branch: {
			repositoryNameWithOwner: "hephaestus-build/Hephaestus",
			branchName: "deploy-state",
		},
		expectedHeadOid: "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c",
		message: { headline: "chore(deploy): channels/staging.json -> v1.2.3" },
		fileChanges: {
			additions: [{ path: "channels/staging.json", contents: "eyJyZWxlYXNlIjoidjEuMi4zIn0K" }],
			deletions: [{ path: "channels/retired.json" }],
		},
	});
	assert.equal(
		Buffer.from(input.fileChanges.additions[0]?.contents ?? "", "base64").toString("utf8"),
		'{"release":"v1.2.3"}\n',
	);
});
