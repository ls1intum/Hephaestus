import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdir, mkdtemp, readFile, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { promisify } from "node:util";

const run = promisify(execFile);

void test("synchronizes every release-owned version reference", async () => {
	const cwd = await mkdtemp(join(tmpdir(), "sync-release-version-"));
	await mkdir(join(cwd, "docs/admin"), { recursive: true });
	await writeFile(join(cwd, "package.json"), '{"version":"0.75.0"}');
	await writeFile(
		join(cwd, "docs/admin/install.mdx"),
		"VERSION=0.74.0 # the release you are installing\n",
	);
	await writeFile(
		join(cwd, "README.md"),
		"```bash\n  VERSION=0.74.0 # the release you are installing\n```\n",
	);
	await writeFile(join(cwd, "MIGRATION.md"), "### Next release\n\nAction.\n");

	await run(process.execPath, [join(import.meta.dirname, "sync-release-version.ts")], { cwd });

	assert.equal(
		await readFile(join(cwd, "docs/admin/install.mdx"), "utf8"),
		"VERSION=0.75.0 # the release you are installing\n",
	);
	assert.equal(
		await readFile(join(cwd, "README.md"), "utf8"),
		"```bash\n  VERSION=0.75.0 # the release you are installing\n```\n",
	);
	assert.equal(await readFile(join(cwd, "MIGRATION.md"), "utf8"), "### v0.75.0\n\nAction.\n");

	await run(process.execPath, [join(import.meta.dirname, "sync-release-version.ts")], { cwd });
	assert.equal(await readFile(join(cwd, "MIGRATION.md"), "utf8"), "### v0.75.0\n\nAction.\n");
});
