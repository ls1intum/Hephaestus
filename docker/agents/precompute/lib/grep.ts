import { isInDiff } from "./diff-parser";
import type { DiffFile, Hint } from "./types";
import { join, relative } from "path";

export interface GrepMatch {
	file: string;
	line: number;
	content: string;
}

export interface GrepOptions {
	glob?: string;
	maxResults?: number;
	fixedString?: boolean;
}

const GLOB_GREP_BATCH_SIZE = 256;

function parseGrepLine(line: string, dir: string): GrepMatch | null {
	const match = line.match(/^(.+?):(\d+):(.*)$/);
	if (!match) {
		return null;
	}

	return {
		file: relative(dir, match[1]),
		line: Number.parseInt(match[2], 10),
		content: match[3].trim(),
	};
}

async function collectGrepMatches(
	args: string[],
	dir: string,
	maxResults: number,
): Promise<GrepMatch[]> {
	const child = Bun.spawn(args, {
		stdout: "pipe",
		stderr: "ignore",
	});

	const reader = child.stdout.getReader();
	const decoder = new TextDecoder();
	const matches: GrepMatch[] = [];
	let buffer = "";

	try {
		while (matches.length < maxResults) {
			const { done, value } = await reader.read();
			if (done) {
				break;
			}

			buffer += decoder.decode(value, { stream: true });
			const lines = buffer.split("\n");
			buffer = lines.pop() ?? "";

			for (const line of lines) {
				if (!line.trim()) {
					continue;
				}

				const match = parseGrepLine(line, dir);
				if (!match) {
					continue;
				}

				matches.push(match);
				if (matches.length >= maxResults) {
					child.kill();
					break;
				}
			}
		}

		buffer += decoder.decode();
		if (buffer.trim() && matches.length < maxResults) {
			const match = parseGrepLine(buffer, dir);
			if (match) {
				matches.push(match);
			}
		}
	} finally {
		reader.releaseLock();
		await child.exited;
	}

	return matches.slice(0, maxResults);
}

async function collectMatchesForGlob(
	pattern: string,
	dir: string,
	grepArgs: string[],
	glob: string,
	maxResults: number,
): Promise<GrepMatch[]> {
	const matcher = new Bun.Glob(glob);
	const matches: GrepMatch[] = [];
	let batch: string[] = [];

	for (const file of matcher.scanSync(dir)) {
		batch.push(join(dir, file));
		if (batch.length < GLOB_GREP_BATCH_SIZE) {
			continue;
		}

		const remaining = maxResults - matches.length;
		matches.push(...(await collectGrepMatches([...grepArgs, "--", pattern, ...batch], dir, remaining)));
		if (matches.length >= maxResults) {
			return matches.slice(0, maxResults);
		}
		batch = [];
	}

	if (batch.length === 0) {
		return matches;
	}

	const remaining = maxResults - matches.length;
	if (remaining <= 0) {
		return matches.slice(0, maxResults);
	}

	matches.push(...(await collectGrepMatches([...grepArgs, "--", pattern, ...batch], dir, remaining)));
	return matches.slice(0, maxResults);
}

function shouldIncludeDiscoveredFile(path: string): boolean {
	const segments = path.split("/");
	return !segments.some((segment) =>
		segment === "node_modules" || segment === ".build" || segment.startsWith("."),
	);
}

/**
 * Run grep on a directory. Returns structured matches.
 *
 * @param pattern — regex pattern (or fixed string if fixedString=true)
 * @param dir — directory to search
	* @param opts.glob — path-relative glob filter rooted at dir, including recursive path globs
 * @param opts.maxResults — cap results (default 500)
 * @param opts.fixedString — use -F instead of -E (default false)
 */
export async function grep(
	pattern: string,
	dir: string,
	opts: GrepOptions = {},
): Promise<GrepMatch[]> {
	const { glob, maxResults = 500, fixedString = false } = opts;
	if (maxResults <= 0) {
		return [];
	}

	const grepArgs = fixedString ? ["grep", "-H", "-n", "-F"] : ["grep", "-H", "-n", "-E"];

	if (glob) {
		return collectMatchesForGlob(pattern, dir, grepArgs, glob, maxResults);
	}

	return collectGrepMatches(["grep", "-r", ...grepArgs.slice(1), "--", pattern, dir], dir, maxResults);
}

/**
 * Convert grep matches to Hints with diff awareness and context flags.
 */
export function matchesToHints(
	matches: GrepMatch[],
	pattern: string,
	diffFiles: Map<string, DiffFile>,
	flagFn?: (match: GrepMatch) => Record<string, boolean | number | string>,
): Hint[] {
	return matches.map((m) => ({
		file: m.file,
		line: m.line,
		pattern,
		context: m.content,
		inDiff: isInDiff(diffFiles, m.file, m.line),
		flags: flagFn ? flagFn(m) : {},
	}));
}

/**
 * Read a file and return its lines (1-indexed Map).
 */
export async function readFileLines(
	path: string,
): Promise<Map<number, string>> {
	try {
		const content = await Bun.file(path).text();
		const lines = new Map<number, string>();
		content.split("\n").forEach((line: string, i: number) => {
			lines.set(i + 1, line);
		});
		return lines;
	} catch {
		return new Map();
	}
}

/**
 * Find all files matching a given extension in a directory.
 */
export async function findFiles(
	dir: string,
	extension: string,
): Promise<string[]> {
	const pattern = `**/*.${extension}`;
	const matcher = new Bun.Glob(pattern);
	return [...matcher.scanSync(dir)]
		.filter((path) => shouldIncludeDiscoveredFile(path))
		.map((path) => join(dir, path));
}

export function findSwiftFiles(dir: string): Promise<string[]> {
	return findFiles(dir, "swift");
}
