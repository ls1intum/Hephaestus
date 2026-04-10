import type { Hint } from "./types";
import type { DiffFile } from "./types";
import { isInDiff } from "./diff-parser";

interface GrepMatch {
	file: string;
	line: number;
	content: string;
}

/**
 * Run grep on a directory. Returns structured matches.
 *
 * @param pattern — regex pattern (or fixed string if fixedString=true)
 * @param dir — directory to search
 * @param opts.glob — file glob filter (REQUIRED for language-specific searches, defaults to all files)
 * @param opts.maxResults — cap results (default 500)
 * @param opts.fixedString — use -F instead of -E (default false)
 */
export async function grep(
	pattern: string,
	dir: string,
	opts: { glob?: string; maxResults?: number; fixedString?: boolean } = {},
): Promise<GrepMatch[]> {
	const { glob = "*", maxResults = 500, fixedString = false } = opts;

	const fixedFlag = fixedString ? "-F" : "-E";
	// Use single quotes for pattern to avoid shell interpretation; escape any ' in pattern
	const escapedPattern = pattern.replace(/'/g, "'\\''");
	const cmd = `grep -rn ${fixedFlag} --include='${glob}' -m ${maxResults} '${escapedPattern}' '${dir}' 2>/dev/null || true`;

	const result = await Bun.spawn(["bash", "-c", cmd], {
		stdout: "pipe",
		stderr: "pipe",
	});

	const stdout = await new Response(result.stdout).text();
	const matches: GrepMatch[] = [];

	for (const line of stdout.split("\n")) {
		if (!line.trim()) continue;
		// Format: filepath:linenum:content
		const m = line.match(/^(.+?):(\d+):(.*)$/);
		if (m) {
			matches.push({
				file: m[1].replace(dir + "/", ""), // relative path
				line: parseInt(m[2]),
				content: m[3].trim(),
			});
		}
	}

	return matches;
}

/**
 * Convert grep matches to Hints with diff awareness and context flags.
 */
export function matchesToHints(
	matches: GrepMatch[],
	pattern: string,
	diffFiles: Map<string, DiffFile>,
	flagFn?: (match: GrepMatch) => Record<string, boolean>,
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
export async function readFileLines(path: string): Promise<Map<number, string>> {
	try {
		const content = await Bun.file(path).text();
		const lines = new Map<number, string>();
		content.split("\n").forEach((line, i) => {
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
export async function findFiles(dir: string, extension: string): Promise<string[]> {
	const result = await Bun.spawn(
		[
			"bash",
			"-c",
			`find "${dir}" -name "*.${extension}" -not -path "*/.*" -not -path "*/.build/*" -not -path "*/node_modules/*" 2>/dev/null`,
		],
		{
			stdout: "pipe",
		},
	);
	const stdout = await new Response(result.stdout).text();
	return stdout.split("\n").filter(Boolean);
}

export function findSwiftFiles(dir: string): Promise<string[]> {
	return findFiles(dir, "swift");
}
