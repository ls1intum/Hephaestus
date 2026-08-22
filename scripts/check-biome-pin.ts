#!/usr/bin/env node
/**
 * Biome's version is stated in four places, and nothing else compares them:
 *
 *   1. the exact pin in the root package.json      — what a fresh install resolves
 *   2. the binary pnpm actually installed          — what CI and the pre-push hook run
 *   3. the $schema URL in biome.jsonc              — what an editor validates against
 *   4. the $schema URL in webapp/biome.jsonc       —  "
 *
 * A stale $schema is the dangerous one: `pnpm install --frozen-lockfile` already refuses a
 * package.json/lockfile disagreement, but an editor pointed at another release's schema
 * happily writes rules the pinned binary formats differently, so the next `format --write`
 * rewrites files nobody touched. Comparing all four keeps a version bump honest — bumping
 * the dependency without the schema URLs now fails here instead of in someone's diff.
 */
import { readFileSync } from "node:fs";
import { asRecord, asString, isRecord, parseJson } from "./lib/json.ts";

const PIN_PATH = "package.json";
const INSTALLED_PATH = "node_modules/@biomejs/biome/package.json";
const CONFIGS = ["biome.jsonc", "webapp/biome.jsonc"];
const SCHEMA_RE = /"\$schema"\s*:\s*"https:\/\/biomejs\.dev\/schemas\/([^/]+)\/schema\.json"/;

const devDependencies = asRecord(
	parseJson(readFileSync(PIN_PATH, "utf8")),
	PIN_PATH,
).devDependencies;
const declared = isRecord(devDependencies) ? devDependencies["@biomejs/biome"] : undefined;
const pinned = typeof declared === "string" ? declared : "";
const problems: string[] = [];

if (pinned === "") {
	problems.push(`${PIN_PATH}: @biomejs/biome is not a devDependency.`);
} else if (!/^\d+\.\d+\.\d+$/.test(pinned)) {
	problems.push(
		`${PIN_PATH}: @biomejs/biome is "${pinned}"; pin an exact version so every machine formats identically.`,
	);
}

for (const config of CONFIGS) {
	const match = SCHEMA_RE.exec(readFileSync(config, "utf8"));
	if (!match) {
		problems.push(`${config}: no biomejs.dev $schema URL found.`);
	} else if (match[1] !== pinned) {
		problems.push(`${config}: $schema is ${match[1]} but package.json pins ${pinned}.`);
	}
}

// Read what pnpm actually put on disk rather than shelling out, so the check does not depend on
// node_modules/.bin being on PATH. An install that predates a pin bump formats to the old
// release's rules — drift the $schema comparison alone cannot see.
let installedManifest: string | undefined;
try {
	installedManifest = readFileSync(INSTALLED_PATH, "utf8");
} catch {
	problems.push("@biomejs/biome is not installed — run `pnpm install` before this check.");
}
const installed =
	installedManifest === undefined
		? undefined
		: asString(
				asRecord(parseJson(installedManifest), INSTALLED_PATH).version,
				`${INSTALLED_PATH} version`,
			);
if (installed !== undefined && installed !== pinned) {
	problems.push(
		`installed biome is ${installed} but package.json pins ${pinned}; run \`pnpm install\`.`,
	);
}

if (problems.length > 0) {
	for (const problem of problems) console.error(`error: ${problem}`);
	console.error("\nKeep the pin, the installed binary and both $schema URLs on one version.");
	process.exit(1);
}

console.log(`Biome ${pinned} — pin, installed binary and both $schema URLs agree.`);
