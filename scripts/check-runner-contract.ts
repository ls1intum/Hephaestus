/**
 * The task graph in `vite.config.ts` relies on how `vp run` executes a command. Those facts are
 * Vite+ behaviour, not documented guarantees, so this gate runs the pinned `vite-plus` against a
 * scratch workspace outside the repository and fails on the first fact that no longer holds:
 *
 *   - array commands run in order, and an `&&` chain keeps its order;
 *   - a glob reaches the command as written, quoted or not; the runner never expands it;
 *   - an uncached task inherits the caller's environment; a cached task is given a filtered one and
 *     sees only what it names in `env`;
 *   - a task runs its `dependsOn` before its own command, and a group with no command runs its
 *     dependencies and fails when one of them fails;
 *   - arguments after the task name reach the command;
 *   - a task caches only when it runs its command itself: neither `vp run <task>` nor `vp exec <bin>`
 *     as a command is ever cached, and a file added to a directory the command read is a miss,
 *     except on Windows, where automatic tracking misses it and the CI leg runs uncached.
 *
 * Shell parameter expansion is outside the contract because the runner's shell is not a POSIX
 * shell on every platform; `ci-contract.test.ts` keeps `$` out of every command instead.
 *
 * The scratch workspace links only the pinned `vite-plus` and the repository's binaries, so its task
 * cache is its own, and declares its own `pnpm-workspace.yaml`, so no parent directory can claim it.
 */
import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { after, test } from "node:test";

import { CAPTURE_LIMIT_BYTES } from "./lib/process.ts";
import { unpassedTasks } from "./report-task-run.ts";

const REPO_ROOT = resolve(import.meta.dirname, "..");
const ARGV = 'node -e "console.log(JSON.stringify(process.argv.slice(1)))" --';
const PROBE_ENV = 'node -e "console.log(JSON.stringify([process.env.PROBE ?? null]))"';

const workspace = mkdtempSync(join(tmpdir(), "runner-contract-"));
mkdirSync(join(workspace, "node_modules"));
for (const entry of ["vite-plus", ".bin"])
	symlinkSync(
		join(REPO_ROOT, "node_modules", entry),
		join(workspace, "node_modules", entry),
		process.platform === "win32" ? "junction" : "dir",
	);
writeFileSync(join(workspace, "pnpm-workspace.yaml"), "packages:\n  - .\n");
writeFileSync(
	join(workspace, "package.json"),
	`${JSON.stringify(
		{ name: "runner-contract", private: true, type: "module", scripts: { leaf: `${ARGV} leaf` } },
		null,
		"\t",
	)}\n`,
);
mkdirSync(join(workspace, "fixtures"));
for (const name of ["a.json", "b.json"]) writeFileSync(join(workspace, "fixtures", name), "{}\n");
// Written as data, so a quote inside a command never has to survive a second layer of quoting.
const tasks = {
	order: {
		command: [
			"node -e \"setTimeout(() => console.log('FIRST'), 200)\"",
			"node -e \"console.log('SECOND')\"",
		],
		cache: false,
	},
	chain: {
		command:
			"node -e \"setTimeout(() => console.log('FIRST'), 200)\" && node -e \"console.log('SECOND')\"",
		cache: false,
	},
	glob: { command: `${ARGV} fixtures/*.json 'fixtures/*.json'`, cache: false },
	env: { command: PROBE_ENV, cache: false },
	cachedEnv: { command: PROBE_ENV, input: ["package.json"], output: [] },
	declaredEnv: { command: PROBE_ENV, input: ["package.json"], output: [], env: ["PROBE"] },
	pass: { command: ARGV, cache: false },
	fails: { command: 'node -e "process.exit(3)"', cache: false },
	groupOk: { command: [], dependsOn: ["leaf"], cache: false },
	depends: { command: `${ARGV} own`, dependsOn: ["leaf"], cache: false },
	groupFails: { command: [], dependsOn: ["leaf", "fails"], cache: false },
	delegating: { command: "vp run leaf", input: ["package.json"], output: [] },
	owning: { command: `${ARGV} owning`, input: ["package.json"], output: [] },
	executing: { command: "vp exec oxfmt --version", input: ["package.json"], output: [] },
	tracking: {
		command: "node -e \"console.log(JSON.stringify(require('node:fs').readdirSync('fixtures')))\"",
		input: [{ auto: true }],
		output: [],
	},
};
writeFileSync(
	join(workspace, "vite.config.ts"),
	`import { defineConfig } from "vite-plus";\nexport default defineConfig({ run: { tasks: ${JSON.stringify(tasks, null, "\t")} } });\n`,
);

function run(
	args: string[],
	env: Record<string, string> = {},
): { readonly status: number | null; readonly output: string } {
	const result = spawnSync("vp", ["run", ...args], {
		cwd: workspace,
		encoding: "utf8",
		maxBuffer: CAPTURE_LIMIT_BYTES,
		env: { ...process.env, ...env },
	});
	assert.equal(result.error, undefined, `vp could not be spawned: ${String(result.error)}`);
	return { status: result.status, output: `${result.stdout}${result.stderr}` };
}

const isArray = (value: unknown): value is unknown[] => Array.isArray(value);

/** The JSON arrays the probe commands printed, in order. */
function printed(output: string): unknown[][] {
	return output
		.split("\n")
		.filter((line) => line.startsWith("["))
		.map((line) => {
			const value: unknown = JSON.parse(line);
			assert.ok(isArray(value), line);
			return value;
		});
}

void test("array commands and && chains run in order", () => {
	for (const task of ["order", "chain"]) {
		const { status, output } = run([task]);
		assert.equal(status, 0, output);
		assert.ok(output.indexOf("FIRST") < output.indexOf("SECOND"), `${task}: ${output}`);
	}
});

void test("globs reach the command unexpanded, quoted or not", () => {
	const { status, output } = run(["glob"]);
	assert.equal(status, 0, output);
	assert.deepEqual(printed(output), [["fixtures/*.json", "fixtures/*.json"]], output);
});

void test("an uncached task inherits the caller's environment", () => {
	const { status, output } = run(["env"], { PROBE: "x" });
	assert.equal(status, 0, output);
	assert.deepEqual(printed(output), [["x"]], output);
});

void test("a cached task sees only the environment it names", () => {
	const hidden = run(["cachedEnv"], { PROBE: "x" });
	assert.equal(hidden.status, 0, hidden.output);
	assert.deepEqual(printed(hidden.output), [[null]], hidden.output);
	const declared = run(["declaredEnv"], { PROBE: "x" });
	assert.equal(declared.status, 0, declared.output);
	assert.deepEqual(printed(declared.output), [["x"]], declared.output);
});

void test("arguments after the task name reach the command", () => {
	const { status, output } = run(["pass", "one", "--two"]);
	assert.equal(status, 0, output);
	assert.deepEqual(printed(output), [["one", "--two"]], output);
});

void test("a task runs its dependencies first; a group runs them and fails when one fails", () => {
	const depends = run(["depends"]);
	assert.equal(depends.status, 0, depends.output);
	assert.deepEqual(printed(depends.output), [["leaf"], ["own"]], depends.output);
	const ok = run(["groupOk"]);
	assert.equal(ok.status, 0, ok.output);
	assert.deepEqual(printed(ok.output), [["leaf"]], ok.output);
	const failing = run(["groupFails"]);
	assert.notEqual(failing.status, 0, failing.output);
});

void test("a task caches only when it runs its command itself", () => {
	for (const task of ["delegating", "owning", "executing"]) run([task]);
	const delegating = run(["delegating"]).output;
	const owning = run(["owning"]).output;
	const executing = run(["executing"]).output;
	assert.doesNotMatch(delegating, /cache hit/, delegating);
	assert.doesNotMatch(executing, /cache hit/, executing);
	assert.match(owning, /cache hit/, owning);
});

void test(
	"a file added to a directory the command read misses the cache",
	{ skip: process.platform === "win32" && "automatic tracking misses a new file on Windows" },
	() => {
		run(["tracking"]);
		assert.match(run(["tracking"]).output, /cache hit/);
		writeFileSync(join(workspace, "fixtures", "c.json"), "{}\n");
		assert.doesNotMatch(run(["tracking"]).output, /cache hit/);
	},
);

void test("the report of a failed run names the task that failed", () => {
	run(["groupFails"]);
	const details = spawnSync("vp", ["run", "--last-details"], {
		cwd: workspace,
		encoding: "utf8",
		maxBuffer: CAPTURE_LIMIT_BYTES,
	});
	assert.equal(details.error, undefined, `vp could not be spawned: ${String(details.error)}`);
	const report = `${details.stdout}${details.stderr}`;
	// What `scripts/report-task-run.ts` turns into the workflow's error annotations.
	assert.ok(unpassedTasks(report).includes("fails"), report);
});

after(() => rmSync(workspace, { recursive: true, force: true }));
