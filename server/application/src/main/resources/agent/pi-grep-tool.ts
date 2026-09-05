import { readdirSync, readFileSync, statSync } from "node:fs";
import { basename, join, relative, resolve, sep } from "node:path";

import { type AgentToolResult, defineTool } from "@earendil-works/pi-coding-agent";

/**
 * The search tool a review session gets instead of the SDK's `grep`, which spawns ripgrep and, when
 * it cannot, downloads one; the sandbox allows neither a child process nor the network. This walks
 * the workspace in-process with the same parameters and the same output shape as the SDK tool, so
 * the orchestrator's instructions and the model's habits carry over unchanged.
 */
export interface GrepParams {
	pattern: string;
	path?: string;
	glob?: string;
	ignoreCase?: boolean;
	literal?: boolean;
	context?: number;
	limit?: number;
}

export interface GrepDetails {
	matches: number;
	truncated: boolean;
}

const DEFAULT_LIMIT = 100;
const MAX_CONTEXT = 20;
const MAX_FILE_BYTES = 2 * 1024 * 1024;
const MAX_LINE_LENGTH = 240;
const MAX_OUTPUT_BYTES = 50 * 1024;
/** Never evidence, at any depth. */
const SKIPPED_ANYWHERE = new Set(["node_modules", ".git"]);
/**
 * The sandbox's own state, by its path from the workspace root; a reviewed repository may use these
 * names. `work` itself stays searchable: the precompute summary the orchestrator points at lives there.
 */
const SKIPPED_PATHS = new Set([".pi", ".sessions", "out", "work/composition"]);

function globToRegExp(glob: string): RegExp {
	let source = "";
	for (let i = 0; i < glob.length; i += 1) {
		const c = glob.charAt(i);
		if (c === "*") {
			if (glob.charAt(i + 1) === "*") {
				source += ".*";
				i += 1;
				if (glob.charAt(i + 1) === "/") i += 1;
			} else {
				source += "[^/]*";
			}
		} else if (c === "?") {
			source += "[^/]";
		} else {
			source += c.replace(/[.+^${}()|[\]\\]/g, "\\$&");
		}
	}
	// A glob without a slash matches a basename anywhere, as ripgrep's does.
	return new RegExp(glob.includes("/") ? `^${source}$` : `(^|/)${source}$`);
}

function truncateLine(text: string): { text: string; wasTruncated: boolean } {
	return text.length > MAX_LINE_LENGTH
		? { text: `${text.slice(0, MAX_LINE_LENGTH)}…`, wasTruncated: true }
		: { text, wasTruncated: false };
}

function listFiles(cwd: string, root: string, out: string[], signal?: AbortSignal): void {
	if (signal?.aborted) return;
	let entries: import("node:fs").Dirent[];
	try {
		entries = readdirSync(root, { withFileTypes: true });
	} catch {
		return;
	}
	entries.sort((a, b) => a.name.localeCompare(b.name));
	for (const entry of entries) {
		if (entry.isDirectory()) {
			if (SKIPPED_ANYWHERE.has(entry.name)) continue;
			const child = join(root, entry.name);
			if (SKIPPED_PATHS.has(relative(cwd, child).split(sep).join("/"))) continue;
			listFiles(cwd, child, out, signal);
		} else if (entry.isFile()) {
			out.push(join(root, entry.name));
		}
	}
}

/** Searches under `cwd` and returns the SDK grep tool's text output. Pure apart from reading files. */
export function searchFiles(
	cwd: string,
	params: GrepParams,
	signal?: AbortSignal,
): { text: string; details: GrepDetails } {
	const searchRoot = resolve(cwd, params.path ?? ".");
	const fromWorkspace = relative(cwd, searchRoot);
	if (fromWorkspace === ".." || fromWorkspace.startsWith(`..${sep}`)) {
		return {
			text: `Path ${params.path} is outside the workspace.`,
			details: { matches: 0, truncated: false },
		};
	}
	// The skips apply to a search rooted inside them as much as to a walk reaching them.
	const rootSegments = fromWorkspace === "" ? [] : fromWorkspace.split(sep);
	const rootIsSkipped =
		rootSegments.some((segment) => SKIPPED_ANYWHERE.has(segment)) ||
		rootSegments.some((_, index) => SKIPPED_PATHS.has(rootSegments.slice(0, index + 1).join("/")));
	if (rootIsSkipped) {
		return {
			text: `Path ${params.path} is the sandbox's own state, not the reviewed work; it is not searchable.`,
			details: { matches: 0, truncated: false },
		};
	}
	const flags = params.ignoreCase ? "i" : "";
	const source = params.literal
		? params.pattern.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
		: params.pattern;
	let matcher: RegExp;
	try {
		matcher = new RegExp(source, flags);
	} catch (error) {
		return {
			text: `Invalid pattern: ${error instanceof Error ? error.message : String(error)}`,
			details: { matches: 0, truncated: false },
		};
	}
	const globMatcher = params.glob ? globToRegExp(params.glob) : null;
	const context =
		params.context && params.context > 0 ? Math.min(MAX_CONTEXT, Math.floor(params.context)) : 0;
	const limit = Math.max(1, params.limit ?? DEFAULT_LIMIT);

	let rootStat: import("node:fs").Stats;
	try {
		rootStat = statSync(searchRoot);
	} catch {
		return {
			text: `Path ${params.path ?? "."} does not exist.`,
			details: { matches: 0, truncated: false },
		};
	}
	const files: string[] = [];
	if (rootStat.isDirectory()) listFiles(resolve(cwd), searchRoot, files, signal);
	else files.push(searchRoot);

	const blocks: string[] = [];
	let matches = 0;
	let truncated = false;
	let linesTruncated = false;
	let aborted = signal?.aborted ?? false;
	for (const file of files) {
		if (signal?.aborted) {
			aborted = true;
			break;
		}
		if (matches >= limit) {
			truncated = true;
			break;
		}
		const relativePath = relative(searchRoot, file);
		// A single-file search has no relative path; its name stands in, as ripgrep prints it.
		const shown = relativePath === "" ? basename(file) : relativePath;
		if (globMatcher && !globMatcher.test(shown.split(sep).join("/"))) continue;
		let content: string;
		try {
			if (statSync(file).size > MAX_FILE_BYTES) continue;
			const raw = readFileSync(file);
			if (raw.includes(0)) continue;
			content = raw.toString("utf8");
		} catch {
			continue;
		}
		const lines = content.split("\n");
		// A file's final newline ends its last line; it does not start an empty one.
		if (lines.at(-1) === "") lines.pop();
		for (let index = 0; index < lines.length; index += 1) {
			if (matches >= limit) {
				truncated = true;
				break;
			}
			const line = lines[index]?.replace(/\r/g, "") ?? "";
			matcher.lastIndex = 0;
			if (!matcher.test(line)) continue;
			matches += 1;
			const lineNumber = index + 1;
			const start = context > 0 ? Math.max(1, lineNumber - context) : lineNumber;
			const end = context > 0 ? Math.min(lines.length, lineNumber + context) : lineNumber;
			for (let current = start; current <= end; current += 1) {
				const { text, wasTruncated } = truncateLine(lines[current - 1]?.replace(/\r/g, "") ?? "");
				if (wasTruncated) linesTruncated = true;
				blocks.push(
					current === lineNumber ? `${shown}:${current}: ${text}` : `${shown}-${current}- ${text}`,
				);
			}
			if (context > 0) blocks.push("--");
		}
	}
	const notes: string[] = [];
	if (aborted) notes.push("[Search stopped: the session's time is up]");
	if (truncated)
		notes.push(
			`[Output truncated at ${limit} matches; narrow the pattern, path or glob to see more]`,
		);
	if (linesTruncated) notes.push(`[Some lines were truncated at ${MAX_LINE_LENGTH} characters]`);
	let body = blocks.join("\n");
	if (body.length > MAX_OUTPUT_BYTES) {
		body = body.slice(0, MAX_OUTPUT_BYTES);
		notes.push(
			`[Output truncated at ${MAX_OUTPUT_BYTES} characters; narrow the pattern, path or glob to see more]`,
		);
		truncated = true;
	}
	const text = [blocks.length === 0 ? "No matches found" : body, ...notes].join("\n");
	return { text, details: { matches, truncated } };
}

/** The model's arguments arrive untyped; anything of the wrong shape is ignored rather than trusted. */
export function readGrepParams(raw: unknown): GrepParams {
	const record: Record<string, unknown> = typeof raw === "object" && raw !== null ? { ...raw } : {};
	const string = (key: string) => {
		const value = record[key];
		return typeof value === "string" ? value : undefined;
	};
	const bool = (key: string) => {
		const value = record[key];
		return typeof value === "boolean" ? value : undefined;
	};
	const number = (key: string) => {
		const value = record[key];
		return typeof value === "number" && Number.isFinite(value) ? value : undefined;
	};
	return {
		pattern: string("pattern") ?? "",
		path: string("path"),
		glob: string("glob"),
		ignoreCase: bool("ignoreCase"),
		literal: bool("literal"),
		context: number("context"),
		limit: number("limit"),
	};
}

export function buildGrepTool(cwd: string) {
	return defineTool({
		name: "grep",
		label: "Grep",
		promptSnippet: "Search file contents for a pattern within the reviewed work",
		description:
			"Search file contents for a pattern. Returns matching lines with file paths and line numbers. " +
			`Output is truncated to ${DEFAULT_LIMIT} matches by default; use limit, path or glob to narrow it.`,
		parameters: {
			type: "object",
			additionalProperties: false,
			required: ["pattern"],
			properties: {
				pattern: { type: "string", description: "Search pattern (regex or literal string)" },
				path: {
					type: "string",
					description: "Directory or file to search (default: current directory)",
				},
				glob: {
					type: "string",
					description: "Filter files by glob pattern, e.g. '*.ts' or '**/*.spec.ts'",
				},
				ignoreCase: { type: "boolean", description: "Case-insensitive search (default: false)" },
				literal: {
					type: "boolean",
					description: "Treat pattern as literal string instead of regex (default: false)",
				},
				context: {
					type: "number",
					description: "Number of lines to show before and after each match (default: 0)",
				},
				limit: {
					type: "number",
					description: `Maximum number of matches to return (default: ${DEFAULT_LIMIT})`,
				},
			},
		},
		execute: (_toolCallId, params, signal): Promise<AgentToolResult<GrepDetails>> => {
			const { text, details } = searchFiles(cwd, readGrepParams(params), signal);
			return Promise.resolve({ content: [{ type: "text", text }], details });
		},
	});
}
