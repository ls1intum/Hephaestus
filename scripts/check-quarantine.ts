import { existsSync } from "node:fs";
import { readFile } from "node:fs/promises";
import path from "node:path";

import { output } from "./lib/process.ts";

const REPO_ROOT = path.resolve(import.meta.dirname, "..");
const MAX_DAYS = 14;
const DAY_MS = 24 * 60 * 60 * 1000;

const JAVA_TEST = /^server\/application\/src\/test\/java\/.*\.java$/u;
const TS_TEST = /\.(?:test|spec)\.tsx?$/u;
// A skip whose first argument is a name skips unconditionally. `test.skip(condition, reason)`
// and `it.skipIf(condition)` gate on the environment, such as missing live credentials.
const TS_SKIP =
	/\b(?:(?:describe|it|test)\.(?:skip|fixme|todo)\(\s*["'`]|x(?:describe|it|test)\()/u;
const JAVA_SKIP = /@(?:org\.junit\.jupiter\.api\.)?Disabled\b/u;
const MARKER = /quarantine #(?<issue>\d+) until (?<until>\d{4}-\d{2}-\d{2})/u;

export interface Finding {
	readonly file: string;
	readonly line: number;
	readonly problem: string;
}

/** Unconditional skips in one test file that name no issue or carry an expired or distant date. */
export function quarantineFindings(file: string, content: string, today: Date): Finding[] {
	const skip = file.endsWith(".java") ? JAVA_SKIP : TS_SKIP;
	const lines = content.split("\n");
	const findings: Finding[] = [];
	for (const [index, text] of lines.entries()) {
		if (!skip.test(text)) {
			continue;
		}
		const line = index + 1;
		const marker = MARKER.exec(text) ?? MARKER.exec(lines[index - 1] ?? "");
		const issue = marker?.groups?.issue;
		const end = marker?.groups?.until;
		if (issue === undefined || end === undefined) {
			findings.push({
				file,
				line,
				problem: "skips a test without `quarantine #<issue> until <YYYY-MM-DD>`",
			});
			continue;
		}
		const until = Date.parse(`${end}T00:00:00Z`);
		const start = Date.UTC(today.getUTCFullYear(), today.getUTCMonth(), today.getUTCDate());
		if (!Number.isFinite(until) || until < start) {
			findings.push({
				file,
				line,
				problem: `quarantine of #${issue} expired on ${end}`,
			});
		} else if (until - start > MAX_DAYS * DAY_MS) {
			findings.push({
				file,
				line,
				problem: `quarantine of #${issue} runs past ${MAX_DAYS} days (${end})`,
			});
		}
	}
	return findings;
}

// This gate's own fixtures spell out the skips that it rejects.
const OWN_FIXTURES = "scripts/check-quarantine.test.ts";

export function isTestSource(file: string): boolean {
	return (
		file !== OWN_FIXTURES &&
		(JAVA_TEST.test(file) || (TS_TEST.test(file) && !file.includes("node_modules/")))
	);
}

if (import.meta.main) {
	const stdout = await output(
		"git",
		["ls-files", "-z", "--cached", "--others", "--exclude-standard"],
		{
			cwd: REPO_ROOT,
			env: { GIT_DIR: undefined, GIT_INDEX_FILE: undefined, GIT_WORK_TREE: undefined },
		},
	);
	// A file deleted in the work tree is still listed from the index until it is staged.
	const files = stdout
		.split("\0")
		.filter((file) => isTestSource(file) && existsSync(path.join(REPO_ROOT, file)));
	if (files.length === 0) {
		throw new Error("No test sources found; refusing to pass without checking anything.");
	}
	const today = new Date();
	const perFile = await Promise.all(
		files.map(async (file) =>
			quarantineFindings(file, await readFile(path.join(REPO_ROOT, file), "utf8"), today),
		),
	);
	const findings = perFile.flat();
	for (const finding of findings) {
		process.stderr.write(`${finding.file}:${finding.line} ${finding.problem}\n`);
	}
	if (findings.length > 0) {
		process.stderr.write("See docs/contributor/testing.mdx § Flaky tests.\n");
		process.exitCode = 1;
	} else {
		process.stdout.write(
			`check-quarantine: ${files.length} test files, no unowned or expired skip.\n`,
		);
	}
}
