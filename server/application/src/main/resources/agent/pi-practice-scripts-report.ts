/**
 * How the program of each admitted practice ran, as `out/practice-scripts.json` carries it to the server: one
 * entry for each admitted practice, the built-in included, sorted by slug. It holds no candidate, and no tokens:
 * the proxy counts those.
 */
import {
	EXECUTION_REPORT_SCHEMA_VERSION,
	type ExecutionEntry,
} from "./work/practice-scripts/lib/contract.ts";
import type { ProgramOutcome } from "./work/practice-scripts/lib/program-host.ts";

/** The bounds the server reads `out/practice-scripts.json` within. */
export const PRACTICE_SCRIPT_REPORT_MAX_BYTES = 64 * 1024;
export const PRACTICE_SCRIPT_REPORT_MAX_PRACTICES = 256;
/** The most characters of a failed program's error that the report carries: `agent_job_practice_script_run.error`. */
export const PRACTICE_SCRIPT_ERROR_MAX_CHARS = 500;

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
export function errorLine(error: unknown): string | undefined {
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

/** One practice's entry: what its program was, how it ran, and why it stopped. */
function entryOf(slug: string, outcome: ProgramOutcome): ExecutionEntry {
	const error = outcome.status === "failed" ? errorLine(outcome.error) : undefined;
	return {
		practiceSlug: slug,
		program: outcome.program,
		models: outcome.models.map(({ slot, need, bound, notRated }) => ({
			slot,
			need,
			bound,
			notRated,
		})),
		status: outcome.status,
		stopReason: outcome.stopReason,
		...(error === undefined ? {} : { error }),
		calls: outcome.calls,
		coverageReached: outcome.coverageReached,
		durationMs: outcome.durationMs,
	};
}

/**
 * The report of the given practices, sorted by slug. An error only explains a failure, so the errors get the
 * bytes that the entries leave, in slug order, and an entry whose error does not fit is reported without it.
 * Past either bound, the practices that sort last are left out and `truncated` is true, so the server reads no
 * claim about a practice that the report does not name.
 */
export function executionReport(practices: readonly (readonly [string, ProgramOutcome])[]): string {
	// By UTF-16 code unit, as Java compares strings.
	const sorted = practices.toSorted(([a], [b]) => Number(a > b) - Number(a < b));
	const kept: { entry: ExecutionEntry; text: string }[] = [];
	let bytes = Buffer.byteLength(
		`{"schemaVersion":${EXECUTION_REPORT_SCHEMA_VERSION},"truncated":false,"practices":[]}`,
	);
	for (const [slug, outcome] of sorted.slice(0, PRACTICE_SCRIPT_REPORT_MAX_PRACTICES)) {
		const entry = entryOf(slug, outcome);
		const { error: _error, ...withoutError } = entry;
		const text = JSON.stringify(withoutError);
		const added = Buffer.byteLength(text) + (kept.length === 0 ? 0 : 1);
		if (bytes + added > PRACTICE_SCRIPT_REPORT_MAX_BYTES) {
			break;
		}
		bytes += added;
		kept.push({ entry, text });
	}
	let withoutErrors = 0;
	for (const item of kept) {
		if (item.entry.error === undefined) {
			continue;
		}
		const text = JSON.stringify(item.entry);
		const added = Buffer.byteLength(text) - Buffer.byteLength(item.text);
		if (bytes + added > PRACTICE_SCRIPT_REPORT_MAX_BYTES) {
			withoutErrors += 1;
			continue;
		}
		bytes += added;
		item.text = text;
	}
	const truncated = kept.length < sorted.length;
	if (truncated) {
		console.error(
			`[pi-runner] practice-scripts.json reports ${kept.length} of ${sorted.length} admitted practices; the rest exceed its bounds`,
		);
	}
	if (withoutErrors > 0) {
		console.error(
			`[pi-runner] practice-scripts.json leaves out the error of ${withoutErrors} failed practices; they exceed its byte bound`,
		);
	}
	return `{"schemaVersion":${EXECUTION_REPORT_SCHEMA_VERSION},"truncated":${String(truncated)},"practices":[${kept.map((item) => item.text).join(",")}]}`;
}
