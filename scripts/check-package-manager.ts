import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { isDeepStrictEqual } from "node:util";

import packageArgument from "npm-package-arg";

import { CAPTURE_LIMIT_BYTES } from "./lib/process.ts";

function isRecord(value: unknown): value is Record<string, unknown> {
	return typeof value === "object" && value !== null;
}

const manifest: unknown = JSON.parse(readFileSync("package.json", "utf8"));
if (!isRecord(manifest)) throw new Error("package.json must be an object");
const { devEngines, engines, packageManager } = manifest;
const match =
	typeof packageManager === "string" ? /^pnpm@(\d+\.\d+\.\d+)$/.exec(packageManager) : null;
if (!match) throw new Error("package.json#packageManager must pin an exact pnpm version");
const version = match[1];
if (!isRecord(engines) || engines.node !== ">=24.19.0 <25")
	throw new Error("package.json#engines.node must require the supported Node 24 line");
const runtime = isRecord(devEngines) ? devEngines.runtime : undefined;
const manager = isRecord(devEngines) ? devEngines.packageManager : undefined;
if (
	!isRecord(runtime) ||
	runtime.name !== "node" ||
	runtime.version !== "24.19.0" ||
	runtime.onFail !== "error"
)
	throw new Error("package.json#devEngines.runtime must pin Node 24.19.0");
if (
	!isRecord(manager) ||
	manager.name !== "pnpm" ||
	manager.version !== version ||
	manager.onFail !== "error"
)
	throw new Error(`package.json#devEngines.packageManager must pin pnpm ${version}`);
const runningVersion = execFileSync("pnpm", ["--version"], { encoding: "utf8" }).trim();
if (runningVersion !== version)
	throw new Error(`Expected pnpm ${version}, found ${runningVersion}`);

for (const file of ["package.json", "docs/package.json", "webapp/package.json"]) {
	const workspaceManifest: unknown = JSON.parse(readFileSync(file, "utf8"));
	if (!isRecord(workspaceManifest)) throw new Error(`${file} must be an object`);
	const scripts = workspaceManifest.scripts;
	if (isRecord(scripts))
		for (const [name, command] of Object.entries(scripts)) {
			if (typeof command === "string" && /(?:^|[;&|]\s*)\s*tsx(?:\s|$)/.test(command))
				throw new Error(`${file}#scripts.${name} must use Node directly`);
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
			if (typeof specifier !== "string" || !packageArgument.resolve(name, specifier).registry)
				throw new Error(`${file} uses unsupported dependency ${name}@${String(specifier)}`);
		}
	}
}
for (const required of [
	"pnpm-lock.yaml",
	"pnpm-workspace.yaml",
	".github/actions/setup-node-pnpm/action.yml",
]) {
	if (!existsSync(required)) throw new Error(`${required} is required`);
}

function pnpmConfig(name: string): unknown {
	return JSON.parse(execFileSync("pnpm", ["config", "get", name, "--json"], { encoding: "utf8" }));
}
function expectConfig(name: string, expected: unknown): void {
	const actual = pnpmConfig(name);
	if (!isDeepStrictEqual(actual, expected)) {
		throw new Error(
			`pnpm ${name} must be ${JSON.stringify(expected)}, found ${JSON.stringify(actual)}`,
		);
	}
}
expectConfig("packages", [".", "webapp", "docs"]);
expectConfig("nodeLinker", "isolated");
expectConfig("hoist", false);
expectConfig("publicHoistPattern", [
	"react",
	"react-dom",
	"@types/react",
	"@types/react-dom",
	"@types/hast",
]);
expectConfig("minimumReleaseAge", 4320);
expectConfig("allowBuilds", {
	"@nestjs/core": false,
	protobufjs: false,
	"core-js": false,
	"@openapitools/openapi-generator-cli": false,
	msw: false,
	esbuild: true,
	"@google/genai": false,
	"@swc/core": true,
	"@sentry/cli": true,
});

const setup = readFileSync(".github/actions/setup-node-pnpm/action.yml", "utf8");
if (!/^\s*uses: actions\/setup-node@[a-f0-9]{40} # v7\.0\.0$/m.test(setup)) {
	throw new Error("setup-node-pnpm must pin actions/setup-node to a full commit SHA");
}
if (!/^\s*node-version-file: package\.json$/m.test(setup)) {
	throw new Error("CI must provision package.json#devEngines.runtime before setup-vp");
}
if (!/^\s*uses: pnpm\/setup@[a-f0-9]{40} # v\d+\.\d+\.\d+$/m.test(setup)) {
	throw new Error("setup-node-pnpm must pin pnpm/setup to a full commit SHA");
}
if (
	!/^\s*cache: true$/m.test(setup) ||
	!/^\s*install: \${{ inputs\.install == 'frozen' }}$/m.test(setup) ||
	!/^\s*require-lockfile: \${{ inputs\.install == 'frozen' }}$/m.test(setup) ||
	!setup.includes("pnpm install --frozen-lockfile --ignore-scripts")
) {
	throw new Error("setup-node-pnpm must own frozen and hardened dependency installation");
}
if (!/^\s*uses: voidzero-dev\/setup-vp@[a-f0-9]{40} # v1\.18\.0$/m.test(setup)) {
	throw new Error("setup-node-pnpm must pin setup-vp to a full commit SHA");
}
if (!/^\s*node-manager: false$/m.test(setup)) {
	throw new Error("setup-vp must leave Node management to the repository-pinned runtime");
}
if (!/^\s*run-install: false$/m.test(setup)) {
	throw new Error("setup-vp must leave dependency installation to the install-mode input");
}
if (/^\s*(?:version|runtime):/m.test(setup)) {
	throw new Error("setup-node-pnpm must read tool versions from package.json");
}

const dockerfile = readFileSync("webapp/Dockerfile", "utf8");
if (!dockerfile.includes(`ghcr.io/pnpm/pnpm:${version}@sha256:`)) {
	throw new Error(`webapp/Dockerfile must use the digest-pinned pnpm ${version} image`);
}
if (
	!new RegExp(`^ARG NODE_VERSION=${runtime.version.replaceAll(".", "\\.")}$`, "m").test(
		dockerfile,
	) ||
	!new RegExp(["^RUN pnpm runtime set node \\$", "\\{NODE_VERSION\\} -g$"].join(""), "m").test(
		dockerfile,
	)
) {
	throw new Error(`webapp/Dockerfile must install Node ${runtime.version} through pnpm`);
}

// Construct the token so this source file also satisfies the repository-wide ban.
const retired = ["b", "un"].join("");
const isHistorical = (file: string): boolean =>
	file === "CHANGELOG.md" || file === "MIGRATION.md" || file.startsWith("docs/decisions/");
// The tracked-file list grows with the repository, so it needs the shared ceiling rather than
// Node's 1 MiB default — this runs in `pnpm run check` on every machine.
const tracked = execFileSync("git", ["ls-files", "-z"], {
	encoding: "utf8",
	maxBuffer: CAPTURE_LIMIT_BYTES,
})
	.split("\0")
	.filter(Boolean);
const forbiddenWord = new RegExp(`\\b${retired}\\b`, "i");
const forbiddenPath = new RegExp(
	`(?:^|/)(?:${retired}\\.lockb?|${retired}fig\\.toml|setup-${retired})(?:$|/)`,
	"i",
);
for (const file of tracked) {
	if (isHistorical(file) || !existsSync(file)) continue;
	if (forbiddenPath.test(file)) throw new Error(`${file} is a retired tool artifact`);
	if (
		["package-lock.json", "npm-shrinkwrap.json", "yarn.lock"].includes(file.split("/").at(-1) ?? "")
	) {
		throw new Error(`${file} is forbidden; pnpm-lock.yaml is the only lockfile`);
	}
	const content = readFileSync(file);
	if (!content.includes(0) && forbiddenWord.test(content.toString("utf8"))) {
		throw new Error(`${file} still references the retired package manager or runtime`);
	}
}
console.log(
	`pnpm ${version}, Node ${runtime.version}, and the no-legacy-runtime policy are consistent`,
);
