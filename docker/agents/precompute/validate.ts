#!/usr/bin/env bun
/**
 * Validate precompute scripts WITHOUT the sandbox: runs each script over a real repo + diff exactly as the
 * runner does, checks the PracticeResult shape, and prints the metrics/directions/hints so you can eyeball
 * that the feature extraction is meaningful.
 *
 * Usage: bun run validate.ts --repo <clone> [--diff <patch>] [--scripts <dir>] [--metadata <json>]
 *   --scripts defaults to ../../../server/src/main/resources/practices/precompute (the version-controlled home)
 */
import { parseArgs } from "util";
import { mkdir, cp, rm, symlink } from "fs/promises";
import { existsSync } from "fs";
import { join, resolve } from "path";

const { values } = parseArgs({
  args: Bun.argv.slice(2),
  options: {
    repo: { type: "string" },
    diff: { type: "string" },
    metadata: { type: "string" },
    context: { type: "string" },
    scripts: { type: "string", default: resolve(import.meta.dir, "../../../server/src/main/resources/practices/precompute") },
  },
});
if (!values.repo) { console.error("--repo required"); process.exit(2); }

const work = `/tmp/pc-validate.${process.pid}`;
await rm(work, { recursive: true, force: true });
await mkdir(`${work}/practices`, { recursive: true });
await symlink(resolve(import.meta.dir, "lib"), `${work}/lib`);
// copy only *.ts script files (skip the lib dir if scripts== a dir containing one)
const glob = new Bun.Glob("*.ts");
let n = 0;
for (const f of glob.scanSync(values.scripts!)) { await cp(join(values.scripts!, f), `${work}/practices/${f}`); n++; }
console.error(`Validating ${n} script(s) from ${values.scripts}`);

const args = ["run", resolve(import.meta.dir, "runner.ts"), "--repo", values.repo!, "--output", work];
if (values.diff) args.push("--diff", values.diff);
if (values.metadata) args.push("--metadata", values.metadata);
if (values.context) args.push("--context", values.context);
const proc = Bun.spawnSync(["bun", ...args], { stderr: "inherit" });

// Inspect results
const REQUIRED = ["practice", "status", "hints", "metrics", "directions"];
let fail = 0;
for (const f of new Bun.Glob("*.json").scanSync(work)) {
  const r = await Bun.file(`${work}/${f}`).json();
  const missing = REQUIRED.filter((k) => !(k in r));
  const ok = missing.length === 0 && r.status === "ok";
  if (!ok) fail++;
  console.log(`\n${ok ? "✅" : "❌"} ${r.practice}  [status=${r.status}${missing.length ? " missing=" + missing : ""}]`);
  console.log(`   metrics: ${JSON.stringify(r.metrics)}`);
  for (const d of r.directions) console.log(`   • ${d}`);
  if (r.hints?.length) console.log(`   hints: ${r.hints.length} (e.g. ${r.hints[0].file}:${r.hints[0].line} ${r.hints[0].pattern})`);
}
console.log(`\n${fail === 0 ? "ALL SCRIPTS VALID" : fail + " SCRIPT(S) INVALID"}`);
process.exit(fail === 0 ? 0 : 1);
