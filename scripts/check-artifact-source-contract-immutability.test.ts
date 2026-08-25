import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

const CHECKER = join(import.meta.dirname, "check-artifact-source-contract-immutability.ts");

/**
 * Git hooks export GIT_DIR, GIT_INDEX_FILE and friends, which would point every command below at the
 * repository being pushed instead of at the fixture. Strip them so the fixtures are self-contained
 * however these tests are invoked.
 */
const cleanGitEnv = (): NodeJS.ProcessEnv =>
	Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith("GIT_")));
const CONTRACTS = "server/application/src/main/resources/contracts/artifact-source";

type Git = (...args: string[]) => string;

/** A throwaway repo with one published contract version on `main`, and a branch checked out. */
function repoWithPublishedContract(): { repo: string; git: Git } {
	const repo = mkdtempSync(join(tmpdir(), "contract-immutability-"));
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
	mkdirSync(join(repo, CONTRACTS, "1.0.0"), { recursive: true });
	writeFileSync(join(repo, CONTRACTS, "1.0.0", "catalog.json"), '{"version":"1.0.0"}\n');
	git("add", "-A");
	git("commit", "--quiet", "-m", "publish 1.0.0");
	git("checkout", "--quiet", "-b", "feature");
	return { repo, git };
}

function runChecker(repo: string): string {
	// Two of these tests expect the checker to throw; without capturing stderr its stack trace
	// prints on every successful run.
	return execFileSync(process.execPath, [CHECKER], {
		cwd: repo,
		encoding: "utf8",
		stdio: ["ignore", "pipe", "pipe"],
		env: { ...cleanGitEnv(), CONTRACT_BASE_REF: "main", GITHUB_BASE_REF: "" },
	});
}

await test("passes and says what it verified when published contracts are untouched", (t) => {
	const { repo, git } = repoWithPublishedContract();
	t.after(() => rmSync(repo, { recursive: true, force: true }));
	writeFileSync(join(repo, "unrelated.txt"), "change something else\n");
	git("add", "-A");
	git("commit", "--quiet", "-m", "unrelated change");

	assert.match(runChecker(repo), /1 published version\(s\) unchanged/);
});

await test("fails when a published contract file is edited", (t) => {
	const { repo, git } = repoWithPublishedContract();
	t.after(() => rmSync(repo, { recursive: true, force: true }));
	writeFileSync(
		join(repo, CONTRACTS, "1.0.0", "catalog.json"),
		'{"version":"1.0.0","sneaked":true}\n',
	);
	git("add", "-A");
	git("commit", "--quiet", "-m", "mutate published contract");

	assert.throws(() => runChecker(repo), /1\.0\.0 is immutable/);
});

await test("fails when a published contract version is deleted", (t) => {
	const { repo, git } = repoWithPublishedContract();
	t.after(() => rmSync(repo, { recursive: true, force: true }));
	git("rm", "--quiet", "-r", join(CONTRACTS, "1.0.0"));
	git("commit", "--quiet", "-m", "delete published contract");

	assert.throws(() => runChecker(repo), /1\.0\.0 is immutable/);
});

await test("adding a new version alongside a published one is allowed", (t) => {
	const { repo, git } = repoWithPublishedContract();
	t.after(() => rmSync(repo, { recursive: true, force: true }));
	mkdirSync(join(repo, CONTRACTS, "1.1.0"), { recursive: true });
	writeFileSync(join(repo, CONTRACTS, "1.1.0", "catalog.json"), '{"version":"1.1.0"}\n');
	git("add", "-A");
	git("commit", "--quiet", "-m", "publish 1.1.0");

	assert.match(runChecker(repo), /1 published version\(s\) unchanged/);
});

await test("reports honestly when nothing is published at the merge base", (t) => {
	const repo = mkdtempSync(join(tmpdir(), "contract-immutability-empty-"));
	t.after(() => rmSync(repo, { recursive: true, force: true }));
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
	writeFileSync(join(repo, "readme.md"), "no contracts yet\n");
	git("add", "-A");
	git("commit", "--quiet", "-m", "initial");
	git("checkout", "--quiet", "-b", "feature");
	mkdirSync(join(repo, CONTRACTS, "1.0.0"), { recursive: true });
	writeFileSync(join(repo, CONTRACTS, "1.0.0", "catalog.json"), '{"version":"1.0.0"}\n');
	git("add", "-A");
	git("commit", "--quiet", "-m", "introduce the contract");

	// The introducing PR has nothing to protect yet, and the check must say so rather than print
	// nothing — silence is what let it pass vacuously from the wrong directory.
	assert.match(runChecker(repo), /no version is published/);
});

await test("runs from a subdirectory", (t) => {
	const { repo, git } = repoWithPublishedContract();
	t.after(() => rmSync(repo, { recursive: true, force: true }));
	writeFileSync(
		join(repo, CONTRACTS, "1.0.0", "catalog.json"),
		'{"version":"1.0.0","sneaked":true}\n',
	);
	git("add", "-A");
	git("commit", "--quiet", "-m", "mutate published contract");

	assert.throws(() => runChecker(join(repo, "server")), /1\.0\.0 is immutable/);
});
