import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { promisify } from "node:util";

const run = promisify(execFile);

void test("synchronizes every release-owned version reference", async () => {
	const cwd = await mkdtemp(join(tmpdir(), "sync-release-version-"));
	await mkdir(join(cwd, "docs/admin"), { recursive: true });
	await mkdir(join(cwd, ".migration"));
	await writeFile(join(cwd, "package.json"), '{"version":"0.75.0"}');
	await writeFile(
		join(cwd, "docs/admin/install.mdx"),
		"VERSION=0.74.0 # the release you are installing\n",
	);
	await writeFile(
		join(cwd, "README.md"),
		"```bash\n  VERSION=0.74.0 # the release you are installing\n```\n",
	);
	await writeFile(
		join(cwd, "MIGRATION.md"),
		"### Next release\n\n#### 🔴 Existing action\n\nDo it.\n\n### v0.74.0\n\nOld.\n",
	);
	await writeFile(join(cwd, ".migration/z-last.md"), "#### 🔴 Z action\n\nDo Z.\n");
	await writeFile(join(cwd, ".migration/a-first.md"), "#### 🔴 A action\n\nDo A.\n");
	await writeFile(join(cwd, ".migration/README.md"), "instructions\n");

	await run(process.execPath, [join(import.meta.dirname, "sync-release-version.ts")], { cwd });

	assert.equal(
		await readFile(join(cwd, "docs/admin/install.mdx"), "utf8"),
		"VERSION=0.75.0 # the release you are installing\n",
	);
	assert.equal(
		await readFile(join(cwd, "README.md"), "utf8"),
		"```bash\n  VERSION=0.75.0 # the release you are installing\n```\n",
	);
	assert.equal(
		await readFile(join(cwd, "MIGRATION.md"), "utf8"),
		"### Next release\n\n### v0.75.0\n\n#### 🔴 Existing action\n\nDo it.\n\n#### 🔴 A action\n\nDo A.\n\n#### 🔴 Z action\n\nDo Z.\n\n### v0.74.0\n\nOld.\n",
	);
	assert.deepEqual(await readdir(join(cwd, ".migration")), ["README.md"]);

	await run(process.execPath, [join(import.meta.dirname, "sync-release-version.ts")], { cwd });
	assert.equal(
		await readFile(join(cwd, "MIGRATION.md"), "utf8"),
		"### Next release\n\n### v0.75.0\n\n#### 🔴 Existing action\n\nDo it.\n\n#### 🔴 A action\n\nDo A.\n\n#### 🔴 Z action\n\nDo Z.\n\n### v0.74.0\n\nOld.\n",
	);
	assert.deepEqual(await readdir(join(cwd, ".migration")), ["README.md"]);

	await writeFile(join(cwd, ".migration/late.md"), "#### 🔴 Late action\n");
	await assert.rejects(
		run(process.execPath, [join(import.meta.dirname, "sync-release-version.ts")], { cwd }),
		/already contains ### v0\.75\.0/,
	);
	await rm(join(cwd, ".migration/late.md"));
	await writeFile(
		join(cwd, "MIGRATION.md"),
		`### Next release\n\n${await readFile(join(cwd, "MIGRATION.md"), "utf8")}`,
	);
	await assert.rejects(
		run(process.execPath, [join(import.meta.dirname, "sync-release-version.ts")], { cwd }),
		/exactly one ### Next release/,
	);
});
