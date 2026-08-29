import { execFileSync } from "node:child_process";
import { existsSync, globSync, readFileSync } from "node:fs";

import { parse } from "jsonc-parser";
import packageArgument from "npm-package-arg";

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null;
}

const manifest: unknown = JSON.parse(readFileSync("package.json", "utf8"));
if (!isRecord(manifest)) throw new Error("package.json must be an object");
const { devEngines, engines, packageManager, overrides, workspaces } = manifest;
if (typeof packageManager !== "string" || !isRecord(overrides)) {
	throw new Error("package.json must declare packageManager and overrides");
}
if (!Array.isArray(workspaces) || !workspaces.includes(".")) {
	throw new Error("package.json#workspaces must include the release-owning root package");
}
if (!isRecord(engines) || engines.node !== ">=24.19.0 <25") {
	throw new Error("package.json#engines.node must require the supported Node 24 line");
}
const runtime = isRecord(devEngines) ? devEngines.runtime : undefined;
if (
	!isRecord(runtime) ||
	runtime.name !== "node" ||
	runtime.version !== "24.19.0" ||
	runtime.onFail !== "error"
) {
	throw new Error("package.json#devEngines.runtime must pin Node 24.19.0");
}
const overrideEntries = Object.entries(overrides).filter(
	(entry): entry is [string, string] => typeof entry[1] === "string",
);
if (overrideEntries.length !== Object.keys(overrides).length)
	throw new Error("Every override must be a string");
const match = /^bun@(\d+\.\d+\.\d+)$/.exec(packageManager);
if (!match) throw new Error("package.json#packageManager must pin an exact Bun version");
const version = match[1];
const runningVersion = execFileSync("bun", ["--version"], { encoding: "utf8" }).trim();
if (runningVersion !== version) throw new Error(`Expected Bun ${version}, found ${runningVersion}`);

for (const file of ["package.json", "docs/package.json", "webapp/package.json"]) {
	const workspaceManifest: unknown = JSON.parse(readFileSync(file, "utf8"));
	if (!isRecord(workspaceManifest)) throw new Error(`${file} must be an object`);
	const scripts = workspaceManifest.scripts;
	if (isRecord(scripts)) {
		for (const [name, command] of Object.entries(scripts)) {
			if (typeof command === "string" && /(?:^|[;&|]\s*)\s*tsx(?:\s|$)/.test(command)) {
				throw new Error(`${file}#scripts.${name} must use Node directly`);
			}
		}
	}
	for (const section of [
		"dependencies",
		"devDependencies",
		"optionalDependencies",
		"peerDependencies",
	]) {
		const dependencies = workspaceManifest[section];
		if (!isRecord(dependencies)) continue;
		for (const [name, specifier] of Object.entries(dependencies)) {
			if (typeof specifier !== "string")
				throw new Error(`${file} has an invalid ${name} specifier`);
			if (!packageArgument.resolve(name, specifier).registry) {
				throw new Error(`${file} uses unsupported exotic dependency ${name}@${specifier}`);
			}
		}
	}
}
for (const file of [".node-version", "pnpm-lock.yaml", "pnpm-workspace.yaml"]) {
	if (existsSync(file)) throw new Error(`${file} must not exist in the Bun-only repository`);
}
const setupBunAction = readFileSync(".github/actions/setup-bun/action.yml", "utf8");
if (!/^\s*bun-version-file:\s*package\.json\s*$/m.test(setupBunAction)) {
	throw new Error("setup-bun must read the exact Bun version from package.json");
}
if (!/^\s*node-version-file:\s*package\.json\s*$/m.test(setupBunAction)) {
	throw new Error("setup-bun must read the exact Node version from package.json");
}
if (/^\[run]$/m.test(readFileSync("bunfig.toml", "utf8"))) {
	throw new Error("bunfig.toml must not override Node script execution");
}
for (const file of globSync("scripts/**/*.ts")) {
	if (/\bBun\.|from\s+["']bun:test["']/.test(readFileSync(file, "utf8"))) {
		throw new Error(`${file} must run on Node.js without Bun APIs`);
	}
}

for (const file of ["docker/agents/pi/Dockerfile", "webapp/Dockerfile"]) {
	const dockerfile = readFileSync(file, "utf8");
	const pins = dockerfile.match(/^ARG BUN_VERSION=(\S+)$/gm) ?? [];
	if (pins.length !== 1 || pins[0] !== `ARG BUN_VERSION=${version}`)
		throw new Error(`${file} must pin Bun ${version}`);
}
const lock: unknown = parse(readFileSync("bun.lock", "utf8"));
if (!isRecord(lock)) throw new Error("bun.lock must be an object");
const { packages } = lock;
if (!isRecord(packages)) throw new Error("bun.lock must contain packages");
const packageValues = Object.values(packages).filter((value): value is unknown[] =>
	Array.isArray(value),
);
// Multiple publicly hoisted React or type resolutions make workspace JSX types incompatible.
const bunfig = readFileSync("bunfig.toml", "utf8");
const publicHoistPattern = /^publicHoistPattern\s*=\s*\[([^\]]+)]$/m
	.exec(bunfig)?.[1]
	?.split(",")
	.map((value) => value.trim().replace(/^"|"$/g, ""));
if (!publicHoistPattern || publicHoistPattern.length === 0) {
	throw new Error("bunfig.toml must declare install.publicHoistPattern");
}
for (const pattern of publicHoistPattern) {
	// The package manager owns glob expansion.
	if (typeof pattern !== "string" || /[*?[\]]/.test(pattern)) continue;
	const resolutions = new Set(
		packageValues
			.map(([resolution]) => resolution)
			.filter((resolution): resolution is string => typeof resolution === "string")
			.filter((resolution) => resolution.startsWith(`${pattern}@`)),
	);
	if (resolutions.size === 0)
		throw new Error(`Publicly hoisted ${pattern} matches no lockfile package`);
	if (resolutions.size > 1) {
		throw new Error(
			`Publicly hoisted ${pattern} resolves to ${resolutions.size} versions, so the workspaces cannot share one copy: ${[...resolutions].join(", ")}`,
		);
	}
}

for (const [dependency, expected] of overrideEntries) {
	const resolutions = packageValues
		.map(([resolution]) => resolution)
		.filter((resolution): resolution is string => typeof resolution === "string")
		.filter((resolution) => resolution.startsWith(`${dependency}@`));
	if (resolutions.length === 0)
		throw new Error(`Override ${dependency}@${expected} matches no lockfile package`);
	const conflicts = resolutions.filter((resolution) => resolution !== `${dependency}@${expected}`);
	if (conflicts.length > 0) {
		throw new Error(
			`Override ${dependency}@${expected} has conflicting nested resolutions: ${conflicts.join(", ")}`,
		);
	}
	const installedPaths = globSync(`node_modules/.bun/**/node_modules/${dependency}/package.json`);
	if (installedPaths.length === 0)
		throw new Error(`Override ${dependency}@${expected} is not installed`);
	for (const path of installedPaths) {
		const installedManifest: unknown = parse(readFileSync(path, "utf8"));
		if (
			!isRecord(installedManifest) ||
			installedManifest.name !== dependency ||
			installedManifest.version !== expected
		) {
			throw new Error(`Installed override ${path} is not ${dependency}@${expected}`);
		}
	}
}

console.log(`Bun ${version} and ${overrideEntries.length} overrides are consistent`);
