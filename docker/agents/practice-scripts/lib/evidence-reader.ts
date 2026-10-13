/**
 * The host's side of a program's reads: the pinned head tree, the captured change and the captured
 * records. Each read leaves a receipt, so the host knows what a program actually read, cut short, or
 * could not read. A missing file is proved absence; a read that fails is unavailable.
 */
import { open, readFile, realpath, stat } from "node:fs/promises";
import path from "node:path";

import {
	CANDIDATE_LIMITS,
	DIFF_SOURCE,
	TREE_SOURCE,
	type Citation,
	type CodeCitationIdentity,
} from "./contract.ts";
import { globFilesSync } from "./files.ts";
import { grep } from "./grep.ts";
import type { ReadRequest, ReadValues } from "./ipc.ts";
import { UnratedError } from "./practice-script.ts";
import type { DiffFile } from "./types.ts";

/** The most of a file one read returns; the rest is cut and the read says so. */
export const MAX_READ_BYTES = 2 * 1024 * 1024;
/** The largest captured record a read returns whole. */
const MAX_RECORD_BYTES = 16 * 1024 * 1024;
/** The most paths one listing returns. */
const MAX_LIST_PATHS = 5000;
/** The most matches one search returns when the program names no bound. */
const DEFAULT_GREP_RESULTS = 200;
/** The most lines one citation spans. */
const MAX_CITED_LINES = 200;
/** Bytes that tell a binary file: a NUL among the first of them. */
const BINARY_PROBE_BYTES = 8000;

/** What a practice's reads reached: every read, those cut short, and those that failed. */
export interface Receipts {
	reads: number;
	truncatedReads: number;
	failedReads: number;
}

export const noReceipts = (): Receipts => ({ reads: 0, truncatedReads: 0, failedReads: 0 });

/** The pinned captures a host reads for its programs. */
export interface EvidenceSources {
	/** The absolute path of the checkout at the pinned head; null without a captured tree. */
	repo: string | null;
	/** The pinned head commit of the tree. */
	headRevision: string | null;
	/** The workspace path of the captured tree's artifact, `<repository>/.git/HEAD`. */
	treeArtifact: string;
	/** The absolute path of the captured context folder; null when nothing was captured. */
	contextDir: string | null;
	/** The workspace path of the context folder, as citations name it. */
	contextReference: string;
	/** The captured change, parsed; null without one. */
	change: { base: string; head: string; files: ReadonlyMap<string, DiffFile> } | null;
	/** The source kind of a captured artifact, by its workspace path; undefined for one not captured. */
	sourceKindOf: (artifactPath: string) => string | undefined;
}

const isMissing = (error: unknown) =>
	error instanceof Error &&
	"code" in error &&
	(error.code === "ENOENT" || error.code === "ENOTDIR" || error.code === "EISDIR");

const messageOf = (error: unknown) => (error instanceof Error ? error.message : String(error));

/** Whether a path is in a checkout's Git folder, which holds the clone's configuration, not the work. */
const inGitFolder = (relative: string) => relative.split(/[\\/]/u).includes(".git");

/** The absolute path of `relative` inside `root`, symlinks resolved; a path that leaves the root is refused. */
async function inside(root: string, relative: string): Promise<string> {
	const base = await realpath(root);
	const full = await realpath(path.resolve(base, relative));
	if (full !== base && !full.startsWith(`${base}${path.sep}`)) {
		throw new UnratedError("refused", `${relative} is outside the captured source`);
	}
	// The checkout's Git folder is not a file of the work: it reads as absent.
	if (inGitFolder(path.relative(base, full))) {
		throw Object.assign(new Error(`${relative} is in the checkout's Git folder, not the work`), {
			code: "ENOENT",
		});
	}
	return full;
}

/** The lines `start` to `end` of a text, 1-based, or why they cannot be quoted. */
export function quotedLines(text: string, start: number, end: number): string {
	if (end < start) {
		throw new UnratedError("error", `the range ${start}-${end} ends before it starts`);
	}
	if (end - start >= MAX_CITED_LINES) {
		throw new UnratedError("too-large", `a citation spans at most ${MAX_CITED_LINES} lines`);
	}
	const lines = text.split("\n");
	if (end > lines.length) {
		throw new UnratedError("error", `the source has ${lines.length} lines, not ${end}`);
	}
	const quote = lines.slice(start - 1, end).join("\n");
	if (quote.length > CANDIDATE_LIMITS.quoteChars) {
		throw new UnratedError(
			"too-large",
			`a quote holds at most ${CANDIDATE_LIMITS.quoteChars} characters`,
		);
	}
	return quote;
}

/** The lines a change adds or removes, `start` to `end`, or why the change does not hold them. */
export function changeLines(
	file: DiffFile | undefined,
	side: "OLD" | "NEW",
	start: number,
	end: number,
): string {
	if (file === undefined) {
		throw new UnratedError("error", "the change does not touch that file");
	}
	const lines = side === "OLD" ? file.removedLines : file.addedLines;
	const picked: string[] = [];
	for (let line = start; line <= end; line += 1) {
		const text = lines.get(line);
		if (text === undefined) {
			throw new UnratedError(
				"error",
				`the change does not ${side === "OLD" ? "remove" : "add"} line ${line} of ${file.path}`,
			);
		}
		picked.push(text);
	}
	const quote = picked.join("\n");
	if (quote === "" || quote.length > CANDIDATE_LIMITS.quoteChars) {
		throw new UnratedError("too-large", "those lines cannot be quoted");
	}
	return quote;
}

/** Answers the reads of one practice and counts its receipts. */
export class EvidenceReader {
	readonly #sources: EvidenceSources;
	readonly #receipts: Receipts;

	constructor(sources: EvidenceSources, receipts: Receipts) {
		this.#sources = sources;
		this.#receipts = receipts;
	}

	citationIdentity(citation: Citation): CodeCitationIdentity | undefined {
		const sources = this.#sources;
		if (sources.repo === null) {
			return undefined;
		}
		let revision: string | null | undefined;
		if (citation.sourceKind === TREE_SOURCE && citation.artifactPath === sources.treeArtifact) {
			revision = citation.revision ?? sources.headRevision;
		} else if (
			citation.sourceKind === DIFF_SOURCE &&
			citation.artifactPath === path.posix.join(sources.contextReference, "change.json")
		) {
			revision = citation.side === "OLD" ? sources.change?.base : sources.change?.head;
		}
		return revision == null ? undefined : { repository: sources.treeArtifact, revision };
	}

	/** Answer one read. A read that cannot be answered throws `UnratedError` with its reason. */
	async answer<R extends ReadRequest>(request: R): Promise<ReadValues[R["op"]]>;
	async answer(request: ReadRequest): Promise<ReadValues[keyof ReadValues]> {
		if (request.op === "cite") {
			return this.#cite(request);
		}
		this.#receipts.reads += 1;
		try {
			const value = await this.#read(request);
			if ("truncated" in value && value.truncated) {
				this.#receipts.truncatedReads += 1;
			}
			return value;
		} catch (error) {
			this.#receipts.failedReads += 1;
			throw error instanceof UnratedError
				? error
				: new UnratedError("unavailable", messageOf(error));
		}
	}

	#repo(tree: "head" | "base"): string {
		if (tree === "base") {
			throw new UnratedError("unavailable", "the base tree is not available to programs yet");
		}
		if (this.#sources.repo === null) {
			throw new UnratedError("unavailable", "this work has no captured file tree");
		}
		return this.#sources.repo;
	}

	async #read(
		request: Exclude<ReadRequest, { op: "cite" }>,
	): Promise<ReadValues[keyof ReadValues]> {
		switch (request.op) {
			case "read": {
				const repo = this.#repo(request.tree);
				let file: string;
				try {
					file = await inside(repo, request.path);
				} catch (error) {
					if (isMissing(error)) {
						return { status: "ABSENT" };
					}
					throw error;
				}
				const stats = await stat(file);
				if (!stats.isFile()) {
					return { status: "ABSENT" };
				}
				const handle = await open(file, "r");
				try {
					const size = Math.min(stats.size, MAX_READ_BYTES);
					const bytes = Buffer.alloc(size);
					await handle.read(bytes, 0, size, 0);
					if (bytes.subarray(0, BINARY_PROBE_BYTES).includes(0)) {
						return { status: "BINARY" };
					}
					return {
						status: "FOUND",
						text: bytes.toString("utf8"),
						truncated: stats.size > MAX_READ_BYTES,
					};
				} finally {
					await handle.close();
				}
			}
			case "list": {
				const repo = this.#repo(request.tree);
				const all = globFilesSync(request.glob ?? "**", repo).filter(
					(file) => file !== ".git" && !file.startsWith(".git/"),
				);
				return {
					paths: all.slice(0, MAX_LIST_PATHS),
					truncated: all.length > MAX_LIST_PATHS,
				};
			}
			case "grep": {
				const repo = this.#repo(request.tree);
				const maxResults = request.maxResults ?? DEFAULT_GREP_RESULTS;
				// One match more than asked tells a search that was cut from one that found exactly the bound.
				const found = await grep(request.pattern, repo, {
					glob: request.glob,
					maxResults: maxResults + 1,
					fixedString: request.fixedString,
				});
				const matches = found.filter((match) => !inGitFolder(match.file));
				return { matches: matches.slice(0, maxResults), truncated: matches.length > maxResults };
			}
			case "record": {
				const { contextDir } = this.#sources;
				if (contextDir === null) {
					return { status: "ABSENT" };
				}
				let file: string;
				try {
					file = await inside(contextDir, request.name);
				} catch (error) {
					if (isMissing(error)) {
						return { status: "ABSENT" };
					}
					throw error;
				}
				const stats = await stat(file);
				if (!stats.isFile()) {
					return { status: "ABSENT" };
				}
				if (stats.size > MAX_RECORD_BYTES) {
					throw new UnratedError(
						"too-large",
						`${request.name} is larger than ${MAX_RECORD_BYTES} bytes`,
					);
				}
				return { status: "FOUND", text: await readFile(file, "utf8") };
			}
		}
	}

	async #cite(request: Extract<ReadRequest, { op: "cite" }>): Promise<Citation> {
		const end = request.endLine ?? request.startLine;
		switch (request.source) {
			case "tree": {
				const repo = this.#repo(request.tree);
				const { headRevision } = this.#sources;
				const file = await inside(repo, request.path).catch((error: unknown) => {
					throw isMissing(error)
						? new UnratedError("error", `${request.path} is not in the tree`)
						: error;
				});
				const stats = await stat(file);
				if (!stats.isFile() || stats.size > MAX_READ_BYTES) {
					throw new UnratedError("too-large", `${request.path} cannot be quoted`);
				}
				return {
					sourceKind: TREE_SOURCE,
					artifactPath: this.#sources.treeArtifact,
					path: request.path,
					...(headRevision === null ? {} : { revision: headRevision }),
					startLine: request.startLine,
					endLine: end,
					quote: quotedLines(await readFile(file, "utf8"), request.startLine, end),
				};
			}
			case "change": {
				const { change } = this.#sources;
				if (change === null) {
					throw new UnratedError("unavailable", "this work has no captured change");
				}
				return {
					sourceKind: DIFF_SOURCE,
					artifactPath: path.posix.join(this.#sources.contextReference, "change.json"),
					path: request.path,
					side: request.side,
					startLine: request.startLine,
					endLine: end,
					quote: changeLines(change.files.get(request.path), request.side, request.startLine, end),
				};
			}
			case "record": {
				const artifactPath = path.posix.join(this.#sources.contextReference, request.name);
				const sourceKind = this.#sources.sourceKindOf(artifactPath);
				if (sourceKind === undefined) {
					throw new UnratedError("error", `${request.name} is not a captured source`);
				}
				const record = await this.#read({ op: "record", name: request.name });
				if (!("text" in record) || typeof record.text !== "string") {
					throw new UnratedError("error", `${request.name} was not captured`);
				}
				return {
					sourceKind,
					artifactPath,
					path: artifactPath,
					startLine: request.startLine,
					endLine: end,
					quote: quotedLines(record.text, request.startLine, end),
				};
			}
		}
	}
}
