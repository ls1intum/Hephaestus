import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { isDeepStrictEqual } from "node:util";

import packageArgument from "npm-package-arg";
import { parse } from "yaml";

import { asRecord, asString, isRecord, parseJson } from "./lib/json.ts";
import { CAPTURE_LIMIT_BYTES } from "./lib/process.ts";

const manifest = asRecord(parseJson(readFileSync("package.json", "utf8")), "package.json");
const { devEngines, engines, packageManager } = manifest;
const match =
	typeof packageManager === "string" ? /^pnpm@(\d+\.\d+\.\d+)$/.exec(packageManager) : null;
if (!match) throw new Error("package.json#packageManager must pin an exact pnpm version");
const version = asString(match[1], "package.json#packageManager");
const runtime = isRecord(devEngines) ? devEngines.runtime : undefined;
const manager = isRecord(devEngines) ? devEngines.packageManager : undefined;
if (!isRecord(runtime) || runtime.name !== "node" || runtime.onFail !== "error")
	throw new Error("package.json#devEngines.runtime must pin Node and fail on drift");
const runtimeVersion = asString(runtime.version, "package.json#devEngines.runtime.version");
if (!/^\d+\.\d+\.\d+$/.test(runtimeVersion))
	throw new Error("package.json#devEngines.runtime must pin an exact Node version");
if (!isRecord(engines) || engines.node !== runtimeVersion)
	throw new Error(`package.json#engines.node must pin Node ${runtimeVersion}`);
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

for (const file of [
	"package.json",
	"docs/package.json",
	"webapp/package.json",
	"docker/agents/pi/package.json",
]) {
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
const releaseAge = 4320;
expectConfig("minimumReleaseAge", releaseAge);
// The agent image is its own pnpm project: nothing the root pins reaches it unless it is restated.
const imageWorkspace = asRecord(
	parse(readFileSync("docker/agents/pi/pnpm-workspace.yaml", "utf8")),
	"docker/agents/pi/pnpm-workspace.yaml",
);
if (imageWorkspace.minimumReleaseAge !== releaseAge)
	throw new Error(`docker/agents/pi/pnpm-workspace.yaml must set minimumReleaseAge ${releaseAge}`);
const imageManifest = asRecord(
	parseJson(readFileSync("docker/agents/pi/package.json", "utf8")),
	"docker/agents/pi/package.json",
);
const imageEngines = imageManifest.engines;
if (!isRecord(imageEngines) || imageEngines.pnpm !== version)
	throw new Error(`docker/agents/pi/package.json must pin pnpm ${version} through engines`);
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
const agentDockerfile = readFileSync("docker/agents/pi/Dockerfile", "utf8");
// One tag over two digests is two different pnpm builds wearing a single version number.
const pnpmImage = new RegExp(
	`ghcr\\.io/pnpm/pnpm:${version.replaceAll(".", "\\.")}@(sha256:[a-f0-9]{64})`,
);
const pnpmDigests = new Set(
	(
		[
			["webapp/Dockerfile", dockerfile],
			["docker/agents/pi/Dockerfile", agentDockerfile],
		] as const
	).map(([file, content]) => {
		const digest = pnpmImage.exec(content)?.[1];
		if (!digest) throw new Error(`${file} must use the digest-pinned pnpm ${version} image`);
		return digest;
	}),
);
if (pnpmDigests.size !== 1) throw new Error("both Dockerfiles must pin one pnpm base digest");
// Those settings only bind if the image copies them and lets pnpm read them.
if (
	!/^COPY pi\/package\.json pi\/pnpm-lock\.yaml pi\/pnpm-workspace\.yaml \.\/$/m.test(
		agentDockerfile,
	) ||
	!/^RUN pnpm install --prod --frozen-lockfile --ignore-scripts$/m.test(agentDockerfile)
) {
	throw new Error(
		"docker/agents/pi/Dockerfile must install from its own workspace settings, without lifecycle scripts",
	);
}
if (
	!new RegExp(`^ARG NODE_VERSION=${runtimeVersion.replaceAll(".", "\\.")}$`, "m").test(
		dockerfile,
	) ||
	!new RegExp(["^RUN pnpm runtime set node \\$", "\\{NODE_VERSION\\} -g$"].join(""), "m").test(
		dockerfile,
	)
) {
	throw new Error(`webapp/Dockerfile must install Node ${runtimeVersion} through pnpm`);
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
	`pnpm ${version}, Node ${runtimeVersion}, and the no-legacy-runtime policy are consistent`,
);
