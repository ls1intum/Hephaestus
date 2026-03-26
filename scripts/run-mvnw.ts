#!/usr/bin/env node
/**
 * Cross-platform Maven wrapper runner.
 * Resolves ./mvnw (Unix) or mvnw.cmd (Windows) automatically.
 *
 * Usage: node --import tsx scripts/run-mvnw.ts [maven-args...]
 * Example: node --import tsx scripts/run-mvnw.ts pmd:check -q
 */

import { spawnSync } from "node:child_process";
import path from "node:path";
import process from "node:process";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const mvnwDir = path.resolve(__dirname, "..", "server", "application-server");
const isWindows = process.platform === "win32";

function main(): void {
	// shell: true is required on Windows for .cmd files (CVE-2024-27980).
	// Safe here because args come from hardcoded npm scripts, not user input.
	const result = spawnSync(
		isWindows ? "mvnw.cmd" : "./mvnw",
		process.argv.slice(2),
		{
			stdio: "inherit",
			cwd: mvnwDir,
			shell: isWindows,
		},
	);

	if (result.error) {
		const errCode = (result.error as NodeJS.ErrnoException).code;
		if (errCode === "ENOENT") {
			console.error(
				`Maven wrapper not found in ${mvnwDir}. Is the Maven wrapper installed?`,
			);
		} else {
			console.error(`Failed to run Maven wrapper: ${result.error.message}`);
		}
		process.exitCode = 1;
		return;
	}

	if (result.signal) {
		process.kill(process.pid, result.signal);
		return;
	}

	process.exitCode = result.status ?? 1;
}

main();
