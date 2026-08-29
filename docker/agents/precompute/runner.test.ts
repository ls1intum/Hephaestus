import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, mkdir, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

void test("runner executes a staged practice and writes its public artifact contract", async () => {
	const root = await mkdtemp(join(tmpdir(), "precompute-runner-"));
	const output = join(root, "out");
	await mkdir(join(output, "practices"), { recursive: true });
	await writeFile(join(root, "package.json"), '{"type":"module"}\n');
	await writeFile(
		join(output, "practices", "sample.ts"),
		'export default () => ({hints: [], metrics: {found: 1}, directions: ["inspect sample"]});\n',
	);

	const child = spawn(
		process.execPath,
		[join(import.meta.dirname, "runner.ts"), "--repo", root, "--output", output],
		{ stdio: ["ignore", "ignore", "pipe"] },
	);
	let stderr = "";
	child.stderr.setEncoding("utf8");
	child.stderr.on("data", (chunk: string) => (stderr += chunk));
	const code = await new Promise<number | null>((resolve, reject) => {
		child.once("error", reject);
		child.once("close", resolve);
	});

	assert.equal(code, 0, stderr);
	assert.deepEqual(JSON.parse(await readFile(join(output, "sample.json"), "utf8")), {
		practice: "sample",
		status: "ok",
		hints: [],
		metrics: { found: 1 },
		directions: ["inspect sample"],
	});
	assert.match(await readFile(join(output, "summary.md"), "utf8"), /## sample/);
	assert.ok(await readFile(join(output, ".complete"), "utf8"));
});
