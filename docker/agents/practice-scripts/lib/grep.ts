import { spawn } from "node:child_process";
import path from "node:path";
import { createInterface } from "node:readline";

import { isRepositoryPath } from "./contract.ts";
import { globFilesSync } from "./files.ts";

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
	// Path and line number are required groups; the trailing content group matches empty for a blank
	// matched line, so it defaults instead of rejecting the whole match.
	const {
		file,
		lineNumber,
		content = "",
	} = /^(?<file>.+?):(?<lineNumber>\d+):(?<content>.*)$/u.exec(line)?.groups ?? {};
	if (file === undefined || lineNumber === undefined) {
		return null;
	}

	return {
		file: path.relative(dir, file),
		line: Number.parseInt(lineNumber, 10),
		content: content.trim(),
	};
}

async function collectGrepMatches(
	args: string[],
	dir: string,
	maxResults: number,
	signal?: AbortSignal,
): Promise<GrepMatch[]> {
	signal?.throwIfAborted();
	const [command, ...commandArgs] = args;
	if (command === undefined || command === "") {
		throw new Error("grep command is empty");
	}
	const child = spawn(command, commandArgs, {
		stdio: ["ignore", "pipe", "ignore"],
		signal,
	});
	let stoppedEarly = false;
	let failure: Error | undefined;
	const completed = Promise.withResolvers<undefined>();
	child.once("error", (error) => {
		failure = error;
	});
	child.once("close", (code) => {
		if (failure !== undefined) {
			completed.reject(failure);
		} else if (stoppedEarly || code === 0 || code === 1) {
			completed.resolve(undefined);
		} else {
			completed.reject(new Error(`grep exited with status ${String(code)}`));
		}
	});
	const matches: GrepMatch[] = [];
	const lines = createInterface({ input: child.stdout, crlfDelay: Infinity });

	const collected = (async () => {
		try {
			for await (const line of lines) {
				const match = parseGrepLine(line, dir);
				if (!match) {
					continue;
				}
				matches.push(match);
				if (matches.length >= maxResults) {
					stoppedEarly = true;
					child.kill();
					break;
				}
			}
		} finally {
			lines.close();
		}
	})();
	await Promise.all([collected, completed.promise]);

	return matches.slice(0, maxResults);
}

async function collectMatchesForGlob(
	pattern: string,
	dir: string,
	grepArgs: string[],
	glob: string,
	maxResults: number,
	signal?: AbortSignal,
): Promise<GrepMatch[]> {
	const matches: GrepMatch[] = [];
	let batch: string[] = [];

	for (const file of globFilesSync(glob, dir)) {
		signal?.throwIfAborted();
		if (!isRepositoryPath(file)) {
			continue;
		}
		batch.push(path.join(dir, file));
		if (batch.length < GLOB_GREP_BATCH_SIZE) {
			continue;
		}

		const remaining = maxResults - matches.length;
		matches.push(
			...(await collectGrepMatches([...grepArgs, "--", pattern, ...batch], dir, remaining, signal)),
		);
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

	matches.push(
		...(await collectGrepMatches([...grepArgs, "--", pattern, ...batch], dir, remaining, signal)),
	);
	return matches.slice(0, maxResults);
}

/**
 * Run grep on a directory. Returns structured matches.
 *
 * @param pattern — regex pattern (or fixed string if fixedString=true)
 * @param dir — directory to search
 * @param opts.glob — file glob filter; basename-only globs (e.g. "*.swift") are auto-expanded to recursive ("**\/*.swift")
 * @param opts.maxResults — cap results (default 500)
 * @param opts.fixedString — use -F instead of -E (default false)
 */
export async function grep(
	pattern: string,
	dir: string,
	opts: GrepOptions = {},
	signal?: AbortSignal,
): Promise<GrepMatch[]> {
	signal?.throwIfAborted();
	const { glob, maxResults = 500, fixedString = false } = opts;
	if (maxResults <= 0) {
		return [];
	}

	const grepArgs = fixedString ? ["grep", "-H", "-n", "-F"] : ["grep", "-H", "-n", "-E"];

	if (glob !== undefined && glob !== "") {
		// A basename-only glob matches only the root dir; prepend "**/" for recursion.
		const recursiveGlob = glob.includes("/") ? glob : `**/${glob}`;
		return collectMatchesForGlob(pattern, dir, grepArgs, recursiveGlob, maxResults, signal);
	}

	return collectGrepMatches(
		["grep", "-r", "--exclude-dir=.git", ...grepArgs.slice(1), "--", pattern, dir],
		dir,
		maxResults,
		signal,
	);
}
