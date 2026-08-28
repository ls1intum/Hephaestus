// Unit tests for the version-controlled precompute scripts' classification logic. Stages each real
// script next to a symlinked lib/ (exactly as the runner does), imports it, and asserts on its
// metrics/directions output.
import { afterEach, beforeAll, describe, expect, it } from "bun:test";
import { cp, mkdir, mkdtemp, rm, symlink, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";

import { isPracticeModule } from "./lib/practice-contract";
import type { DiffFile, PracticeScript } from "./lib/types";

const SCRIPTS_DIR = resolve(
	import.meta.dir,
	"../../../server/application/src/main/resources/practices/precompute",
);
const LIB_DIR = resolve(import.meta.dir, "lib");

const tempDirs: string[] = [];

async function createTempDir(prefix: string): Promise<string> {
	const dir = await mkdtemp(join(tmpdir(), prefix));
	tempDirs.push(dir);
	return dir;
}

/**
 * Stage a single script in a work dir laid out like the runner (`practices/<script>` + symlinked `lib/`)
 * so its `../lib/types` import resolves, then import the staged copy and return its default export.
 */
async function loadScript(name: string): Promise<PracticeScript> {
	const work = await createTempDir(`pc-script-${name}-`);
	await mkdir(join(work, "practices"), { recursive: true });
	await symlink(LIB_DIR, join(work, "lib"));
	const staged = join(work, "practices", `${name}.ts`);
	await cp(join(SCRIPTS_DIR, `${name}.ts`), staged);
	const mod: unknown = await import(staged);
	if (!isPracticeModule(mod)) {
		throw new Error(`${name} does not export a default function`);
	}
	return mod.default;
}

/** A path the diff touched. The scripts under test read the changed paths, not the hunk bodies. */
function changedFile(path: string): DiffFile {
	return { path, addedLines: new Map(), removedLines: new Map(), hunks: [] };
}

afterEach(async () => {
	await Promise.all(tempDirs.splice(0).map((dir) => rm(dir, { recursive: true, force: true })));
});

describe("ships-tests-with-the-change", () => {
	let run: PracticeScript;
	beforeAll(async () => {
		run = await loadScript("ships-tests-with-the-change");
	});

	async function repoWith(files: Record<string, string>): Promise<string> {
		const repo = await createTempDir("pc-repo-");
		for (const [path, body] of Object.entries(files)) {
			const full = join(repo, path);
			await mkdir(join(full, ".."), { recursive: true });
			await writeFile(full, body);
		}
		return repo;
	}

	it("classifies code vs test files in a single walk and flags an untested prod change", async () => {
		const repo = await repoWith({
			"src/App.swift": "struct App {}",
			"src/util.ts": "export const x = 1;",
			"Tests/AppTests.swift": "// test",
		});
		// Diff touches a production file, adds no test file.
		const diff = new Map([["src/App.swift", changedFile("src/App.swift")]]);

		const result = await run(repo, diff, {});

		expect(result.metrics.repoCodeFileCount).toBe(3);
		expect(result.metrics.repoTestFileCount).toBe(1);
		expect(result.metrics.worktreeVisible).toBe(1);
		expect(result.metrics.diffProductionFiles).toBe(1);
		expect(result.metrics.diffTestFiles).toBe(0);
		expect(result.directions.join(" ")).toContain("0 test file(s)");
	});

	it("excludes node_modules / dotfile / .build dirs from the census", async () => {
		const repo = await repoWith({
			"src/main.ts": "export const a = 1;",
			"node_modules/dep/index.js": "module.exports = {};",
			".build/cache.swift": "struct Cached {}",
			".hidden/secret.go": "package x",
		});

		const result = await run(repo, new Map<string, DiffFile>(), {});

		// Only src/main.ts counts; the three excluded-dir files are skipped.
		expect(result.metrics.repoCodeFileCount).toBe(1);
	});

	it("reports the worktree as not visible when zero source files are seen", async () => {
		const repo = await repoWith({ "README.md": "# docs only" });

		const result = await run(repo, new Map<string, DiffFile>(), {});

		expect(result.metrics.worktreeVisible).toBe(0);
		expect(result.directions.join(" ")).toContain("WORKTREE NOT VISIBLE");
	});
});
