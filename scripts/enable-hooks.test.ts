import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { cpSync, mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { after, test } from "node:test";

import { CAPTURE_LIMIT_BYTES } from "./lib/process.ts";

const REPO_ROOT = resolve(import.meta.dirname, "..");
const SCRIPT = join(REPO_ROOT, "scripts", "enable-hooks.ts");
const repositories: string[] = [];

/**
 * Git exports `GIT_DIR` and its siblings to every hook process, and they outrank a child's `cwd`.
 * Inherited, they would point both the script under test and the assertions at the repository the
 * hook is running in rather than at the clone made below.
 */
const ENV = Object.fromEntries(
	Object.entries(process.env).filter(([key]) => !key.startsWith("GIT_")),
);

after(() => {
	for (const repository of repositories) rmSync(repository, { recursive: true, force: true });
});

/** A clone with the project hooks whose Git still points at an earlier hook manager's directory. */
function clone(): { repository: string; git: (...args: string[]) => string } {
	const repository = mkdtempSync(join(tmpdir(), "enable-hooks-"));
	repositories.push(repository);
	const git = (...args: string[]): string =>
		execFileSync("git", args, {
			cwd: repository,
			encoding: "utf8",
			maxBuffer: CAPTURE_LIMIT_BYTES,
			env: ENV,
		}).trim();
	git("init", "--quiet");
	git("config", "core.hooksPath", ".husky/_");
	cpSync(join(REPO_ROOT, ".vite-hooks"), join(repository, ".vite-hooks"), { recursive: true });
	return { repository, git };
}

void test("an install moves Git from an earlier hooks directory to the dispatcher", () => {
	const { repository, git } = clone();
	const result = spawnSync("node", [SCRIPT], {
		cwd: repository,
		encoding: "utf8",
		maxBuffer: CAPTURE_LIMIT_BYTES,
		env: ENV,
	});
	assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
	assert.equal(git("config", "core.hooksPath"), ".vite-hooks/_");
	const again = spawnSync("node", [SCRIPT], { cwd: repository, encoding: "utf8", env: ENV });
	assert.equal(again.status, 0, `${again.stdout}${again.stderr}`);
	assert.equal(git("config", "core.hooksPath"), ".vite-hooks/_");
});
