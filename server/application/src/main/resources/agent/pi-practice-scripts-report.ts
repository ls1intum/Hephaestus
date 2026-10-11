/**
 * What the practice script stage did for each staged practice, as `out/practice-scripts.json` carries it to the
 * server. The practice script runner writes `<slug>.json` whole when a practice ends, so a staged practice
 * without one did not finish. The report holds no tokens or calls: the proxy counts those.
 */
import { readdirSync, readFileSync } from "node:fs";
import path from "node:path";

import { isRecord } from "./pi-observation-normalize.ts";

/** The bounds the server reads `out/practice-scripts.json` within. */
export const PRACTICE_SCRIPT_REPORT_MAX_BYTES = 64 * 1024;
export const PRACTICE_SCRIPT_REPORT_MAX_PRACTICES = 256;
/** The most characters of a failed script's error that the report carries: `agent_job_practice_script_run.error`. */
export const PRACTICE_SCRIPT_ERROR_MAX_CHARS = 500;

/** The practice script runner's status of a practice that ended, as `<slug>.json` holds it: `RUN_STATUSES`. */
const ENDED = ["ok", "skipped", "error", "timeout"] as const;
type Ended = (typeof ENDED)[number];
const isEnded = (value: unknown): value is Ended => ENDED.some((status) => status === value);

/** "not-finished": the practice has no result. The server maps each status to its `PracticeScriptRunStatus`. */
export type PracticeScriptRunStatus = Ended | "not-finished";

/** One model slot of a script, in the practice script contract's own words (`lib/contract.ts`). */
export interface PracticeScriptModelUse {
	slot: string;
	need: string;
	bound: boolean;
	/** Calls that the runner could not rate, by reason. */
	notRated: Record<string, number>;
}

export interface PracticeScriptRun {
	slug: string;
	status: PracticeScriptRunStatus;
	leads: number;
	/**
	 * Empty for a script that uses no model. Absent when the models are not known: the script ended
	 * before it declared them, or did not finish.
	 */
	models?: PracticeScriptModelUse[];
	/** The first line of a failed script's error. Present only with the status "error". */
	error?: string;
	/** How long the script ran, in milliseconds. Absent when the practice did not finish. */
	durationMs?: number;
}

const count = (value: unknown): number | undefined =>
	typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : undefined;

function modelUses(models: unknown): PracticeScriptModelUse[] {
	if (!isRecord(models)) {
		return [];
	}
	return Object.entries(models).flatMap(([slot, use]) => {
		if (!isRecord(use) || typeof use.need !== "string" || typeof use.bound !== "boolean") {
			return [];
		}
		const notRated = isRecord(use.notRated)
			? Object.entries(use.notRated).flatMap(([reason, n]) => {
					const calls = count(n);
					return calls === undefined || calls === 0 ? [] : [[reason, calls] as const];
				})
			: [];
		return [{ slot, need: use.need, bound: use.bound, notRated: Object.fromEntries(notRated) }];
	});
}

/** Control characters and Unicode separators: what an error line loses at both ends. */
const BLANK_START = /^[\p{Cc}\p{Z}]+/u;
const BLANK_END = /[\p{Cc}\p{Z}]+$/u;

/**
 * The error line that the server stores, by one rule that `PiResultParser.errorLine` applies again to
 * what it reads. The fixture `practice-script-error-lines.json` holds cases that both sides must agree on.
 * 1. Remove control characters and separators (`\p{Cc}`, `\p{Z}`) at the start.
 * 2. Keep the text up to the first CR or LF.
 * 3. Cut it to {@link PRACTICE_SCRIPT_ERROR_MAX_CHARS} code points, as PostgreSQL counts characters.
 * 4. Replace each control character with a space, because PostgreSQL refuses NUL.
 * 5. Remove control characters and separators at the end.
 * 6. Give no line when nothing is left or the line holds an unpaired surrogate, which UTF-8 cannot
 *    encode.
 */
function errorLine(error: unknown): string | undefined {
	if (typeof error !== "string") {
		return undefined;
	}
	const [first = ""] = error.replace(BLANK_START, "").split(/[\r\n]/u, 1);
	// A code point takes at most two UTF-16 units, so the head holds the cut, and the rest of a long
	// line is never split into code points.
	// With the `u` flag, `.` matches one code point, an unpaired surrogate included.
	const cut = (first.slice(0, 2 * PRACTICE_SCRIPT_ERROR_MAX_CHARS).match(/./gsu) ?? [])
		.slice(0, PRACTICE_SCRIPT_ERROR_MAX_CHARS)
		.join("");
	const line = cut.replaceAll(/\p{Cc}/gu, " ").replace(BLANK_END, "");
	return line === "" || !line.isWellFormed() ? undefined : line;
}

function runOf(slug: string, file: string): PracticeScriptRun {
	const notFinished: PracticeScriptRun = { slug, status: "not-finished", leads: 0 };
	let result: unknown;
	try {
		result = JSON.parse(readFileSync(file, "utf8"));
	} catch {
		return notFinished;
	}
	if (!isRecord(result)) {
		return notFinished;
	}
	const { status, contract } = result;
	if (!isEnded(status)) {
		return notFinished;
	}
	const { leads } = result;
	const error = status === "error" ? errorLine(result.error) : undefined;
	const durationMs = count(result.durationMs);
	const run: PracticeScriptRun = {
		slug,
		status,
		leads: Array.isArray(leads) ? leads.length : 0,
		...(error === undefined ? {} : { error }),
		...(durationMs === undefined ? {} : { durationMs }),
	};
	// Only a script whose `meta` the runner accepted has known models.
	return contract === "definition" ? { ...run, models: modelUses(result.models) } : run;
}

/**
 * The report of every practice staged in `stagedDir` (`<slug>.ts`), by slug, from the results in
 * `outputDir`, or undefined when no practice was staged. An error only explains a failure, so the
 * errors get the bytes that the runs leave, in slug order, and a run whose error does not fit is
 * reported without it. Past either bound, the practices that sort last are left out and `truncated`
 * is true, so the server reads no claim about a practice that the report does not name.
 */
export function practiceScriptReport(stagedDir: string, outputDir: string): string | undefined {
	let staged: string[];
	try {
		staged = readdirSync(stagedDir, { withFileTypes: true })
			.filter((entry) => entry.isFile() && entry.name.endsWith(".ts"))
			.map((entry) => entry.name.slice(0, -".ts".length))
			.toSorted();
	} catch {
		return undefined;
	}
	if (staged.length === 0) {
		return undefined;
	}
	const kept: { run: PracticeScriptRun; entry: string }[] = [];
	let bytes = Buffer.byteLength('{"practices":[],"truncated":false}');
	for (const slug of staged.slice(0, PRACTICE_SCRIPT_REPORT_MAX_PRACTICES)) {
		const run = runOf(slug, path.join(outputDir, `${slug}.json`));
		const { error: _error, ...withoutError } = run;
		const entry = JSON.stringify(withoutError);
		const added = Buffer.byteLength(entry) + (kept.length === 0 ? 0 : 1);
		if (bytes + added > PRACTICE_SCRIPT_REPORT_MAX_BYTES) {
			break;
		}
		bytes += added;
		kept.push({ run, entry });
	}
	let withoutErrors = 0;
	for (const item of kept) {
		if (item.run.error === undefined) {
			continue;
		}
		const entry = JSON.stringify(item.run);
		const added = Buffer.byteLength(entry) - Buffer.byteLength(item.entry);
		if (bytes + added > PRACTICE_SCRIPT_REPORT_MAX_BYTES) {
			withoutErrors += 1;
			continue;
		}
		bytes += added;
		item.entry = entry;
	}
	const truncated = kept.length < staged.length;
	if (truncated) {
		console.error(
			`[pi-runner] practice-scripts.json reports ${kept.length} of ${staged.length} staged practices; the rest exceed its bounds`,
		);
	}
	if (withoutErrors > 0) {
		console.error(
			`[pi-runner] practice-scripts.json leaves out the error of ${withoutErrors} failed practices; they exceed its byte bound`,
		);
	}
	return `{"practices":[${kept.map((item) => item.entry).join(",")}],"truncated":${String(truncated)}}`;
}
