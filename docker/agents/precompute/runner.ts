#!/usr/bin/env bun
/**
 * Precomputation runner — executes per-practice static analysis scripts.
 *
 * Produces .precompute/summary.md and .precompute/{slug}.json for each practice
 * that has a script injected from the DB into {output}/practices/{slug}.ts.
 *
 * Scripts are the ONLY source — there are no baked-in practice scripts.
 * The runner + shared libs are infrastructure; practice scripts are dynamic data.
 *
 * Usage:
 *   bun run runner.ts --repo <path> --diff <path> [--metadata <path>] [--output <path>]
 */
import { parseArgs } from "util";
import { mkdir, rename, rm } from "fs/promises";
import { existsSync } from "fs";
import { parseDiff } from "./lib/diff-parser";
import type { PracticeResult, DiffFile } from "./lib/types";

const { values } = parseArgs({
  args: Bun.argv.slice(2),
  options: {
    repo: { type: "string" },
    diff: { type: "string" },
    metadata: { type: "string" },
    output: { type: "string", default: ".precompute" },
    timeout: { type: "string", default: "15000" },
  },
});

if (!values.repo) {
  console.error("Usage: bun run runner.ts --repo <path> --diff <path> [--metadata <path>] [--output <dir>]");
  process.exit(1);
}

const globalStart = Date.now();
const repoPath = values.repo;
const outputDir = values.output!;
const timeoutMs = parseInt(values.timeout!);

// Parse diff
let diffFiles = new Map<string, DiffFile>();
if (values.diff) {
  try {
    const diffContent = await Bun.file(values.diff).text();
    diffFiles = parseDiff(diffContent);
    console.error(`Parsed diff: ${diffFiles.size} files`);
  } catch (e) {
    console.error(`Could not parse diff: ${e}`);
  }
}

// Load metadata
let metadata: any = {};
if (values.metadata) {
  try {
    metadata = await Bun.file(values.metadata).json();
  } catch (e) {
    console.error(`Could not load metadata: ${e}`);
  }
}

// Discover practice scripts — ONLY from the injected directory (from DB precomputeScript).
// There are no baked-in practice scripts. Scripts are data, stored per-practice in the DB.
const practicesDir = `${outputDir}/practices`;
const practiceModules: [string, string][] = [];

if (existsSync(practicesDir)) {
  const glob = new Bun.Glob("*.ts");
  for (const file of glob.scanSync(practicesDir)) {
    const slug = file.replace(/\.ts$/, "");
    practiceModules.push([slug, `${practicesDir}/${file}`]);
  }
}

if (practiceModules.length === 0) {
  console.error("No practice scripts found. Exiting.");
  // Create minimal output so agent knows precompute ran but found nothing
  await mkdir(outputDir, { recursive: true });
  await Bun.write(`${outputDir}/summary.md`, "# Precomputed Analysis\n\n> No practice scripts available.\n");
  process.exit(0);
}

console.error(`Running ${practiceModules.length} practice analyzer(s)...`);

// Write to temp dir first, then atomic rename to output
const tmpDir = `${outputDir}.tmp.${process.pid}`;
await rm(tmpDir, { recursive: true, force: true });
await mkdir(tmpDir, { recursive: true });

/**
 * Validate that a script return value has the expected PracticeResult shape.
 * Throws on invalid shape so the caller can catch and produce an error result.
 */
function validateResult(result: unknown, slug: string): PracticeResult {
  if (!result || typeof result !== "object") {
    throw new Error(`Script ${slug} must return an object`);
  }
  const r = result as any;
  if (!Array.isArray(r.hints)) throw new Error(`Script ${slug}: hints must be an array`);
  if (typeof r.metrics !== "object" || r.metrics === null) throw new Error(`Script ${slug}: metrics must be an object`);
  if (!Array.isArray(r.directions)) throw new Error(`Script ${slug}: directions must be an array`);
  // Force practice name to match the filename slug — single source of truth
  return {
    practice: slug,
    status: "ok",
    hints: r.hints,
    metrics: r.metrics,
    directions: r.directions.slice(0, 10), // cap directions to prevent bloat
  };
}

// Run all practices in parallel
const results = await Promise.allSettled(
  practiceModules.map(async ([slug, modulePath]) => {
    const start = Date.now();
    try {
      const mod = await import(modulePath);
      const rawResult = await Promise.race([
        mod.default(repoPath, diffFiles, metadata),
        new Promise<never>((_, reject) =>
          setTimeout(() => reject(new Error(`Timeout after ${timeoutMs}ms`)), timeoutMs)
        ),
      ]);
      const result = validateResult(rawResult, slug);
      const elapsed = Date.now() - start;
      console.error(`  ok ${slug}: ${result.hints.length} hints (${elapsed}ms)`);
      return result;
    } catch (e: any) {
      const elapsed = Date.now() - start;
      console.error(`  FAIL ${slug}: ${e.message} (${elapsed}ms)`);
      return {
        practice: slug,
        status: "error" as const,
        hints: [],
        metrics: { error: 1 },
        directions: [`Script failed: ${e.message}`],
      } satisfies PracticeResult;
    }
  })
);

const practiceResults: PracticeResult[] = results.map(r =>
  r.status === "fulfilled" ? r.value : { practice: "unknown", status: "error" as const, hints: [], metrics: { error: 1 }, directions: ["Promise rejected"] }
);

// Write per-practice JSON
for (const result of practiceResults) {
  await Bun.write(`${tmpDir}/${result.practice}.json`, JSON.stringify(result, null, 2));
}

// Generate summary.md
const lines: string[] = [
  "# Precomputed Analysis Hints",
  "",
  "> These are **verified facts and directions** from static analysis.",
  "> Use them as starting points — investigate further for things the scripts may have missed.",
  "",
];

const errors = practiceResults.filter(r => r.status === "error");
if (errors.length > 0) {
  lines.push(`> **${errors.length} script(s) failed** — perform full manual analysis for: ${errors.map(e => e.practice).join(", ")}`, "");
}

for (const result of practiceResults) {
  const inDiffHints = result.hints.filter(h => h.inDiff);

  lines.push(`## ${result.practice}`);
  if (result.status === "error") {
    lines.push("", `> **Script failed.** Agent must analyze this practice manually.`);
  }
  lines.push("");

  for (const d of result.directions) {
    lines.push(`- ${d}`);
  }
  lines.push("");

  if (inDiffHints.length > 0 && inDiffHints.length <= 10) {
    lines.push("**Key locations (on changed lines):**");
    for (const h of inDiffHints) {
      // Render ALL flag types (boolean, number, string), not just boolean=true
      const flagEntries = Object.entries(h.flags).filter(([, v]) => v !== false && v !== 0 && v !== "");
      const flagStr = flagEntries.map(([k, v]) => v === true ? k : `${k}=${v}`).join(", ");
      lines.push(`- \`${h.file}:${h.line}\` — ${h.pattern}${flagStr ? ` [${flagStr}]` : ""}: \`${h.context.slice(0, 100)}\``);
    }
    lines.push("");
  } else if (inDiffHints.length > 10) {
    lines.push(`**${inDiffHints.length} hints on changed lines** — see \`.precompute/${result.practice}.json\` for full list.`);
    for (const h of inDiffHints.slice(0, 5)) {
      lines.push(`- \`${h.file}:${h.line}\` — ${h.pattern}: \`${h.context.slice(0, 80)}\``);
    }
    lines.push(`- ... and ${inDiffHints.length - 5} more`, "");
  }
}

await Bun.write(`${tmpDir}/summary.md`, lines.join("\n"));

// Timing file for observability — handler can read this from output files
const totalHints = practiceResults.reduce((s, r) => s + r.hints.length, 0);
const inDiffHints = practiceResults.reduce((s, r) => s + r.hints.filter(h => h.inDiff).length, 0);
const errorCount = errors.length;
await Bun.write(`${tmpDir}/.timing.json`, JSON.stringify({
  durationMs: Date.now() - globalStart,
  practices: practiceResults.length,
  totalHints,
  inDiffHints,
  errors: errorCount,
}));

// Sentinel file to indicate successful completion
await Bun.write(`${tmpDir}/.complete`, new Date().toISOString());

// Atomic rename: tmpDir → outputDir (same filesystem, so rename(2) is atomic)
// Preserve the practices/ subdirectory (contains injected scripts from DB)
if (existsSync(outputDir)) {
  // Move practices dir out before replacing, then move back in
  const practicesBak = `${outputDir}.practices.bak.${process.pid}`;
  if (existsSync(practicesDir)) {
    await rename(practicesDir, practicesBak);
  }
  await rm(outputDir, { recursive: true, force: true });
  await rename(tmpDir, outputDir);
  // Restore practices dir
  if (existsSync(practicesBak)) {
    await rename(practicesBak, `${outputDir}/practices`);
  }
} else {
  await rename(tmpDir, outputDir);
}

// Structured log for observability
console.error(JSON.stringify({
  event: "precompute_complete",
  practices: practiceResults.length,
  totalHints,
  inDiffHints,
  errors: errorCount,
  durationMs: Date.now() - globalStart,
}));
