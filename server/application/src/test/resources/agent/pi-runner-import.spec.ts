import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdtempSync, readdirSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { test } from "node:test";

void test("importing the runner leaves an empty workspace untouched and exits without a watchdog", (t) => {
	const cwd = mkdtempSync(path.join(tmpdir(), "runner-import-"));
	t.after(() => rmSync(cwd, { recursive: true, force: true }));
	const runner = new URL("../../../main/resources/agent/pi-runner.ts", import.meta.url).href;
	const child = spawnSync(
		process.execPath,
		[
			"--input-type=module",
			"-e",
			`const runner = await import(${JSON.stringify(runner)}); process.stdout.write(typeof runner.runReviewJob);`,
		],
		{
			cwd,
			env: { ...process.env, PI_RUNNER_CWD: cwd },
			encoding: "utf8",
			timeout: 5000,
		},
	);
	assert.equal(child.status, 0, child.stderr);
	assert.equal(child.stdout, "function");
	assert.deepEqual(readdirSync(cwd), []);
});
