/**
 * The runner's side of a script's result. The runner writes each quote from the source, so
 * a lead cannot quote words that the source does not hold.
 */
import { realpathSync, statSync, readFileSync } from "node:fs";
import path from "node:path";

import { oneLine } from "./bounded-text.ts";
import {
	isFailed,
	type Citation,
	type Lead,
	type ModelSlot,
	type Need,
	type PracticeScriptResult,
	type Reason,
	type RunStatus,
	type Unrated,
} from "./contract.ts";
import type { DiffFile } from "./types.ts";

/** A file larger than this is not quoted from. */
const MAX_QUOTED_FILE_BYTES = 2 * 1024 * 1024;
/** Lines one quote may span. */
const MAX_QUOTE_LINES = 20;
/**
 * A practice's section is inlined beside its criteria in the turn that evaluates it, so each section is
 * bounded on its own: one busy script cannot crowd another practice's leads out of the prompt.
 */
const SECTION_CHARS = 3000;
/**
 * The most of a section that its notices take: why the script failed or did not run, and what it did
 * not rate. The rest of the section can never push them out.
 */
const NOTICE_CHARS = 1500;
/** The longest notice line: a failure message can be long. */
const NOTICE_LINE_CHARS = 300;

/** The first line of the section of a script that failed. */
const SCRIPT_FAILED = "> **Script failed.** Agent must analyze this practice manually.";

export interface Sources {
	change: Map<string, DiffFile>;
	repo: string;
	contextDir: string;
	contextReference: string;
}

interface QuotedLead extends Lead {
	quote: string;
	/** The lead cites a line that the change since the previous review added. */
	newSinceLastReview?: true;
}

/** A path inside `root`, symlinks resolved, naming a regular file; otherwise undefined. */
function fileInside(root: string, relative: string): string | undefined {
	try {
		const base = realpathSync(root);
		const full = realpathSync(path.resolve(base, relative));
		if (full !== base && !full.startsWith(`${base}${path.sep}`)) {
			return undefined;
		}
		const stats = statSync(full);
		return stats.isFile() && stats.size <= MAX_QUOTED_FILE_BYTES ? full : undefined;
	} catch {
		return undefined;
	}
}

function linesOf(
	lines: ReadonlyMap<number, string>,
	start: number,
	end: number,
): string | undefined {
	if (end < start || end - start >= MAX_QUOTE_LINES) {
		return undefined;
	}
	const picked: string[] = [];
	for (let n = start; n <= end; n += 1) {
		const text = lines.get(n);
		if (text === undefined) {
			return undefined;
		}
		picked.push(text);
	}
	return picked.join("\n");
}

function numbered(file: string): Map<number, string> {
	return new Map(
		readFileSync(file, "utf8")
			.split("\n")
			.map((text, i) => [i + 1, text]),
	);
}

/** The text a citation names, or undefined when the source does not hold it. */
function quoteOf(citation: Citation, sources: Sources): string | undefined {
	if ("change" in citation) {
		const file = sources.change.get(citation.change);
		if (file === undefined) {
			return undefined;
		}
		const side = citation.side === "OLD" ? file.removedLines : file.addedLines;
		return linesOf(side, citation.line, citation.endLine ?? citation.line);
	}
	if ("file" in citation) {
		const file = fileInside(sources.repo, citation.file);
		return file === undefined
			? undefined
			: linesOf(numbered(file), citation.line, citation.endLine ?? citation.line);
	}
	if (sources.contextDir === "") {
		return undefined;
	}
	const file = fileInside(sources.contextDir, citation.record);
	if (file === undefined) {
		return undefined;
	}
	// A record cited as a whole (line 0 or none) is quoted by name only.
	return citation.line === undefined || citation.line === 0
		? ""
		: linesOf(numbered(file), citation.line, citation.line);
}

/**
 * Keep the leads whose kind the script declared and whose citation the source holds, each with the
 * quote the runner read. The rest are counted.
 */
export function settleLeads(
	result: PracticeScriptResult,
	kinds: Record<string, string>,
	sources: Sources,
): { leads: QuotedLead[]; dropped: number } {
	const leads: QuotedLead[] = [];
	let dropped = 0;
	for (const lead of result.leads) {
		const quote = Object.hasOwn(kinds, lead.kind) ? quoteOf(lead.at, sources) : undefined;
		if (quote === undefined) {
			dropped += 1;
		} else {
			leads.push({ ...lead, quote });
		}
	}
	return { leads, dropped };
}

/**
 * One slot of a script: what it needs, whether a model was bound, and its calls that the
 * runner could not rate, by reason. The proxy counts calls and tokens.
 */
export interface SlotReport {
	need: Need;
	bound: boolean;
	notRated: Partial<Record<Reason, number>>;
}

/**
 * What a script did, as `<slug>.json` records it. "unknown": the script failed before the runner
 * accepted its `meta`, or never started, so its models are not known.
 */
export interface ScriptResult {
	practice: string;
	contract: "definition" | "unknown";
	status: RunStatus;
	models: Partial<Record<ModelSlot, SlotReport>>;
	leads: QuotedLead[];
	facts: PracticeScriptResult["facts"];
	unrated: Unrated[];
	directions: string[];
	kinds: Record<string, string>;
	dropped: number;
	/**
	 * How many leads cite a line that the change since the previous review added. Absent when that
	 * change is not known: no previous review, or a previous head that is not an ancestor.
	 */
	newSinceLastReview?: number;
	/** Why the script failed or was stopped. */
	error?: string;
}

function where(citation: Citation, contextReference: string): string {
	if ("change" in citation) {
		const range =
			citation.endLine === undefined ? `${citation.line}` : `${citation.line}-${citation.endLine}`;
		return `\`${citation.change}\` [L${range}]${citation.side === "OLD" ? " (removed)" : ""}`;
	}
	if ("file" in citation) {
		const range =
			citation.endLine === undefined ? `${citation.line}` : `${citation.line}-${citation.endLine}`;
		return `\`${citation.file}:${range}\``;
	}
	const record = path.posix.join(contextReference, citation.record);
	return `\`${record}${citation.line === undefined || citation.line === 0 ? "" : `:${citation.line}`}\``;
}

function leadRow(lead: QuotedLead, contextReference: string): string {
	const facts = Object.entries(lead.facts ?? {})
		.map(([key, value]) => `${key}=${String(value)}`)
		.join(", ");
	const quote = lead.quote.split("\n")[0]?.trim().slice(0, 120) ?? "";
	return `- ${lead.kind}${lead.rating === undefined ? "" : " (model-rated)"}${lead.newSinceLastReview === true ? " (new)" : ""} at ${where(lead.at, contextReference)}${quote ? `: \`${quote}\`` : ""}${facts ? ` [${facts}]` : ""}`;
}

/** The characters that lines take in a section, each with its line break. */
const charsOf = (lines: readonly string[]) => lines.reduce((n, line) => n + line.length + 1, 0);

/**
 * The lines in their order, as many as fit in `max` characters. When some do not fit, the last line
 * is `rest` of the lines that it leaves out.
 */
function fit(lines: readonly string[], max: number, rest: (omitted: readonly string[]) => string) {
	if (charsOf(lines) <= max) {
		return lines;
	}
	let kept = 0;
	while (
		kept < lines.length &&
		charsOf(lines.slice(0, kept + 1)) + rest(lines.slice(kept + 1)).length + 1 <= max
	) {
		kept += 1;
	}
	return [...lines.slice(0, kept), rest(lines.slice(kept))];
}

/**
 * The section that a script's result becomes. It has at most {@link SECTION_CHARS} characters. The
 * notices come first in its bound: why the script failed or did not run, and each `Not rated` line.
 * The directions, leads and facts get what is left, and a line counts what they leave out.
 */
export function renderSection(
	result: ScriptResult,
	contextReference: string,
	jsonPointer: string,
): string {
	const head: string[] = [];
	const body: string[] = [];
	if (isFailed(result.status)) {
		head.push(
			SCRIPT_FAILED,
			"",
			...result.directions.map((direction) => `- ${oneLine(direction, NOTICE_LINE_CHARS)}`),
		);
	} else {
		body.push(...result.directions.map((direction) => `- ${direction}`));
	}
	if (result.status === "skipped") {
		const needed = Object.entries(result.models)
			.filter(([, slot]) => slot.need === "required" && !slot.bound)
			.map(([name]) => name);
		head.push(
			`> **Not run.** No model is available for a slot that the practice script requires: ${needed.join(", ")}. Assess it from the sources yourself.`,
			"",
		);
	}
	const leadRows = new Set<string>();
	const used = new Set(result.leads.map((lead) => lead.kind));
	if (used.size > 0) {
		const rows = result.leads.map((lead) => leadRow(lead, contextReference));
		for (const row of rows) {
			leadRows.add(row);
		}
		body.push(
			"",
			"**Lead kinds:**",
			...[...used].map((kind) => `- ${kind}: ${result.kinds[kind] ?? ""}`),
			"",
			"**Leads:**",
			...(result.newSinceLastReview === undefined
				? []
				: [
						`${result.newSinceLastReview} of ${result.leads.length} leads point at lines added since the last review of this practice, each marked (new).`,
					]),
			...rows,
		);
	}
	const facts = Object.entries(result.facts ?? {});
	if (facts.length > 0) {
		body.push("", "**Facts:**", ...facts.map(([key, value]) => `- ${key} = ${String(value)}`));
	}
	const unrated = [...result.unrated];
	if (result.dropped > 0) {
		unrated.push({
			what: "lead(s) with a kind that the script did not declare or cited lines that the source does not hold",
			count: result.dropped,
			reason: "off-format",
		});
	}
	const notRated = fit(
		unrated.map((item) =>
			oneLine(
				`- Not rated: ${item.count} ${item.what} (${item.reason}). Read those yourself.`,
				NOTICE_LINE_CHARS,
			),
		),
		NOTICE_CHARS - charsOf(head),
		(omitted) =>
			`- Not rated: ${omitted.length} more entries are not shown. The full result is in ${jsonPointer}.`,
	);
	const shown = fit(body, SECTION_CHARS - charsOf(head) - charsOf(notRated) - 1, (omitted) => {
		const lines = omitted.filter((line) => line !== "");
		const leads = lines.filter((line) => leadRows.has(line)).length;
		return `- ${lines.length} more lines are not shown${leads > 0 ? `, ${leads} of them leads` : ""}. The full result is in ${jsonPointer}.`;
	});
	return `${[...head, ...shown, ...notRated].join("\n").trim()}\n`;
}
