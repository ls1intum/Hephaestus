import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";
import { test } from "node:test";

import { isMap, isSeq, parseDocument } from "yaml";

const workflow = parseDocument(await readFile(".github/workflows/continuous-staging.yml", "utf8"));
const steps = workflow.getIn(["jobs", "follow-main", "steps"]);
assert.ok(isSeq(steps));
const follow = steps.items.find(
	(item) => isMap(item) && item.get("name") === "Follow this commit on staging",
);
assert.ok(isMap(follow));
const run = follow.get("run");
assert.equal(typeof run, "string");
assert.ok(typeof run === "string");
const script = run;

function isCall(value: unknown): value is string[] {
	return Array.isArray(value) && value.every((arg: unknown) => typeof arg === "string");
}

const shellOnly = {
	skip:
		process.platform === "win32"
			? "The Ubuntu workflow uses Bash, and its fixture executables need POSIX shebangs."
			: false,
};

// Run the workflow's actual shell. Only GitHub and the clock are fixtures: no workflow is dispatched.
const transport = String.raw`#!/usr/bin/env node
const fs = require('node:fs');
const args = process.argv.slice(2);
const state = JSON.parse(fs.readFileSync(process.env.STATE, 'utf8'));
state.calls.push(args);
const save = () => fs.writeFileSync(process.env.STATE, JSON.stringify(state));
if (args[0] === 'api' && args.includes('POST')) {
  save();
  console.log(process.env.INVALID_RUN === 'true' ? 'null' : '123');
} else if (args[0] === 'api') {
  if (!args.includes('repos/owner/repo/actions/runs/123/jobs?per_page=1')) throw new Error('Wrong run observed');
  state.reads++;
  if (process.env.FORBIDDEN === 'true') {
    save();
    console.error('gh: Forbidden (HTTP 403)');
    process.exit(1);
  }
  if (state.reads <= Number(process.env.HIDDEN_READS)) {
    save();
    console.error('gh: Not Found (HTTP 404)');
    process.exit(1);
  }
  state.visible = true;
  save();
} else if (args[0] === 'run' && args[1] === 'watch') {
  save();
  if (!state.visible) {
    console.error('failed to get jobs: HTTP 404: Not Found');
    process.exit(1);
  }
  if (JSON.stringify(args) !== JSON.stringify(['run','watch','123','--repo','owner/repo','--exit-status'])) {
    throw new Error('Watcher must retain the child verdict');
  }
  console.log(process.env.CHILD_FAILED === 'true' ? 'Promotion failed' : 'Promotion succeeded');
  process.exit(process.env.CHILD_FAILED === 'true' ? 1 : 0);
} else throw new Error('Unexpected GitHub operation');
`;

async function observe(overrides: Record<string, string> = {}) {
	const directory = await mkdtemp(path.join(tmpdir(), "continuous-staging-"));
	try {
		const bin = path.join(directory, "bin");
		await mkdir(bin);
		const state = path.join(directory, "state.json");
		await writeFile(state, JSON.stringify({ calls: [], reads: 0, visible: false }));
		await writeFile(path.join(bin, "gh"), transport, { mode: 0o755 });
		await writeFile(path.join(bin, "sleep"), '#!/bin/sh\nprintf "%s\\n" "$1" >> "$SLEEPS"\n', {
			mode: 0o755,
		});
		const sleeps = path.join(directory, "sleeps");
		await writeFile(sleeps, "");
		const result = spawnSync("bash", ["--noprofile", "--norc", "-e", "-c", script], {
			encoding: "utf8",
			env: {
				...process.env,
				PATH: `${bin}${path.delimiter}${process.env.PATH ?? ""}`,
				GITHUB_REPOSITORY: "owner/repo",
				COMMIT: "c".repeat(40),
				RUNNER_TEMP: directory,
				STATE: state,
				SLEEPS: sleeps,
				HIDDEN_READS: "1",
				...overrides,
			},
			timeout: 10_000,
		});
		assert.equal(result.error, undefined);
		const recorded: unknown = JSON.parse(await readFile(state, "utf8"));
		assert.ok(typeof recorded === "object" && recorded !== null && "calls" in recorded);
		const { calls } = recorded;
		assert.ok(Array.isArray(calls));
		const sleepLog = await readFile(sleeps, "utf8");
		return {
			status: result.status,
			output: result.stdout + result.stderr,
			calls: calls.map((call: unknown) => {
				assert.ok(isCall(call));
				return call;
			}),
			sleeps: sleepLog.trim().split("\n").filter(Boolean),
		};
	} finally {
		await rm(directory, { recursive: true, force: true });
	}
}

void test(
	"follows the exact dispatched promotion after its jobs endpoint changes from 404 to visible",
	shellOnly,
	async () => {
		const result = await observe();
		assert.equal(result.status, 0, result.output);
		assert.equal(result.calls.filter((args) => args.includes("POST")).length, 1);
		assert.equal(
			result.calls.filter((args) => args[0] === "api" && !args.includes("POST")).length,
			2,
		);
		assert.equal(result.calls.filter((args) => args[0] === "run").length, 1);
		assert.deepEqual(result.sleeps, ["5"]);
	},
);

void test(
	"keeps a visible promotion's terminal failure without dispatching or watching it again",
	shellOnly,
	async () => {
		const result = await observe({ CHILD_FAILED: "true" });
		assert.equal(result.status, 1, result.output);
		assert.match(result.output, /Promotion failed/u);
		assert.equal(result.calls.filter((args) => args.includes("POST")).length, 1);
		assert.equal(result.calls.filter((args) => args[0] === "run").length, 1);
	},
);

void test(
	"refuses a promotion that never becomes visible within the bounded wait",
	shellOnly,
	async () => {
		const result = await observe({ HIDDEN_READS: "12" });
		assert.equal(result.status, 1, result.output);
		assert.match(result.output, /did not become visible after 12 checks/u);
		assert.equal(result.calls.filter((args) => args.includes("POST")).length, 1);
		assert.equal(
			result.calls.filter((args) => args[0] === "api" && !args.includes("POST")).length,
			12,
		);
		assert.equal(result.calls.filter((args) => args[0] === "run").length, 0);
		assert.deepEqual(
			result.sleeps,
			Array.from({ length: 11 }, () => "5"),
		);
	},
);

void test("does not retry a non-404 API refusal", shellOnly, async () => {
	const result = await observe({ FORBIDDEN: "true" });
	assert.equal(result.status, 1, result.output);
	assert.match(result.output, /HTTP 403/u);
	assert.equal(
		result.calls.filter((args) => args[0] === "api" && !args.includes("POST")).length,
		1,
	);
	assert.equal(result.calls.filter((args) => args[0] === "run").length, 0);
	assert.deepEqual(result.sleeps, []);
});

void test("refuses an invalid dispatch ID before observing another run", shellOnly, async () => {
	const result = await observe({ INVALID_RUN: "true" });
	assert.equal(result.status, 1, result.output);
	assert.match(result.output, /Dispatch returned no run ID/u);
	assert.equal(result.calls.length, 1);
	assert.deepEqual(result.sleeps, []);
});
