import { createHash } from "node:crypto";
import { readFile, rm } from "node:fs/promises";
import { join } from "node:path";

import { run } from "./lib/process.ts";

export function parseIterations(value: string | undefined): number {
	const text = value ?? "25";
	if (!/^[1-9]\d*$/.test(text)) throw new Error("iterations must be a positive integer");
	const iterations = Number(text);
	if (!Number.isSafeInteger(iterations)) throw new Error("iterations must be a safe integer");
	return iterations;
}

async function main(): Promise<void> {
	let iterations: number;
	try {
		iterations = parseIterations(process.argv[2]);
	} catch (error) {
		console.error(error instanceof Error ? error.message : String(error));
		process.exitCode = 2;
		return;
	}
	const root = join(import.meta.dirname, "..");
	const lockfile = join(root, "bun.lock");
	const digest = async (): Promise<string> =>
		createHash("sha256")
			.update(await readFile(lockfile))
			.digest("hex");
	const expected = await digest();
	for (let iteration = 1; iteration <= iterations; iteration++) {
		await Promise.all(
			["node_modules", "webapp/node_modules", "docs/node_modules"].map((path) =>
				rm(join(root, path), { recursive: true, force: true }),
			),
		);
		await run("bun", ["install", "--frozen-lockfile", "--no-progress"], { cwd: root });
		if ((await digest()) !== expected)
			throw new Error(`bun.lock changed on iteration ${iteration}`);
		await run("bun", ["run", "check:package-manager"], { cwd: root });
	}
}

if (import.meta.main) await main();
