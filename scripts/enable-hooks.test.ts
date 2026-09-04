import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { cpSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { after, test } from "node:test";

import { CAPTURE_LIMIT_BYTES } from "./lib/process.ts";

const REPO_ROOT = resolve(import.meta.dirname, "..");
const SCRIPT = join(REPO_ROOT, "scripts", "enable-hooks.ts");
const temporaries: string[] = [];

/** A global Git config holding exactly these lines, so the machine's own cannot decide a test. */
function globalConfig(contents = ""): string {
	const directory = mkdtempSync(join(tmpdir(), "enable-hooks-config-"));
	temporaries.push(directory);
	const path = join(directory, "gitconfig");
	writeFileSync(path, contents);
	return path;
}

const EMPTY_GLOBAL_CONFIG = globalConfig();

/**
 * Git exports `GIT_DIR` and its siblings to every hook process, and they outrank a child's `cwd`.
 * Inherited, they would point both the script under test and the assertions at the repository the
 * hook is running in rather than at the clone made below. The global and system configs are then
 * replaced rather than dropped, because what a developer's own config says about commit signing is
 * exactly what these tests decide.
 */
function hookFreeEnv(configPath: string = EMPTY_GLOBAL_CONFIG): NodeJS.ProcessEnv {
	return {
		...Object.fromEntries(Object.entries(process.env).filter(([key]) => !key.startsWith("GIT_"))),
		GIT_CONFIG_GLOBAL: configPath,
		GIT_CONFIG_NOSYSTEM: "1",
	};
}

after(() => {
	for (const temporary of temporaries) rmSync(temporary, { recursive: true, force: true });
});

/** A clone with the project hooks whose Git still points at an earlier hook manager's directory. */
function clone(): { repository: string; git: (...args: string[]) => string } {
	const repository = mkdtempSync(join(tmpdir(), "enable-hooks-"));
	temporaries.push(repository);
	const git = (...args: string[]): string =>
		execFileSync("git", args, {
			cwd: repository,
			encoding: "utf8",
			maxBuffer: CAPTURE_LIMIT_BYTES,
			env: hookFreeEnv(),
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
		env: hookFreeEnv(),
	});
	assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
	assert.equal(git("config", "core.hooksPath"), ".vite-hooks/_");
	const again = spawnSync("node", [SCRIPT], {
		cwd: repository,
		encoding: "utf8",
		env: hookFreeEnv(),
	});
	assert.equal(again.status, 0, `${again.stdout}${again.stderr}`);
	assert.equal(git("config", "core.hooksPath"), ".vite-hooks/_");
});

// Git exports `GIT_DIR` to a hook only when the push runs from a linked worktree, so no CI push can
// reach this state; setting it here is the whole reproduction.
void test("an install under a hook's GIT_DIR configures the clone it runs in", () => {
	const decoy = clone();
	const { repository, git } = clone();
	process.env.GIT_DIR = join(decoy.repository, ".git");
	try {
		const result = spawnSync("node", [SCRIPT], {
			cwd: repository,
			encoding: "utf8",
			maxBuffer: CAPTURE_LIMIT_BYTES,
			env: hookFreeEnv(),
		});
		assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
	} finally {
		delete process.env.GIT_DIR;
	}
	assert.equal(git("config", "core.hooksPath"), ".vite-hooks/_");
	assert.equal(decoy.git("config", "core.hooksPath"), ".husky/_");
});

void test("an install without commit signing configured warns and still succeeds", () => {
	const { repository } = clone();
	const result = spawnSync("node", [SCRIPT], {
		cwd: repository,
		encoding: "utf8",
		maxBuffer: CAPTURE_LIMIT_BYTES,
		env: hookFreeEnv(),
	});
	assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
	assert.match(result.stderr, /git config --global commit\.gpgsign true/);
	assert.match(result.stderr, /gh ssh-key add .* --type signing/);
});

void test("an install with commit signing configured says nothing about it", () => {
	const { repository } = clone();
	const signing = globalConfig(
		"[gpg]\n\tformat = ssh\n[user]\n\tsigningkey = ~/.ssh/id_ed25519.pub\n[commit]\n\tgpgsign = true\n",
	);
	const result = spawnSync("node", [SCRIPT], {
		cwd: repository,
		encoding: "utf8",
		maxBuffer: CAPTURE_LIMIT_BYTES,
		env: hookFreeEnv(signing),
	});
	assert.equal(result.status, 0, `${result.stdout}${result.stderr}`);
	assert.doesNotMatch(result.stderr, /commit\.gpgsign/);
});
