import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdir, mkdtemp, readFile, readdir, writeFile } from "node:fs/promises";
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
	// v0.76.0 and v0.75.0 are mislabeled hand-written sections for unreleased work (the released
	// history starts at v0.74.0) — the exact shape that broke the Version PR on main.
	await writeFile(
		join(cwd, "MIGRATION.md"),
		"### Next release\n\n#### 🔴 Existing action\n\nDo it.\n\n### v0.76.0\n\n#### 🔴 Mislabeled newer action\n\nFold me.\n\n### v0.75.0\n\n#### 🔴 Mislabeled same-version action\n\nFold me too.\n\n### v0.74.0\n\nOld.\n",
	);
	await writeFile(
		join(cwd, ".migration/z-last.md"),
		"#### 🔴 Z action\n\nDo Z: keep `$$VAR`, `$&`, and `$'` literal.\n",
	);
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
	const stamped =
		"### Next release\n\n### v0.75.0\n\n#### 🔴 Existing action\n\nDo it.\n\n#### 🔴 Mislabeled newer action\n\nFold me.\n\n#### 🔴 Mislabeled same-version action\n\nFold me too.\n\n#### 🔴 A action\n\nDo A.\n\n#### 🔴 Z action\n\nDo Z: keep `$$VAR`, `$&`, and `$'` literal.\n\n### v0.74.0\n\nOld.\n";
	assert.equal(await readFile(join(cwd, "MIGRATION.md"), "utf8"), stamped);
	assert.deepEqual(await readdir(join(cwd, ".migration")), ["README.md"]);

	// Regeneration re-reads the script's own prior output and must be byte-idempotent.
	await run(process.execPath, [join(import.meta.dirname, "sync-release-version.ts")], { cwd });
	assert.equal(await readFile(join(cwd, "MIGRATION.md"), "utf8"), stamped);
	assert.deepEqual(await readdir(join(cwd, ".migration")), ["README.md"]);

	// A fragment landing between regenerations merges into the same unreleased section.
	await writeFile(join(cwd, ".migration/late.md"), "#### 🔴 Late action\n");
	await run(process.execPath, [join(import.meta.dirname, "sync-release-version.ts")], { cwd });
	assert.equal(
		await readFile(join(cwd, "MIGRATION.md"), "utf8"),
		stamped.replace("\n### v0.74.0", "\n#### 🔴 Late action\n\n### v0.74.0"),
	);
	assert.deepEqual(await readdir(join(cwd, ".migration")), ["README.md"]);

	// A same-version heading below released history is a real conflict, not prior output.
	await writeFile(
		join(cwd, "MIGRATION.md"),
		`${await readFile(join(cwd, "MIGRATION.md"), "utf8")}\n### v0.75.0\n\nGhost.\n`,
	);
	await assert.rejects(
		run(process.execPath, [join(import.meta.dirname, "sync-release-version.ts")], { cwd }),
		/already contains ### v0\.75\.0/,
	);
	await writeFile(join(cwd, "MIGRATION.md"), `### Next release\n\n${stamped}`);
	await assert.rejects(
		run(process.execPath, [join(import.meta.dirname, "sync-release-version.ts")], { cwd }),
		/exactly one ### Next release/,
	);
});
