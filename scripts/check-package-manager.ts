import { execFileSync } from "node:child_process";
import { existsSync, globSync, readFileSync } from "node:fs";

import { parse } from "jsonc-parser";
import packageArgument from "npm-package-arg";

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null;
}

const manifest: unknown = JSON.parse(readFileSync("package.json", "utf8"));
if (!isRecord(manifest)) throw new Error("package.json must be an object");
const { packageManager, overrides, workspaces } = manifest;
if (typeof packageManager !== "string" || !isRecord(overrides)) {
	throw new Error("package.json must declare packageManager and overrides");
}
if (!Array.isArray(workspaces) || !workspaces.includes(".")) {
	throw new Error("package.json#workspaces must include the release-owning root package");
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
	const engines = workspaceManifest.engines;
	if (isRecord(engines) && "node" in engines) {
		throw new Error(`${file} must not declare a Node.js engine`);
	}
	const scripts = workspaceManifest.scripts;
	if (isRecord(scripts)) {
		for (const [name, command] of Object.entries(scripts)) {
			if (typeof command === "string" && /(?:^|[;&|]\s*)\s*(?:node|tsx)(?:\s|$)/.test(command)) {
				throw new Error(`${file}#scripts.${name} must use Bun`);
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
