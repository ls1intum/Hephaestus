#!/usr/bin/env bun
import { cp, mkdir, rm, symlink } from "node:fs/promises";
import { join, resolve } from "node:path";
/**
 * Validate precompute scripts WITHOUT the sandbox: runs each script over a real repo + diff exactly as the
 * runner does, checks the PracticeResult shape, and prints the metrics/directions/hints so you can eyeball
 * that the feature extraction is meaningful.
 *
 * Usage: bun run validate.ts --repo <clone> [--diff <patch>] [--scripts <dir>] [--metadata <json>]
 *   --scripts defaults to ../../../server/src/main/resources/practices/precompute (the version-controlled home)
 */
import { parseArgs } from "node:util";
import { parsePracticeResult } from "./lib/practice-contract";
import type { PracticeResult } from "./lib/types";

const DEFAULT_SCRIPTS_DIR = resolve(
	import.meta.dir,
	"../../../server/src/main/resources/practices/precompute",
);

const { values } = parseArgs({
	args: Bun.argv.slice(2),
	options: {
		repo: { type: "string" },
		diff: { type: "string" },
		metadata: { type: "string" },
		context: { type: "string" },
		scripts: { type: "string", default: DEFAULT_SCRIPTS_DIR },
	},
});
if (!values.repo) {
	console.error("--repo required");
	process.exit(2);
}

const scriptsDir = values.scripts ?? DEFAULT_SCRIPTS_DIR;

const work = `/tmp/pc-validate.${process.pid}`;
await rm(work, { recursive: true, force: true });
await mkdir(`${work}/practices`, { recursive: true });
await symlink(resolve(import.meta.dir, "lib"), `${work}/lib`);
// copy only *.ts script files (skip the lib dir if scripts== a dir containing one)
const glob = new Bun.Glob("*.ts");
let n = 0;
for (const f of glob.scanSync(scriptsDir)) {
	await cp(join(scriptsDir, f), `${work}/practices/${f}`);
	n++;
}
console.error(`Validating ${n} script(s) from ${scriptsDir}`);

const args = [
	"run",
	resolve(import.meta.dir, "runner.ts"),
	"--repo",
	values.repo,
	"--output",
	work,
];
if (values.diff) args.push("--diff", values.diff);
if (values.metadata) args.push("--metadata", values.metadata);
if (values.context) args.push("--context", values.context);
const _proc = Bun.spawnSync(["bun", ...args], { stderr: "inherit" });

// Inspect results. The written {slug}.json is checked against the SAME contract the runner enforces
// on a script's return value (lib/practice-contract.ts), so this tool cannot drift from the runner.
let fail = 0;
for (const f of new Bun.Glob("*.json").scanSync(work)) {
	let result: PracticeResult;
	try {
		result = parsePracticeResult(await Bun.file(join(work, f)).json(), f);
	} catch (e) {
		fail++;
		console.log(`\n❌ ${f}  [${e instanceof Error ? e.message : String(e)}]`);
		continue;
	}

	const ok = result.status === "ok";
	if (!ok) fail++;
	console.log(`\n${ok ? "✅" : "❌"} ${result.practice}  [status=${result.status}]`);
	console.log(`   metrics: ${JSON.stringify(result.metrics)}`);
	for (const d of result.directions) console.log(`   • ${d}`);
	const [firstHint] = result.hints;
	if (firstHint) {
		console.log(
			`   hints: ${result.hints.length} (e.g. ${firstHint.file}:${firstHint.line} ${firstHint.pattern})`,
		);
	}
}
console.log(`\n${fail === 0 ? "ALL SCRIPTS VALID" : `${fail} SCRIPT(S) INVALID`}`);
process.exit(fail === 0 ? 0 : 1);
