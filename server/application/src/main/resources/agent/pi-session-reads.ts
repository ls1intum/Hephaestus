import { readFileSync } from "node:fs";
import path from "node:path";
import type { AgentSessionEvent } from "@earendil-works/pi-coding-agent";

import { bashRead, bashReadRanges } from "./pi-bash-reads.ts";

import type { CitableText } from "./pi-folder-index.ts";
import type { BriefSource } from "./pi-review-brief.ts";
import {
	CANDIDATE_LIMITS,
	type Coverage,
	type Omission,
} from "./work/practice-scripts/lib/core/contract.ts";
import {
	type EvidenceReader,
	type Receipts,
	noReceipts,
} from "./work/practice-scripts/lib/core/evidence-reader.ts";
import { isJsonObject as isRecord } from "./work/practice-scripts/lib/core/json.ts";
import { ReadRanges } from "./work/practice-scripts/lib/core/read-ranges.ts";

const READ_TOOLS = new Set(["read", "grep", "find", "ls", "bash"]);
// Bounds each refusal: sources named, and ranges named per source.
export const REPAIR_LIMIT = 5;

/** A relative path that stays inside the root it is read from. */
export function insideRoot(target: string): boolean {
	const normal = path.normalize(target);
	return normal !== "." && !path.isAbsolute(normal) && normal.split(path.sep)[0] !== "..";
}
const textOf = (result: unknown): string =>
	isRecord(result) && Array.isArray(result.content)
		? result.content
				.flatMap((part: unknown) =>
					isRecord(part) && part.type === "text" && typeof part.text === "string"
						? [part.text]
						: [],
				)
				.join("\n")
		: "";

/** Host observations retain all reads; coverage checks only required and cited source content. */
export class SessionReads {
	reads = 0;
	readonly ranges = new ReadRanges();
	readonly cutSearches: string[] = [];
	readonly failed: string[] = [];
	readonly unknown: string[] = [];
	readonly #required = new Set<string>();
	readonly #supplied = new Set<string>();
	readonly pending = new Map<string, unknown>();
	readonly #observedContent = new Set<string>();
	readonly #opened = new Set<string>();
	readonly #coveredBy = new Map<string, Set<string>>();
	/** First line the read tool could not return because it alone exceeds the output byte limit. */
	readonly #oversized = new Map<string, number>();
	readonly #root: string;
	readonly #lineCount: (target: string) => number;

	constructor(
		root: string,
		sources: readonly BriefSource[] = [],
		lineCount = (target: string) =>
			readFileSync(path.resolve(root, target), "utf8").split("\n").length,
		texts: readonly CitableText[] = [],
	) {
		this.#root = root;
		this.#lineCount = lineCount;
		const byContent = new Map<string, Set<string>>();
		const byPath = new Map<string, Set<string>>();
		for (const text of texts) {
			for (const [index, key] of [
				[byContent, `${text.source}:${text.sha256}`],
				[byPath, `${text.source}:${text.path}`],
			] as const) {
				const paths = index.get(key) ?? new Set<string>();
				paths.add(this.#key(text.stagedPath));
				index.set(key, paths);
			}
		}
		for (const text of texts) {
			const key = this.#key(text.stagedPath);
			const copies = this.#coveredBy.get(key) ?? new Set<string>();
			for (const copy of byContent.get(`${text.source}:${text.sha256}`) ?? []) {
				copies.add(copy);
			}
			// Extracted strings add citation coordinates, not new source content.
			const separator = text.path.indexOf("#/");
			if (separator !== -1 && text.stagedPath.startsWith("context/citable-records/")) {
				const parent = text.path.slice(0, separator);
				copies.add(this.#key(parent));
				for (const record of byPath.get(`${text.source}:${parent}`) ?? []) {
					copies.add(record);
				}
			}
			this.#coveredBy.set(key, copies);
		}
		for (const source of sources) {
			const key = this.#key(source.path);
			this.#required.add(key);
			this.ranges.expect(key);
			if (source.supplied && source.lines !== undefined) {
				this.#supplied.add(key);
				this.ranges.add(key, 1, source.lines, source.lines);
				this.#observedContent.add(key);
			}
		}
	}

	#key(target: string): string {
		return path.relative(this.#root, path.resolve(this.#root, target));
	}

	note(event: AgentSessionEvent): void {
		if (event.type === "tool_execution_start" && READ_TOOLS.has(event.toolName)) {
			this.pending.set(event.toolCallId, event.args);
			return;
		}
		if (event.type !== "tool_execution_end" || !this.pending.has(event.toolCallId)) {
			return;
		}
		const args = this.pending.get(event.toolCallId);
		this.pending.delete(event.toolCallId);
		this.reads += 1;
		const target =
			isRecord(args) && typeof args.path === "string" ? this.#key(args.path) : undefined;
		const named = target ?? event.toolName;
		const text = textOf(event.result);
		if (event.toolName === "bash") {
			this.#bash(args, event.result, event.isError);
			return;
		}
		if (event.isError) {
			this.failed.push(named);
			return;
		}
		const result: unknown = event.result;
		const details = isRecord(result) && isRecord(result.details) ? result.details : {};
		const truncation = isRecord(details.truncation) ? details.truncation : {};
		const cut =
			truncation.truncated === true ||
			details.matchLimitReached !== undefined ||
			details.resultLimitReached !== undefined ||
			details.entryLimitReached !== undefined;
		if (event.toolName === "read" && target !== undefined) {
			const start = isRecord(args) && typeof args.offset === "number" ? args.offset : 1;
			this.ranges.expect(target);
			this.#opened.add(target);
			if (truncation.firstLineExceedsLimit === true && !this.#oversized.has(target)) {
				this.#oversized.set(target, start);
			}
			// Count the pinned source on the host. Source text can imitate the tool's continuation notices.
			try {
				const last = this.#lineCount(target);
				const limit = isRecord(args) && typeof args.limit === "number" ? args.limit : last;
				let shown = Math.min(limit, last - start + 1);
				if (cut) {
					shown = typeof truncation.outputLines === "number" ? truncation.outputLines : 0;
				}
				if (truncation.firstLineExceedsLimit !== true && !/Read image file/u.test(text)) {
					this.ranges.add(target, start, start + shown - 1, last);
					if (shown > 0) {
						this.#observedContent.add(target);
					}
				}
			} catch {
				this.unknown.push(target);
			}
			return;
		}
		if (cut) {
			this.cutSearches.push(named);
		}
	}

	#bash(args: unknown, result: unknown, isError: boolean): void {
		const command = isRecord(args) && typeof args.command === "string" ? args.command : "";
		const read = bashRead(command);
		const details = isRecord(result) && isRecord(result.details) ? result.details : {};
		const truncation = isRecord(details.truncation) ? details.truncation : {};
		const structured =
			isRecord(result) && isRecord(result.structuredContent) ? result.structuredContent : {};
		const cut = truncation.truncated === true || structured.truncated === true;
		const noMatches = read?.kind === "search" && structured.exit_code === 1;
		const target = read === undefined ? undefined : this.#key(read.path);
		if (target !== undefined) {
			this.ranges.expect(target);
			if (!isError || noMatches) {
				this.#opened.add(target);
			}
		}
		if (isError && !noMatches) {
			this.failed.push(target ?? "bash");
		}
		if (read !== undefined && target !== undefined && !cut && (!isError || noMatches)) {
			try {
				const output = noMatches ? "" : textOf(result);
				const { last, ranges } = bashReadRanges(this.#root, read, output);
				for (const range of ranges) {
					this.ranges.add(target, range.start, range.end, last);
					if (range.end >= range.start) {
						this.#observedContent.add(target);
					}
				}
				return;
			} catch {
				// A receipt cannot rely on output that differs from the pinned source.
			}
		}
		this.unknown.push(target ?? `bash: ${command}`);
	}

	/** Content supplied in this session's opening or returned by its native reader. */
	hasRead(target: string): boolean {
		return this.#observedContent.has(this.#key(target));
	}

	#complete(key: string, visited = new Set<string>()): boolean {
		if (this.#supplied.has(key) || this.ranges.complete(key)) {
			return true;
		}
		if (visited.has(key)) {
			return false;
		}
		visited.add(key);
		return [...(this.#coveredBy.get(key) ?? [])].some((copy) => this.#complete(copy, visited));
	}

	/** Complete source content, including equivalent captured copies supplied in the brief. */
	hasCompleteRead(target: string): boolean {
		return this.#complete(this.#key(target));
	}

	/** Unread file lines of a source, or undefined when the host cannot count them. */
	remaining(target: string): { start: number; end: number }[] | undefined {
		const key = this.#key(target);
		if (this.#complete(key)) {
			return [];
		}
		const gaps = this.ranges.gaps(key);
		if (gaps !== undefined) {
			return gaps;
		}
		try {
			return [{ start: 1, end: this.#lineCount(key) }];
		} catch {
			return undefined;
		}
	}

	/** Executable reads for the unread lines of sources inside the session root. */
	repair(targets: readonly string[]): string {
		const keys = [...new Set(targets.map((target) => this.#key(target)))].filter(
			(key) => key !== "" && insideRoot(key) && this.remaining(key)?.length !== 0,
		);
		const steps = keys.slice(0, REPAIR_LIMIT).map((key) => {
			const oversized = this.#oversized.get(key);
			if (oversized !== undefined) {
				return `file line ${oversized} of ${key} alone exceeds the read tool's output limit, so no read can cover ${key} and no MET result that needs it can be admitted`;
			}
			const gaps = this.remaining(key);
			if (gaps === undefined) {
				return `read(${JSON.stringify({ path: key })})`;
			}
			const more = gaps.length - REPAIR_LIMIT;
			return (
				gaps
					.slice(0, REPAIR_LIMIT)
					.map(
						({ start, end }) =>
							`read(${JSON.stringify({ path: key, offset: start, limit: end - start + 1 })})`,
					)
					.join("; ") + (more > 0 ? `; then ${more} more unread ranges of ${key}` : "")
			);
		});
		if (steps.length === 0) {
			return "";
		}
		const more = keys.length - REPAIR_LIMIT;
		return (
			`Read the unread file lines: ${steps.join("; ")}${more > 0 ? `; then ${more} more sources` : ""}. ` +
			`Offsets and limits count physical lines of the file, not the [L<n>] markers inside diff content. ` +
			`When a read ends with "Use offset=N to continue", read again from N to the end of the same range.${this.bashNote(
				keys,
			)}`
		);
	}

	/** Explains uncounted shell reads, only when a command named one of these sources. */
	bashNote(targets: readonly string[]): string {
		const names = targets.map((target) => path.basename(target));
		return this.unknown.some(
			(entry) => entry.startsWith("bash: ") && names.some((name) => entry.includes(name)),
		)
			? " Bash output from pipes, globs or compound commands is not counted as reading; use read, or one literal `sed -n 'a,bp' <file>` per range."
			: "";
	}

	get #incomplete(): string[] {
		return this.ranges.incomplete.filter((key) => !this.#complete(key));
	}

	get receipts(): Receipts {
		return {
			reads: this.reads,
			truncatedReads:
				this.#incomplete.filter((file) => this.#opened.has(file)).length + this.cutSearches.length,
			failedReads: this.failed.length,
		};
	}

	coverage(
		reader?: EvidenceReader,
		domainReads: Receipts = noReceipts(),
		citedPaths: readonly string[] = [],
	): Extract<Coverage, { source: "host" }> {
		const required = new Set([...this.#required, ...citedPaths.map((file) => this.#key(file))]);
		const incomplete = [...required].filter((file) => !this.#complete(file));
		const failed = incomplete.filter((file) => this.failed.includes(file));
		const truncated = incomplete.filter((file) => !failed.includes(file) && this.#opened.has(file));
		const unavailable = incomplete.filter(
			(file) => !failed.includes(file) && !this.#opened.has(file),
		);
		const omitted: Omission[] = [];
		const omit = (what: string, items: string[], reason: Omission["reason"]) => {
			if (items.length > 0) {
				omitted.push({
					what,
					count: items.length,
					reason,
					items: items
						.slice(0, CANDIDATE_LIMITS.omissionItems)
						.map((item) => item.slice(0, CANDIDATE_LIMITS.warrantFieldChars)),
				});
			}
		};
		omit("required or cited sources with unread ranges", truncated, "truncated");
		omit("relevant sources not supplied or read", unavailable, "unavailable");
		omit("reads that failed", failed, "unavailable");
		if (required.size === 0 && this.#observedContent.size === 0 && domainReads.reads === 0) {
			omitted.push({
				what: "no relevant source was supplied or read",
				count: 1,
				reason: "unavailable",
			});
		}
		if (domainReads.truncatedReads > 0) {
			omitted.push({
				what: "governed file reads cut short",
				count: domainReads.truncatedReads,
				reason: "truncated",
			});
		}
		if (domainReads.failedReads > 0) {
			omitted.push({
				what: "governed file reads that failed",
				count: domainReads.failedReads,
				reason: "unavailable",
			});
		}
		const receipts = {
			source: "host" as const,
			reads: this.reads + domainReads.reads,
			truncatedReads: truncated.length + domainReads.truncatedReads,
			failedReads: failed.length + domainReads.failedReads,
			receipts: reader?.readReceipts ?? [],
		};
		const [first, ...rest] = omitted;
		return first === undefined
			? { ...receipts, complete: true }
			: { ...receipts, complete: false, omitted: [first, ...rest] };
	}
}
