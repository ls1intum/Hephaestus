#!/usr/bin/env node
/**
 * Runs a tool and drops the lines nobody reads, so a failure is not buried under a page of routine
 * output. Prettier names every file it looked at; the OpenAPI generator logs one INFO line per
 * artefact plus a donation banner. Both are the only thing on screen when a run goes wrong.
 *
 * Every line the child writes is kept for the summary, whether or not it was printed, and the exit
 * code is the child's own — a filtered line never changes the verdict.
 */

import { spawn } from "node:child_process";
import process from "node:process";

type FilterConfig = {
	command: string;
	args: string[];
	filter: (line: string) => boolean;
	transform?: (line: string) => string;
	summary?: (lines: string[], exitCode: number) => string;
};

/** Prettier prints one line per file it looked at; only the ones it changed or refused are news. */
function prettierFilter(line: string): boolean {
	if (line.includes("[error]") || line.includes("[warn]")) {
		return true;
	}

	if (line.includes("(unchanged)")) {
		return false;
	}

	if (line.trim() === "Checking formatting...") {
		return false;
	}

	return true;
}

function prettierSummary(lines: string[], exitCode: number): string {
	const changedFiles = lines.filter((l) => {
		// `[warn] src/A.java` ends in a source extension too, so the diagnostics go first.
		if (l.includes("[warn]") || l.includes("[error]")) {
			return false;
		}
		return l.match(/\.(java|ts|tsx|js|jsx|json|css|scss|md)$/) && !l.includes("(unchanged)");
	});

	const warnings = lines.filter((l) => l.includes("[warn]"));
	const errors = lines.filter((l) => l.includes("[error]"));

	if (exitCode === 0) {
		if (changedFiles.length === 0) {
			return "All files formatted correctly.";
		}
		return `Formatted ${changedFiles.length} file(s).`;
	}

	if (warnings.length > 0) {
		return `${warnings.length} file(s) need formatting.`;
	}

	if (errors.length > 0) {
		return `${errors.length} error(s) found.`;
	}

	return "";
}

/**
 * Order matters: the three suppressed groups are all spelled as warnings the generator emits on every
 * run whatever the spec says, so each has to be matched before the generic WARN clause keeps it.
 */
function openApiGenFilter(line: string): boolean {
	// The JVM's own `sun.misc.Unsafe` deprecation notice, from a dependency this repo does not own.
	if (
		line.includes("sun.misc.Unsafe") ||
		line.includes("terminally deprecated") ||
		line.includes("Please consider reporting this to the maintainers")
	) {
		return false;
	}

	if (
		line.includes("###") ||
		line.includes("opencollective") ||
		line.includes("Thanks for using OpenAPI Generator") ||
		line.includes("Please consider donation")
	) {
		return false;
	}

	// A paragraph-long caveat about the generator's OpenAPI 3.1 support, repeated per operation.
	if (line.includes("3.1.0 specs is in development")) {
		return false;
	}

	if (line.includes("[main] INFO")) {
		// The generator's own name and version, which is worth one line of the run.
		if (line.includes("OpenAPI Generator:")) return true;
		return false;
	}

	if (line.includes("[main] WARN") || line.includes("[main] ERROR")) {
		return true;
	}

	if (line.trim() === "" || line.trim().match(/^#+$/)) {
		return false;
	}

	return true;
}

/** The generator logs absolute paths; the caller is standing in the directory they start from. */
function openApiGenTransform(line: string): string {
	const cwdPrefix = `${process.cwd()}/`;
	return line.replaceAll(cwdPrefix, "");
}

function openApiGenSummary(lines: string[], exitCode: number): string {
	const generatedFilesCount = lines.filter((l) => l.includes("written file")).length;
	if (generatedFilesCount > 0) {
		return `Generated ${generatedFilesCount} files.`;
	}

	if (exitCode === 0) {
		return "Generation completed.";
	}

	return "Generation failed.";
}

const TOOL_CONFIGS: Record<string, Omit<FilterConfig, "command" | "args">> = {
	prettier: {
		filter: prettierFilter,
		summary: prettierSummary,
	},
	"openapi-gen": {
		filter: openApiGenFilter,
		transform: openApiGenTransform,
		summary: openApiGenSummary,
	},
};

async function run(tool: string, args: string[]): Promise<number> {
	const config = TOOL_CONFIGS[tool];
	if (!config) {
		console.error(`Unknown tool: ${tool}`);
		console.error(`Supported tools: ${Object.keys(TOOL_CONFIGS).join(", ")}`);
		return 1;
	}

	const command = "npx";
	const commandArgs =
		tool === "prettier" ? ["prettier", ...args] : ["openapi-generator-cli", ...args];

	return new Promise((resolve) => {
		const proc = spawn(command, commandArgs, {
			stdio: ["inherit", "pipe", "pipe"],
			shell: process.platform === "win32",
		});

		const allLines: string[] = [];

		const processOutput = (data: Buffer, isStderr: boolean) => {
			const lines = data.toString().split("\n");
			for (const rawLine of lines) {
				if (!rawLine) continue;

				// Recorded before filtering: the summary counts what the tool did, not what got printed.
				allLines.push(rawLine);

				if (!config.filter(rawLine)) {
					continue;
				}

				const line = config.transform ? config.transform(rawLine) : rawLine;

				if (isStderr) {
					console.error(line);
				} else {
					console.log(line);
				}
			}
		};

		proc.stdout.on("data", (data: Buffer) => processOutput(data, false));
		proc.stderr.on("data", (data: Buffer) => processOutput(data, true));

		proc.on("close", (code) => {
			const exitCode = code ?? 0;

			if (config.summary) {
				const summary = config.summary(allLines, exitCode);
				if (summary) {
					console.log(summary);
				}
			}

			resolve(exitCode);
		});

		proc.on("error", (err) => {
			console.error(`Failed to start ${command}:`, err.message);
			resolve(1);
		});
	});
}

async function main(): Promise<void> {
	const [tool, ...toolArgs] = process.argv.slice(2);

	if (tool === undefined) {
		console.error("Usage: run-quiet.ts <tool> [args...]");
		console.error("Supported tools: prettier, openapi-gen");
		process.exitCode = 1;
		return;
	}

	process.exitCode = await run(tool, toolArgs);
}

main().catch((error: unknown) => {
	console.error(error);
	process.exitCode = 1;
});
