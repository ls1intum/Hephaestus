import { readFileSync } from "node:fs";
import path from "node:path";
import type { AgentSessionEvent } from "@earendil-works/pi-coding-agent";

import { bashRead, bashReadRanges, bashPaths } from "./pi-bash-reads.ts";

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
const ABSENT_PATH = /\bENOENT\b|^Path not found: /u;
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

/** Host observations include supplied brief sources, continuous file ranges, and unknown shell access. */
export class SessionReads {
	reads = 0;
	readonly ranges = new ReadRanges();
	readonly cutSearches: string[] = [];
	readonly failed: string[] = [];
	readonly unknown: string[] = [];
	readonly #unknownPaths = new Set<string>();
	readonly pending = new Map<string, unknown>();
	readonly #supplied: boolean;
	readonly #observedContent = new Set<string>();
	readonly #opened = new Set<string>();
	readonly #coveredBy = new Map<string, Set<string>>();
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
		this.#supplied = sources.some((source) => source.supplied && source.lines !== undefined);
		for (const source of sources) {
			const key = this.#key(source.path);
			this.ranges.expect(key);
			if (source.supplied && source.lines !== undefined) {
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
			if (!ABSENT_PATH.test(text)) {
				this.failed.push(named);
			}
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
			this.#opened.add(target);
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
		const paths = read === undefined ? bashPaths(this.#root, command) : [read.path];
		if (read === undefined || paths.length === 0) {
			this.unknown.push(`bash: ${command}`);
		}
		for (const file of paths) {
			this.#unknownPaths.add(this.#key(file));
		}
	}

	/** Content supplied in this session's opening or returned by its native reader. */
	hasRead(target: string): boolean {
		return this.#observedContent.has(this.#key(target));
	}

	#complete(key: string, visited = new Set<string>()): boolean {
		if (this.ranges.complete(key)) {
			return true;
		}
		if (visited.has(key)) {
			return false;
		}
		visited.add(key);
		return [...(this.#coveredBy.get(key) ?? [])].some((copy) => this.#complete(copy, visited));
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
	): Extract<Coverage, { source: "host" }> {
		const native = this.receipts;
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
		omit(
			"reads cut short at the tool's output limit",
			[...this.#incomplete.filter((file) => this.#opened.has(file)), ...this.cutSearches],
			"truncated",
		);
		omit(
			"relevant sources not supplied or read",
			this.#incomplete.filter((file) => !this.#opened.has(file)),
			"unavailable",
		);
		omit("reads that failed", this.failed, "unavailable");
		omit(
			"evidence coverage is unknown",
			[...this.unknown, ...[...this.#unknownPaths].filter((key) => !this.#complete(key))],
			"unavailable",
		);
		omit("evidence reads still running", [...this.pending.keys()], "unavailable");
		if (!this.#supplied && this.#observedContent.size === 0 && domainReads.reads === 0) {
			omit(
				"no relevant source was supplied or read",
				["read the sources needed by the criterion"],
				"unavailable",
			);
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
			reads: native.reads + domainReads.reads,
			truncatedReads: native.truncatedReads + domainReads.truncatedReads,
			failedReads: native.failedReads + domainReads.failedReads,
			receipts: reader?.readReceipts ?? [],
		};
		const [first, ...rest] = omitted;
		return first === undefined
			? { ...receipts, complete: true }
			: { ...receipts, complete: false, omitted: [first, ...rest] };
	}
}
