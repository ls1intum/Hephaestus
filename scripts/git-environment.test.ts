/**
 * The repository being pushed has to survive the gate the push runs. Git hands every hook the
 * location of that repository, and anything below the hook that lets those variables through
 * addresses it instead of the throwaway repository it built for itself — `git config`, `git commit`
 * and `git tag` have all landed on a contributor's checkout that way.
 *
 * Two things keep them apart and both are asserted here: the hook drops the variables before it
 * starts the gate, and every repository test that runs `git` builds its environment in one place.
 */
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { chmodSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { delimiter, join, resolve } from "node:path";
import { after, test } from "node:test";

import { environmentForGitFixture, GIT_REPOSITORY_VARIABLES } from "./lib/git-environment.ts";

const REPO_ROOT = resolve(import.meta.dirname, "..");
const HOOKS = join(REPO_ROOT, ".vite-hooks");
const SCRIPTS = join(REPO_ROOT, "scripts");

const temporaries: string[] = [];

after(() => {
	for (const temporary of temporaries) rmSync(temporary, { recursive: true, force: true });
});

function temporaryDirectory(prefix: string): string {
	const directory = mkdtempSync(join(tmpdir(), prefix));
	temporaries.push(directory);
	return directory;
}

/** The hooks the dispatcher runs that start a repository task, which is what carries the risk. */
function gateHooks(): string[] {
	return readdirSync(HOOKS, { withFileTypes: true })
		.filter((entry) => entry.isFile())
		.map((entry) => entry.name)
		.filter((name) => readFileSync(join(HOOKS, name), "utf8").includes("vp run"));
}

void test("the hook drops every repository variable before it starts the gate", () => {
	const hooks = gateHooks();
	assert.deepEqual(hooks, ["pre-push"]);
	for (const hook of hooks) {
		const source = readFileSync(join(HOOKS, hook), "utf8");
		const unset = source.split("\n").find((line) => line.startsWith("unset "));
		assert.ok(unset, `.vite-hooks/${hook} must unset the variables git exports to it`);
		assert.deepEqual(
			unset.split(/\s+/).slice(1).toSorted(),
			[...GIT_REPOSITORY_VARIABLES],
			`.vite-hooks/${hook} must unset exactly the variables lib/git-environment.ts names`,
		);
		assert.ok(
			source.indexOf(unset) < source.indexOf("vp run"),
			`.vite-hooks/${hook} must unset them before it runs the gate`,
		);
	}
});

void test("a gate that runs git the wrong way cannot reach the repository being pushed", () => {
	const git = (cwd: string, ...args: string[]): string =>
		execFileSync("git", args, { cwd, encoding: "utf8", env: environmentForGitFixture() }).trim();
	const fingerprint = (repository: string): string[] => [
		git(repository, "show-ref"),
		git(repository, "tag", "--list"),
		git(repository, "worktree", "list"),
		readFileSync(join(repository, ".git", "config"), "utf8"),
	];

	const decoy = temporaryDirectory("git-environment-decoy-");
	git(decoy, "init", "--quiet", "--initial-branch=main");
	git(decoy, "config", "user.email", "decoy@example.invalid");
	git(decoy, "config", "user.name", "Decoy");
	writeFileSync(join(decoy, "tracked"), "decoy\n");
	git(decoy, "add", "-A");
	git(decoy, "commit", "--quiet", "-m", "decoy");
	const before = fingerprint(decoy);

	// Stands in for `vp run check`: a task that runs git in whatever environment it was handed, and
	// records that it ran so a hook that never reached it cannot pass this test.
	const stubs = temporaryDirectory("git-environment-gate-");
	const marker = join(stubs, "ran");
	const stub = join(stubs, "vp");
	writeFileSync(
		stub,
		`#!/bin/sh\nprintf ran > "${marker}"\ngit config user.name intruder\ngit tag intruder\nexit 0\n`,
	);
	chmodSync(stub, 0o755);

	const environment = environmentForGitFixture({
		GIT_DIR: join(decoy, ".git"),
		GIT_INDEX_FILE: join(decoy, ".git", "index"),
		GIT_WORK_TREE: decoy,
	});
	environment.PATH = `${stubs}${delimiter}${environment.PATH ?? ""}`;
	execFileSync("sh", ["-e", join(HOOKS, "pre-push")], {
		cwd: temporaryDirectory("git-environment-worktree-"),
		encoding: "utf8",
		env: environment,
		stdio: ["ignore", "pipe", "pipe"],
	});

	assert.equal(readFileSync(marker, "utf8"), "ran");
	assert.deepEqual(fingerprint(decoy), before);
});

void test("a repository test that runs git builds its environment in one place", () => {
	const spawnsGit = /(?:execFile|execFileAsync|execFileSync|spawn|spawnSync)\(\s*"git"/;
	// The naive form: an environment derived from this process, which under a hook names the
	// repository being pushed.
	const derivesFromProcess = /\.\.\.process\.env|process\.env\s*\)/;
	const tests = readdirSync(SCRIPTS).filter((name) => name.endsWith(".test.ts"));
	const running = tests.filter((name) => spawnsGit.test(readFileSync(join(SCRIPTS, name), "utf8")));
	assert.ok(running.includes("reconcile-deployment.test.ts"));
	for (const name of running) {
		const source = readFileSync(join(SCRIPTS, name), "utf8");
		assert.match(
			source,
			/from "\.\/lib\/git-environment\.ts"/,
			`scripts/${name} runs git, so it takes its environment from lib/git-environment.ts`,
		);
		assert.doesNotMatch(
			source,
			derivesFromProcess,
			`scripts/${name} must build its git environment with environmentForGitFixture()`,
		);
	}
});
