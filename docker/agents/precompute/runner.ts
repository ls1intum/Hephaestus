#!/usr/bin/env bun
import { existsSync } from "node:fs";
import { mkdir, rename, rm } from "node:fs/promises";
/**
 * Precomputation runner — executes per-practice static analysis scripts.
 *
 * Produces {output}/summary.md and {output}/{slug}.json for each practice
 * that has a script injected from the DB into {output}/practices/{slug}.ts.
 *
 * Scripts are the ONLY source — there are no baked-in practice scripts.
 * The runner + shared libs are infrastructure; practice scripts are dynamic data.
 *
 * Usage:
 *   bun run runner.ts --repo <path> --diff <path> [--metadata <path>] [--output <path>]
 */
import { parseArgs } from "node:util";
import { parseDiff } from "./lib/diff-parser";
import { isJsonObject, isPracticeModule, parseFindings } from "./lib/practice-contract";
import type { ArtifactMetadata, DiffFile, PracticeResult } from "./lib/types";

const DEFAULT_OUTPUT_DIR = ".precompute";
const DEFAULT_TIMEOUT_MS = 15_000;

const { values } = parseArgs({
	args: Bun.argv.slice(2),
	options: {
		repo: { type: "string" },
		diff: { type: "string" },
		metadata: { type: "string" },
		context: { type: "string" },
		output: { type: "string", default: DEFAULT_OUTPUT_DIR },
		timeout: { type: "string", default: String(DEFAULT_TIMEOUT_MS) },
	},
});

if (!values.repo) {
	console.error(
		"Usage: bun run runner.ts --repo <path> --diff <path> [--metadata <path>] [--context <dir>] [--output <dir>]",
	);
	process.exit(1);
}

const globalStart = Date.now();
const repoPath = values.repo;
const outputDir = values.output;
// A non-numeric or non-positive --timeout would make every race timer fire immediately and time out
// every practice on the spot, so an unusable value falls back to the default instead of silently
// disabling precompute.
const requestedTimeoutMs = Number.parseInt(values.timeout, 10);
const timeoutIsUsable = Number.isFinite(requestedTimeoutMs) && requestedTimeoutMs > 0;
if (!timeoutIsUsable) {
	console.error(`Ignoring unusable --timeout ${values.timeout}; using ${DEFAULT_TIMEOUT_MS}ms`);
}
const timeoutMs = timeoutIsUsable ? requestedTimeoutMs : DEFAULT_TIMEOUT_MS;
// The materialised context directory (inputs/context/) — gives scripts read access to the SAME
// cross-artifact context the agent sees (project_inventory.json, linked_work_items.json, comments.json,
// issue_summary.md, …), so a precompute can point the LLM at relevant neighbours. Optional and read-only.
const contextDir =
	values.context ?? (values.metadata ? values.metadata.replace(/\/[^/]*$/, "") : "");

// Parse diff
let diffFiles = new Map<string, DiffFile>();
if (values.diff) {
	try {
		const diffContent = await Bun.file(values.diff).text();
		diffFiles = parseDiff(diffContent);
		console.error(`Parsed diff: ${diffFiles.size} files`);
	} catch (e) {
		console.error(`Could not parse diff: ${String(e)}`);
	}
}

// Load metadata
let metadata: ArtifactMetadata = {};
if (values.metadata) {
	try {
		const parsed: unknown = await Bun.file(values.metadata).json();
		if (isJsonObject(parsed)) {
			metadata = parsed;
		} else {
			console.error(`Metadata ${values.metadata} is not a JSON object; scripts will see {}`);
		}
	} catch (e) {
		console.error(`Could not load metadata: ${String(e)}`);
	}
}

// Practice scripts come ONLY from the injected directory (DB precomputeScript); there are no
// baked-in scripts. Scripts are data, stored per-practice in the DB.
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
	// Minimal output so the agent knows precompute ran but found nothing.
	await mkdir(outputDir, { recursive: true });
	await Bun.write(
		`${outputDir}/summary.md`,
		"# Precomputed Analysis\n\n> No practice scripts available.\n",
	);
	process.exit(0);
}

console.error(`Running ${practiceModules.length} practice analyzer(s)...`);

// Write to temp dir first, then atomic rename to output
const tmpDir = `${outputDir}.tmp.${process.pid}`;
await rm(tmpDir, { recursive: true, force: true });
await mkdir(tmpDir, { recursive: true });

/** A practice script is foreign code (DB-stored data), so it can reject with a non-Error value. */
function messageOf(error: unknown): string {
	return error instanceof Error ? error.message : String(error);
}

/**
 * Cap how long one practice script may run. The timer is cleared once the race settles: Bun keeps the
 * process alive until every pending timer has fired, so an uncleared one would hold the whole runner
 * open for the remainder of the timeout after the last practice had already produced its result.
 */
async function withTimeout<T>(work: T | Promise<T>): Promise<T> {
	let timer: ReturnType<typeof setTimeout> | undefined;
	try {
		return await Promise.race([
			work,
			new Promise<never>((_, reject) => {
				timer = setTimeout(() => reject(new Error(`Timeout after ${timeoutMs}ms`)), timeoutMs);
			}),
		]);
	} finally {
		clearTimeout(timer);
	}
}

/**
 * Validate that a script return value has the expected PracticeFindings shape.
 * Throws on invalid shape so the caller can catch and produce an error result.
 */
function validateResult(result: unknown, slug: string): PracticeResult {
	const findings = parseFindings(result, `Script ${slug}`);
	// Force practice name to match the filename slug — single source of truth
	return {
		practice: slug,
		status: "ok",
		hints: findings.hints,
		metrics: findings.metrics,
		directions: findings.directions.slice(0, 10), // cap directions to prevent bloat
	};
}

// Run all practices in parallel
const results = await Promise.allSettled(
	practiceModules.map(async ([slug, modulePath]) => {
		const start = Date.now();
		try {
			const mod: unknown = await import(modulePath);
			if (!isPracticeModule(mod)) {
				throw new Error(`Script ${slug} must export a default function`);
			}
			const rawResult: unknown = await withTimeout(
				mod.default(repoPath, diffFiles, metadata, contextDir),
			);
			const result = validateResult(rawResult, slug);
			const elapsed = Date.now() - start;
			console.error(`  ok ${slug}: ${result.hints.length} hints (${elapsed}ms)`);
			return result;
		} catch (e) {
			const elapsed = Date.now() - start;
			const message = messageOf(e);
			console.error(`  FAIL ${slug}: ${message} (${elapsed}ms)`);
			return {
				practice: slug,
				status: "error" as const,
				hints: [],
				metrics: { error: 1 },
				directions: [`Script failed: ${message}`],
			} satisfies PracticeResult;
		}
	}),
);

const practiceResults: PracticeResult[] = results.map((r) =>
	r.status === "fulfilled"
		? r.value
		: {
				practice: "unknown",
				status: "error" as const,
				hints: [],
				metrics: { error: 1 },
				directions: ["Promise rejected"],
			},
);

// Write per-practice JSON
for (const result of practiceResults) {
	await Bun.write(`${tmpDir}/${result.practice}.json`, JSON.stringify(result, null, 2));
}

// Generate summary.md
const lines: string[] = [
	"# Precomputed Analysis Hints",
	"",
	"> These are **pattern matches and directions to investigate** from static analysis — starting points, not verdicts.",
	"> Use them as starting points — investigate further for things the scripts may have missed.",
	"",
];

const errors = practiceResults.filter((r) => r.status === "error");
if (errors.length > 0) {
	lines.push(
		`> **${errors.length} script(s) failed** — perform full manual analysis for: ${errors.map((e) => e.practice).join(", ")}`,
		"",
	);
}

for (const result of practiceResults) {
	const inDiffHints = result.hints.filter((h) => h.inDiff);

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
			const flagEntries = Object.entries(h.flags).filter(
				([, v]) => v !== false && v !== 0 && v !== "",
			);
			const flagStr = flagEntries.map(([k, v]) => (v === true ? k : `${k}=${v}`)).join(", ");
			lines.push(
				`- \`${h.file}:${h.line}\` — ${h.pattern}${flagStr ? ` [${flagStr}]` : ""}: \`${h.context.slice(0, 100)}\``,
			);
		}
		lines.push("");
	} else if (inDiffHints.length > 10) {
		lines.push(
			`**${inDiffHints.length} hints on changed lines** — see \`${outputDir}/${result.practice}.json\` for full list.`,
		);
		for (const h of inDiffHints.slice(0, 5)) {
			lines.push(`- \`${h.file}:${h.line}\` — ${h.pattern}: \`${h.context.slice(0, 80)}\``);
		}
		lines.push(`- ... and ${inDiffHints.length - 5} more`, "");
	}
}

await Bun.write(`${tmpDir}/summary.md`, lines.join("\n"));

// Timing file for observability — handler can read this from output files
const totalHints = practiceResults.reduce((s, r) => s + r.hints.length, 0);
const inDiffHints = practiceResults.reduce((s, r) => s + r.hints.filter((h) => h.inDiff).length, 0);
const errorCount = errors.length;
await Bun.write(
	`${tmpDir}/.timing.json`,
	JSON.stringify({
		durationMs: Date.now() - globalStart,
		practices: practiceResults.length,
		totalHints,
		inDiffHints,
		errors: errorCount,
	}),
);

// Sentinel file to indicate successful completion
await Bun.write(`${tmpDir}/.complete`, new Date().toISOString());

// Replace outputDir, preserving the injected practices/ subdir (contains scripts from DB).
// Single-writer precompute step; the replace sequence below is NOT concurrency-safe (there is a
// window after rm where outputDir does not exist). The no-existing-dir branch is a single atomic rename.
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
console.error(
	JSON.stringify({
		event: "precompute_complete",
		practices: practiceResults.length,
		totalHints,
		inDiffHints,
		errors: errorCount,
		durationMs: Date.now() - globalStart,
	}),
);
